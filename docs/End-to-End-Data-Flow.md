# End-to-End Data Flow

This document explains the complete runtime flow in MiniDatabase from client connection to data save and result return.

It is intentionally narrative and code-aligned.

---

## 1. High-level lifecycle

A typical request goes through these stages:

1. Client application opens a connection
2. Server authenticates the client
3. Client sends SQL
4. SQL is parsed into AST
5. Engine dispatches execution
6. Planner is used for SELECT queries
7. Storage layer reads or mutates pages
8. Dirty pages are flushed to disk on write operations
9. Results are converted to text protocol lines
10. JDBC/raw client converts response back into application-facing objects

---

## 2. Phase A: application connects to server

## 2.1 JDBC path

When a Java application calls `DriverManager.getConnection(...)` using:
- driver: `com.minidb.jdbc.MiniDbDriver`
- URL: `jdbc:minidb://host:port/`

The following occurs:

1. `MiniDbDriver.connect(...)` parses host/port from JDBC URL.
2. `MiniDbConnection` opens a `Socket`.
3. `MiniDbConnection` reads protocol greeting and auth prompt.
4. `MiniDbConnection` sends username/password.
5. If auth succeeds, the connection object is ready.

Relevant files:
- `minidb-jdbc/src/main/java/com/minidb/jdbc/MiniDbDriver.java`
- `minidb-jdbc/src/main/java/com/minidb/jdbc/MiniDbConnection.java`

## 2.2 Server accept path

At the same time on the server side:

1. `DatabaseServer` starts `TcpTransportServer`.
2. `TcpTransportServer` listens on configured port.
3. Each accepted socket is handed to a worker thread.
4. Worker calls `TextProtocolHandler.handle(socket)`.

Relevant files:
- `minidb-server/src/main/java/com/minidb/server/DatabaseServer.java`
- `minidb-transport/src/main/java/com/minidb/transport/tcp/TcpTransportServer.java`
- `minidb-transport/src/main/java/com/minidb/transport/protocol/TextProtocolHandler.java`

---

## 3. Phase B: authentication flow

Authentication happens before query processing begins.

Current behavior:
1. Server sends `MINIDB 1.0`
2. Server sends `AUTH`
3. Client sends username line
4. Client sends password line
5. `AuthService.authenticate(...)` is called
6. Server returns `OK` on success, error otherwise

Current auth implementation:
- `InMemoryAuthService`
- default account `admin` / `minidb`
- optional env-driven admin credentials supported at startup

Security note:
- This is authentication only.
- Authorization and TLS are not yet implemented.

---

## 4. Phase C: SQL submission from client

Once authenticated:

### JDBC
- `MiniDbStatement.executeQuery(...)`, `executeUpdate(...)`, or `execute(...)`
- `MiniDbConnection.execute(sql)` sends one SQL line over the socket
- response is read until `END`

### CLI/raw TCP
- client writes SQL line directly
- server responds with one or more lines followed by `END`

Protocol framing is line-based, so current best practice is to send one logical SQL statement per line.

---

## 5. Phase D: parsing

On the server:

1. `TextProtocolHandler` receives SQL line
2. It passes SQL into the parser function created in `DatabaseServer`
3. `SQLParserService.parse(sql)` builds AST using ANTLR-generated parser
4. Resulting AST is a `Statement` subtype such as:
   - `CreateTableStatement`
   - `InsertStatement`
   - `SelectStatement`
   - `UpdateStatement`
   - `DeleteStatement`
   - transaction control statements

This stage converts text into structured semantic objects.

---

## 6. Phase E: engine dispatch

`Engine.execute(stmt)` is the main command dispatcher.

It routes by statement category:

### DDL
Handled by `DDLExecutor`
- create/drop database
- create/drop schema
- create/drop table

### Transactions
Handled in `Engine`
- `BEGIN`
- `COMMIT`
- `ROLLBACK`

### DML
Handled in `Engine`
- `INSERT`
- `UPDATE`
- `DELETE`

### SELECT
Handled by planner + physical execution plan

Relevant file:
- `minidb-executor/src/main/java/com/minidb/executor/Engine.java`

---

## 7. Phase F: planning (SELECT path)

SELECT execution is different from direct DML path.

Flow:
1. `Engine.select(...)` calls `QueryPlanner.plan(stmt)`
2. Planner resolves target table from catalog
3. Planner builds plan nodes depending on SQL clauses

Current supported physical plan nodes include:
- table scan
- filter
- project
- aggregate
- sort
- limit/offset

This means SELECT goes through a planned execution tree rather than direct table mutation logic.

---

## 8. Phase G: catalog resolution

Before storage can be used, table metadata must be resolved.

The catalog provides:
- database
- schema
- table
- columns
- root page ID
- row layout metadata

This is how the engine knows:
- which data file structure to access
- how to serialize/deserialize rows
- what columns exist and in what order

Catalog state is persisted in:
- `data/catalog.meta`

---

## 9. Phase H: write path (INSERT/UPDATE/DELETE)

## 9.1 INSERT

Flow:
1. `Engine.insert(...)` resolves target table
2. Values are evaluated from expressions
3. Values are reordered if column list is provided
4. `TableStorage.insert(new Row(...))` is called
5. `TableStorage` walks page chain to find page with space
6. If needed, allocates a new page and links it
7. `TablePage.insertRow(...)` serializes row bytes into page
8. Buffer pool flushes dirty page to disk

## 9.2 UPDATE

Flow:
1. `Engine.update(...)` resolves table and storage
2. `TableStorage.update(...)` scans table pages
3. Each row is checked against WHERE predicate
4. Matching rows get assignments applied in-memory
5. Updated rows overwrite page row region
6. Dirty pages are flushed

## 9.3 DELETE

Flow:
1. `Engine.delete(...)` resolves table and storage
2. `TableStorage.delete(...)` scans pages
3. Matching rows are removed from row list
4. Page row list is rewritten
5. Empty non-root pages may be unlinked and added to free-page set
6. Dirty pages are flushed

---

## 10. Phase I: how data is saved physically

## 10.1 Serialization

A `Row` is converted into bytes by `RowSerializer` using table column metadata.

Current serialization behavior includes support for:
- `INT`
- `BIGINT`
- `DOUBLE`
- `BOOLEAN`
- `STRING` / `VARCHAR(n)` style fixed-width storage
- `DATE`
- `TIMESTAMP`

## 10.2 Page layout

Rows live inside table pages.
A table page contains:
- row count
- next page pointer
- row payload region

## 10.3 Buffer pool

`BufferPoolManager` manages in-memory `Page` objects.
Its responsibilities:
- fetch page from memory or disk
- keep access-order cache
- flush dirty pages
- evict pages when cache is full

## 10.4 Disk manager

`FileDiskManager` is responsible for actual page read/write operations against the data file.

Main data file:
- `data/minidb.data`

So the final durable path is:

`Row -> RowSerializer -> TablePage -> BufferPoolManager -> FileDiskManager -> minidb.data`

---

## 11. Phase J: read path (SELECT)

Read flow:
1. `TableStorage.scan()` traverses page chain from root page
2. Each `TablePage` returns row list
3. Physical plan operators process rows:
   - scan
   - filter
   - projection
   - aggregate
   - sort
   - limit
4. Result rows are turned into string output by `Engine.select(...)`
5. Text protocol sends each result line to client
6. JDBC wraps returned lines in `MiniDbResultSet`

---

## 12. Phase K: how results are returned to application

### Server side
- Execution result is converted into newline-separated textual rows
- `TextProtocolHandler` emits every line
- Final terminator line is `END`

### JDBC side
- `MiniDbConnection.execute(sql)` reads lines until `END`
- `MiniDbResultSet.fromLines(...)` parses bracketed row text
- Java application consumes rows through JDBC API

This means the return path is:

`Execution rows -> string lines -> TCP socket -> JDBC parser -> ResultSet -> Java application`

---

## 13. Index flow (current)

Current indexing status is partial but important.

Implemented:
- `BPlusTree`
- `IndexManager`
- create/drop/lookup/range lookup APIs

Current behavior:
- index structures are in memory
- planner does not yet choose index scans automatically
- DML paths do not yet maintain indexes transactionally
- index state is not yet persisted to disk

So current index flow is best described as a scaffold ready for deeper integration.

---

## 14. Transaction flow (current)

There are two layers:

### 14.1 Active runtime transaction behavior
Currently used in `Engine` + `TableStorage`:
- `BEGIN` enables transaction-active mode
- storage begins per-thread snapshot logging
- `ROLLBACK` restores original page images from snapshot log
- `COMMIT` clears logs and ends transaction

### 14.2 WAL/transaction manager primitives
Implemented in code but not fully integrated into DML execution path:
- `TransactionManager`
- `WalManager`

Capabilities present:
- tx ID generation
- BEGIN/COMMIT/ABORT WAL records
- update/insert/delete WAL record methods
- WAL reading helpers
- committed transaction recovery helper

What is still pending:
- engine uses `TableStorage` transaction controls directly, not full `TransactionManager`
- automated WAL replay during server startup is not wired yet

---

## 15. Session management flow

Current session model is connection-scoped.

A session currently includes:
- one socket
- one authenticated protocol loop
- one client command stream

When the socket closes:
- session ends
- reader/writer are closed
- server worker exits

There is no separate session store yet.

---

## 16. Connection management flow

### Server side
- listener accepts sockets
- worker thread handles protocol
- bounded task queue controls concurrency

### Client side
- JDBC maintains one persistent socket per `MiniDbConnection`
- statements reuse that socket until connection close

Implication:
- connection loss usually invalidates the session immediately
- reconnect requires full auth handshake again

---

## 17. Security flow

Current security processing is:
1. Connect
2. Authenticate
3. Execute commands as authenticated connection

What exists:
- password-based auth
- basic sanitization of error output in protocol handler

What does not yet exist:
- authorization/ACLs
- encrypted transport
- audit trails
- secret rotation framework

---

## 18. Failure points along the path

Potential failure zones:
- network/socket open failure
- auth failure
- SQL parse failure
- unsupported statement/expression combination
- table or column resolution failure
- page write/read failure
- buffer pool eviction/flush failure
- transaction rollback/commit misuse

See `docs/Operations-Runbook.md` for operational handling.

---

## 19. One complete example: Java app updates data

Scenario: Java app updates one user name.

1. App gets JDBC connection.
2. Driver authenticates.
3. App calls:
   `UPDATE testdb.public.users SET name='Alicia' WHERE id=1`
4. JDBC sends SQL line.
5. Protocol handler forwards to parser.
6. Parser creates `UpdateStatement`.
7. `Engine.update(...)` resolves table.
8. `TableStorage.update(...)` scans rows.
9. Matching row is modified in page.
10. Page marked dirty and flushed.
11. Server returns `1 ROWS UPDATED`.
12. JDBC returns update count to application.

Data in `minidb.data` is now changed.

---

## 20. Related documents

- `docs/Architecture-and-Execution-Guide.md`
- `docs/Java-Application-Integration.md`
- `docs/Flow-and-Visualization.md`
- `docs/Operations-Runbook.md`

