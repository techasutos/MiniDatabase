# MiniDatabase Operations Runbook

This runbook is for operating, diagnosing, and recovering the current MiniDatabase implementation.

It focuses on the implementation that is actually present now:
- TCP text protocol server
- Basic authentication
- Catalog metadata persistence
- Page-based storage engine
- Buffer pool caching
- Engine-managed transactions
- WAL and transaction manager primitives that exist in code, but are not yet fully wired into startup recovery flow

---

## 1. Scope and operational boundaries

### What this runbook covers
- Server startup/shutdown
- Authentication failures
- Connection resets and client disconnect issues
- Catalog/data file checks
- Basic corruption indicators
- Recovery and containment steps
- Post-incident follow-up

### What is not fully supported yet
- Automated crash recovery at startup using WAL replay
- TLS transport encryption
- Persistent user/role/grant authorization
- Full WAL-backed transactional durability for all DML paths
- Online consistency checking tools

When a recovery step refers to WAL, treat it as a controlled/manual engineering workflow for now, not a fully automated production recovery subsystem.

---

## 2. Key runtime files and components

### Runtime files
Assuming default startup data directory `data`:
- Catalog metadata: `data/catalog.meta`
- Data file: `data/minidb.data`
- WAL file: only when explicitly wired/created in transaction integration flow; currently primitives exist in code but startup wiring is still pending

### Main runtime components
- Server bootstrap: `minidb-server/src/main/java/com/minidb/server/DatabaseServer.java`
- TCP listener: `minidb-transport/src/main/java/com/minidb/transport/tcp/TcpTransportServer.java`
- Text protocol: `minidb-transport/src/main/java/com/minidb/transport/protocol/TextProtocolHandler.java`
- JDBC socket connection: `minidb-jdbc/src/main/java/com/minidb/jdbc/MiniDbConnection.java`
- Execution engine: `minidb-executor/src/main/java/com/minidb/executor/Engine.java`
- Table storage: `minidb-storage/src/main/java/com/minidb/storage/engine/TableStorage.java`
- Transaction primitives: `minidb-transaction/src/main/java/com/minidb/tx/TransactionManager.java`, `WalManager.java`

---

## 3. Standard start and stop procedures

### Start server
```powershell
Set-Location "D:\projects\MiniDatabase"
mvn -pl minidb-server -am exec:java -Dexec.mainClass="com.minidb.server.DatabaseServer"
```

### Start server with explicit port and data directory
Known from code:
- argument 1 = port
- argument 2 = data directory

If you run directly from built classes/jar, the main class accepts:
- `DatabaseServer [port] [dataDir]`

### Stop server
- Preferred: terminate process gracefully so shutdown hook can run.
- Current server uses a shutdown hook to stop the transport layer.

If the server is run in an IDE/terminal:
- use normal process stop first
- avoid killing the process during heavy writes if possible

---

## 4. Quick health checklist

Use this checklist before and after incidents:

- [ ] Server process is listening on expected port
- [ ] `data` directory exists
- [ ] `catalog.meta` exists after first DDL operations
- [ ] `minidb.data` exists after first DML operations
- [ ] A client can connect and authenticate
- [ ] A simple `SELECT` works
- [ ] A simple `INSERT`/`UPDATE`/`DELETE` works
- [ ] `mvn test --no-transfer-progress` passes locally if doing engineering validation

---

## 5. Troubleshooting: authentication failures

### Symptoms
- Client receives `ERROR: Authentication failed`
- JDBC connection throws `SQLException` during connect
- CLI never enters command loop after login

### Current authentication behavior
From `TextProtocolHandler`:
1. Server sends `MINIDB 1.0`
2. Server sends `AUTH`
3. Client sends username/password
4. `AuthService.authenticate(...)` returns true/false

Current implementation uses `InMemoryAuthService` with:
- default `admin` / `minidb`
- optional extra admin account from env vars:
  - `MINIDB_USER`
  - `MINIDB_PASSWORD`

### Checks
- Confirm client is using the expected host/port
- Confirm credentials match current server startup environment
- If env vars were used, verify server process inherited them
- Confirm client sends plain lines, no extra prompt text

### Recovery actions
1. Retry with default credentials:
```text
user=admin
password=minidb
```
2. Restart server with explicit env vars if needed.
3. Re-test with CLI before testing JDBC app.

### Root cause categories
- Wrong username/password
- Server started without expected env vars
- Custom client not following line protocol correctly
- Client connected to wrong port/process

---

## 6. Troubleshooting: connection resets and disconnects

### Symptoms
- JDBC throws `Lost connection to server`
- CLI disconnects mid-query
- TCP client sees socket closed after auth or during command loop

### Current connection/session model
- One socket = one protocol session
- `TextProtocolHandler` owns the socket until QUIT/disconnect/error
- `TcpTransportServer` uses a bounded worker queue and fixed worker pool

### Common causes
- Server process stopped or crashed
- Client sent malformed or unexpected data
- Socket closed after auth failure
- Client application dropped connection
- Resource pressure causing process termination or thread exhaustion

### Checks
- Verify server process is still running
- Check whether auth completed successfully before disconnect
- Re-test with simple command like `SELECT * FROM ...`
- Check whether many clients are opening connections simultaneously

### Recovery actions
1. Reconnect with a fresh client socket.
2. Restart server if it stopped unexpectedly.
3. Reduce client burst load if queue saturation is suspected.
4. Validate protocol framing with CLI or raw-socket test.

### Preventive practices
- Use connection pooling in Java app only after validating current JDBC baseline for your workload
- Keep SQL single-line in current protocol path
- Add retries in client app for transient connection failures

---

## 7. Troubleshooting: query failures

### Symptoms
- `ERROR: Use db.schema.table format`
- `Column not found`
- `Column count mismatch`
- `Unsupported statement` or `Unsupported operation`

### Checks
- Confirm fully qualified table names: `db.schema.table`
- Confirm table/schema/database exists in catalog
- Confirm insert column counts match table shape
- Confirm SQL uses currently supported syntax

### Recovery actions
1. Re-run DDL to ensure catalog objects exist.
2. Use minimal reproducible SQL.
3. Start with `SELECT *` before complex SELECT clauses.
4. For DML, verify exact column names from DDL.

---

## 8. Corruption indicators and consistency checks

Because the current implementation is still a foundation engine, corruption detection is mostly observational.

### Possible corruption indicators
- `SELECT` returns missing rows after successful writes
- Duplicate or unexpected row counts in scans
- Page chain loops or traversal errors
- Catalog says table exists but scan/read behavior is inconsistent
- Exceptions around page traversal or storage mutation

### What to inspect
- `catalog.meta` exists and is readable
- `minidb.data` file size is non-zero after writes
- DDL objects can still be resolved by catalog
- Repeated scan of same table returns stable results

### Practical consistency checks
Use a known table and run:
```sql
SELECT * FROM db.schema.table
SELECT COUNT(*) FROM db.schema.table
```
Compare returned row set and count with expected values.

### Storage-focused engineering validation
```powershell
Set-Location "D:\projects\MiniDatabase"
mvn -pl minidb-storage test --no-transfer-progress
```

### Full-project validation
```powershell
Set-Location "D:\projects\MiniDatabase"
mvn test --no-transfer-progress
```

---

## 9. Recovery steps

## 9.1 Soft recovery (preferred first)
Use this when the server is unhealthy but files are still present.

1. Stop server gracefully.
2. Back up current `data` directory.
3. Restart server.
4. Reconnect with CLI/JDBC.
5. Validate catalog objects and row counts.

## 9.2 Recovery after failed transaction behavior
Current engine-level rollback restores per-thread page snapshots held by `TableStorage`.

If application-level transaction failed:
1. Disconnect client session.
2. Restart server if session state is uncertain.
3. Re-run read-only validation queries.
4. Replay failed business operation from application layer if needed.

## 9.3 Controlled file recovery workflow
If storage inconsistency is suspected:

1. Stop server.
2. Copy `data/catalog.meta` and `data/minidb.data` to a safe location.
3. Preserve any WAL file if transaction integration has been enabled in your branch.
4. Start a clean server against a fresh data directory.
5. Recreate schema objects if necessary.
6. Restore/replay data from application source of truth or engineering export.

## 9.4 WAL-assisted recovery (engineering/manual only for now)
Current code contains:
- `WalManager.readAll()`
- `TransactionManager.recoverCommittedTxIds()`

This means manual engineering recovery can inspect WAL records, but the server does not yet automatically replay them at startup.

Recommended manual path:
1. Stop server.
2. Preserve data files and WAL file.
3. Use a controlled engineering utility/test harness to inspect WAL records.
4. Determine committed transaction set.
5. Apply custom replay logic in a maintenance branch/tool if required.

Do not treat WAL as a production-ready self-healing path yet.

---

## 10. Incident response playbooks

## 10.1 Auth failure playbook
- Verify server port
- Verify credentials
- Verify env vars if used
- Test with CLI
- Restart server if environment mismatch suspected

## 10.2 Connection reset playbook
- Verify process alive
- Retry with simple client
- Check auth phase success/failure
- Restart server if repeated disconnects persist

## 10.3 Suspected corruption playbook
- Stop writes immediately
- Back up current `data` directory
- Run storage/full test suite in engineering environment
- Start fresh data directory for comparison
- Compare expected catalog + row counts

## 10.4 Failed deployment playbook
- Keep old data directory backup
- Roll back code build if new behavior caused incompatibility
- Re-run:
```powershell
Set-Location "D:\projects\MiniDatabase"
mvn clean install -DskipTests --no-transfer-progress
mvn test --no-transfer-progress
```

---

## 11. Logging and observability gaps

Current logging is Java util logging and is useful but limited.

Missing today:
- Structured log correlation IDs
- Session IDs
- Request tracing
- Query timing metrics
- Page/cache metrics export

Recommended next step:
- add query/session identifiers to logs
- emit statement start/end, auth result, connection open/close events

---

## 12. Post-incident follow-up checklist

- [ ] Capture failing SQL or connection scenario
- [ ] Preserve `data` directory snapshot
- [ ] Preserve server startup parameters and env vars
- [ ] Note whether incident happened during DDL, DML, SELECT, or transaction control
- [ ] Reproduce in isolated local environment
- [ ] Add regression test if root cause was code defect
- [ ] Update this runbook if new operational behavior is introduced

---

## 13. Related documentation

- `docs/Architecture-and-Execution-Guide.md`
- `docs/Java-Application-Integration.md`
- `docs/Flow-and-Visualization.md`
- `docs/End-to-End-Data-Flow.md`

