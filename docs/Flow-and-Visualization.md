# MiniDatabase Flow and Visualization Guide

This document provides implementation-aligned flow explanations and visualization options.

## 1. End-to-end connection and query flow

```mermaid
sequenceDiagram
    autonumber
    participant App as User Java App
    participant JDBC as MiniDbDriver/Connection
    participant TCP as TcpTransportServer
    participant Proto as TextProtocolHandler
    participant Parser as SQLParserService
    participant Exec as Engine
    participant Planner as QueryPlanner
    participant Storage as TableStorage/BufferPool
    participant Disk as minidb.data

    App->>JDBC: DriverManager.getConnection(...)
    JDBC->>TCP: Open socket
    TCP->>Proto: handle(socket)
    Proto-->>JDBC: MINIDB 1.0, AUTH
    JDBC->>Proto: username/password
    Proto-->>JDBC: OK

    App->>JDBC: executeQuery("SELECT ...")
    JDBC->>Proto: SQL line
    Proto->>Parser: parse(sql)
    Parser-->>Proto: AST
    Proto->>Exec: execute(ast)
    Exec->>Planner: plan(select)
    Planner->>Storage: execute plan nodes (scan/filter/project/...)
    Storage->>Disk: read/write pages via buffer pool
    Storage-->>Exec: rows
    Exec-->>Proto: result text
    Proto-->>JDBC: lines + END
    JDBC-->>App: ResultSet
```

## 2. Write path (INSERT/UPDATE/DELETE)

```mermaid
flowchart TD
    A[Java App calls executeUpdate] --> B[JDBC sends SQL over socket]
    B --> C[TextProtocolHandler]
    C --> D[Parser builds AST]
    D --> E[Engine DML dispatch]
    E --> F[Resolve table from Catalog]
    F --> G[TableStorage mutation]
    G --> H[BufferPool fetch/modify pages]
    H --> I[Dirty page flush]
    I --> J[FileDiskManager writes minidb.data]
    J --> K[Rows affected response]
    K --> L[JDBC update count returned]
```

## 3. Data persistence flow

```mermaid
flowchart LR
    R[Row values] --> S[RowSerializer]
    S --> P[TablePage bytes]
    P --> B[BufferPoolManager]
    B --> D[FileDiskManager]
    D --> F[(minidb.data)]
```

Catalog metadata is stored separately in `data/catalog.meta`.

## 4. Transaction flow (current code behavior)

```mermaid
sequenceDiagram
    autonumber
    participant App as Client App
    participant Eng as Engine
    participant TS as TableStorage
    participant WAL as TransactionManager/WalManager

    App->>Eng: BEGIN
    Eng->>TS: beginTransaction() on cached tables
    Note over WAL: WAL classes exist but are not fully wired to Engine DML path yet

    App->>Eng: UPDATE/INSERT/DELETE
    Eng->>TS: mutate + per-thread page snapshot log

    alt COMMIT
        App->>Eng: COMMIT
        Eng->>TS: commitTransaction()
    else ROLLBACK
        App->>Eng: ROLLBACK
        Eng->>TS: rollbackTransaction() restore pages
    end
```

## 5. Session and connection lifecycle

```mermaid
stateDiagram-v2
    [*] --> SocketAccepted
    SocketAccepted --> GreetingSent
    GreetingSent --> AwaitAuth
    AwaitAuth --> Authenticated: valid credentials
    AwaitAuth --> Closed: auth failed
    Authenticated --> CommandLoop
    CommandLoop --> CommandLoop: SQL line processed
    CommandLoop --> Closed: QUIT / disconnect / IO error
    Closed --> [*]
```

## 6. Indexing flow (current scaffold)

```mermaid
flowchart TD
    A[IndexManager.createIndex(table,col)] --> B[BPlusTree allocated in memory]
    B --> C[insertEntry/lookup/rangeLookup APIs]
    C --> D[Query/runtime integration pending]
```

Status:
- In-memory index APIs are implemented.
- Planner index selection and transactional index maintenance are still pending.

## 7. Authentication and security flow

```mermaid
sequenceDiagram
    participant C as Client
    participant H as TextProtocolHandler
    participant A as AuthService

    H-->>C: MINIDB 1.0
    H-->>C: AUTH
    C->>H: username/password
    H->>A: authenticate(u,p)
    A-->>H: true/false
    alt true
        H-->>C: OK
    else false
        H-->>C: ERROR: Authentication failed
    end
```

Current security posture:
- Basic authentication exists.
- No authorization (roles/grants) yet.
- No TLS transport encryption yet.

## 8. Can we add visualization to our database?

Yes. You can add both operational and data-model visualization in phases.

### 8.1 Immediate options (no major code changes)
1. Mermaid docs (already enabled in these docs) for architecture and runtime flows.
2. Graphviz generation from catalog metadata (`db.schema.table -> columns`).
3. SQL trace timeline export (CSV/JSON) from protocol/executor logs.

### 8.2 Recommended implementation plan
1. Add `minidb-observability` module.
2. Introduce event stream interfaces:
   - connection events
   - statement lifecycle events
   - page read/write events
   - transaction events
3. Persist events to JSON log.
4. Build simple web UI (or desktop JavaFX) to visualize:
   - active sessions
   - query latency
   - hot pages
   - table and index topology

### 8.3 Suggested first visualization dashboard
- Connections: active, authenticated, failed auth count.
- Queries: per-statement counts and p95 latency.
- Storage: page read/write counters, dirty flushes.
- Transactions: begin/commit/rollback counts.

## 9. Suggested developer command set

```powershell
Set-Location "D:\projects\MiniDatabase"
mvn clean install -DskipTests --no-transfer-progress
mvn test --no-transfer-progress
```

Start server:

```powershell
Set-Location "D:\projects\MiniDatabase"
mvn -pl minidb-server -am exec:java -Dexec.mainClass="com.minidb.server.DatabaseServer"
```

Start client:

```powershell
Set-Location "D:\projects\MiniDatabase"
mvn -pl minidb-client -am exec:java -Dexec.mainClass="com.minidb.client.MiniDbClient"
```

