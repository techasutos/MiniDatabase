# MiniDatabase Architecture and Execution Guide

## 1. Current architecture (implemented)

The runtime stack currently works as follows:

1. Client application (JDBC or raw TCP)
2. Transport listener (`TcpTransportServer`)
3. Protocol handler (`TextProtocolHandler`)
4. SQL parser (`SQLParserService`)
5. Execution engine (`Engine`)
6. Planner (`QueryPlanner`) for SELECT
7. Storage (`TableStorage`, `BufferPoolManager`, `FileDiskManager`)
8. Catalog (`CatalogManager`, `CatalogStore`)

Main bootstrap entrypoint:
- `minidb-server/src/main/java/com/minidb/server/DatabaseServer.java`

## 2. Module responsibilities

### `minidb-server`
- Wires parser, catalog, engine, auth, protocol, and transport.
- Starts TCP listener.
- Adds shutdown hook to stop transport cleanly.

### `minidb-transport`
- `TransportServer` lifecycle contract.
- `TcpTransportServer` accepts sockets and dispatches each client to worker pool.
- `ProtocolHandler` abstraction.
- `TextProtocolHandler` implements handshake, authentication, command loop, and response framing.

### `minidb-catalog`
- Metadata for databases/schemas/tables/columns.
- Catalog persistence through `CatalogStore`.

### `minidb-sql-parser`
- SQL grammar parsing into AST (`Statement`, `SelectStatement`, `InsertStatement`, etc.).

### `minidb-executor`
- `Engine` dispatches DDL/DML/transaction commands and SELECT.
- SELECT path goes through `QueryPlanner` + physical plan nodes.

### `minidb-storage`
- `FileDiskManager`: file page I/O.
- `BufferPoolManager`: in-memory page cache with LRU ordering and flush/evict.
- `TableStorage`: table insert/scan/update/delete/compact over chained table pages.
- `IndexManager` + `BPlusTree`: in-memory indexing primitives.

### `minidb-transaction`
- `WalManager`: append-only WAL records and flush/checkpoint primitives.
- `TransactionManager`: tx IDs and tx state transitions.

### `minidb-jdbc`
- JDBC driver (`MiniDbDriver`) and baseline JDBC API objects.
- Socket-backed connection (`MiniDbConnection`) speaking MiniDB text protocol.

### `minidb-client`
- Interactive CLI client over text protocol.

## 3. Connection management

Connection handling is currently socket-based and server-side per-client-thread execution:

1. `TcpTransportServer` accepts socket.
2. Socket is submitted to worker pool.
3. `TextProtocolHandler.handle(socket)` runs full lifecycle for that client.
4. On disconnect or QUIT, socket is closed.

Key properties:
- Backpressure: bounded queue (`LinkedBlockingQueue<>(1024)`) + `CallerRunsPolicy`.
- Worker model: fixed-size thread pool configured at startup.
- Session identity: tied to TCP socket lifetime.

## 4. Session management

A "session" currently means one active protocol loop per socket.

Session state currently includes:
- Authenticated user identity (in handler scope)
- Connection stream state (reader/writer)
- Command loop context

Not yet implemented:
- Server-side session IDs
- Session metadata catalog
- Session timeouts/quotas
- Session-scoped variables

## 5. Authentication and security layer

### Implemented now
- Challenge phase:
  - Server sends `MINIDB 1.0` then `AUTH`
  - Client sends username/password lines
  - Server validates using `AuthService`
- Default auth service:
  - `InMemoryAuthService`
  - Default account: `admin` / `minidb`
  - Optional env override: `MINIDB_USER`, `MINIDB_PASSWORD`

### Current security boundaries
- Authentication: yes (basic).
- Authorization (roles/grants): no (not yet implemented).
- Transport encryption (TLS): no (plaintext TCP).
- Password-at-rest policy: in-memory hash service exists, but no persistent user catalog yet.

### Practical guidance
- Do not expose current server directly to untrusted networks.
- Use private network or reverse proxy tunnel while TLS is not implemented.

## 6. Data persistence model (how data is saved)

### Files currently used
- Catalog metadata: `data/catalog.meta`
- Data pages: `data/minidb.data`

### Storage write path (current)
1. Engine resolves target table via catalog.
2. Engine gets `TableStorage` instance from per-table cache.
3. `TableStorage.insert/update/delete` fetches page(s) via buffer pool.
4. Page rows are serialized/deserialized by row/page helpers.
5. Dirty pages are flushed by `BufferPoolManager.flushPage(...)`.
6. Disk writes happen through `FileDiskManager.writePage(...)`.

### Page structure (current)
- Chained table pages using `nextPageId` in header.
- Root page from table metadata.
- New pages allocated with monotonic page ID counter in `TableStorage` instance.

### Buffer pool notes
- Access-ordered LRU map.
- Dirty page flushing supported.
- Explicit pin API exists; current code path mainly relies on fetch/flush.

## 7. Query execution model (how queries are executed)

### Protocol to engine path
1. Text command arrives from socket.
2. Parsed to AST by `SQLParserService`.
3. `Engine.execute(stmt)` dispatches by statement type.

### DDL path
- Handled by `DDLExecutor` (create/drop database/schema/table).

### DML path
- INSERT/UPDATE/DELETE handled directly by `Engine` + `TableStorage`.
- WHERE predicates evaluated by expression evaluation against row context.

### SELECT path
1. `QueryPlanner.plan(SelectStatement)` builds physical node tree.
2. Plan nodes execute recursively.
3. Result rows are converted to textual lines and sent back.

Implemented SELECT operators include:
- Scan
- Filter
- Projection
- Aggregate
- Sort
- Limit/Offset

## 8. Indexing model (current and planned behavior)

### Implemented now
- `BPlusTree` data structure in memory.
- `IndexManager` manages per-table/per-column trees.
- Supports:
  - create/drop index objects
  - exact lookup
  - range lookup
  - rebuild-from-rows utility

### Not fully integrated yet
- Planner does not yet select index scans automatically.
- DML paths are not yet maintaining indexes transactionally.
- Index state is not yet persisted to disk.

## 9. Transaction model (current behavior)

There are two transaction-related layers in code:

1. `Engine` + `TableStorage` transaction controls (currently active in execution flow)
   - `BEGIN` sets engine transaction flag.
   - `TableStorage.beginTransaction()` starts per-thread snapshot logging.
   - `COMMIT` clears tx logs.
   - `ROLLBACK` restores original page snapshots in `TableStorage`.

2. `TransactionManager` + `WalManager` (implemented but not fully wired into DML path yet)
   - Generates transaction IDs and records BEGIN/COMMIT/ABORT in WAL.
   - Has APIs for update/insert/delete log records.
   - Recovery helpers present for replay planning.

Important current state:
- Transaction commands work for active `Engine` storage operations.
- Full WAL-backed atomicity/recovery integration is pending.

## 10. Error and response model

Protocol responses are line-based:
- Success returns zero or more result lines.
- `END` marks result completion.
- Error returns `ERROR: <message>` then `END`.

JDBC layer interprets these lines and raises `SQLException` on `ERROR:` payloads.

## 11. Performance characteristics (current)

Strengths:
- Lightweight startup.
- Simple path from socket to execution.
- Page caching in memory.

Current bottlenecks:
- Text protocol serialization.
- No vectorized execution.
- No cost-based optimization.
- Index usage not planner-driven.
- Transaction/WAL integration incomplete.

## 12. Immediate hardening priorities

1. Wire `TransactionManager`/`WalManager` into Engine DML commit path.
2. Persist and integrate indexes with planner.
3. Add TLS and persistent authz model.
4. Add crash-recovery bootstrap sequence at server startup.
5. Add execution metrics and trace IDs for operability.

