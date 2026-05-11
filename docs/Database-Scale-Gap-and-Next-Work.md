# MiniDatabase Scale Gap Analysis and Next Work

This document captures what is still missing for MiniDatabase to behave more like a true database, and what must change before it can comfortably handle large tables (including million-row workloads) without stalling, blowing memory, or depending on fragile in-memory state.

## Current status

The project already has the core building blocks of a database:

- catalog / schema / table metadata
- page-based storage
- buffer pool and disk-backed pages
- table page chaining
- SQL parsing and execution
- a WAL/transaction subsystem scaffold
- an in-memory B+ tree index scaffold

That means the system is no longer a toy that only stores rows in RAM. It does persist data to disk pages.

However, several critical pieces are still missing or incomplete for production-grade behavior.

## What is still missing

### 1. Crash recovery baseline is implemented, but not fully production-hardened

Startup recovery is now wired into boot flow and executes before listener startup.

Implemented behavior:
- read WAL on startup
- rebuild committed/incomplete transaction picture from logs
- REDO committed changes deterministically
- UNDO non-committed changes in reverse order
- enforce pageLSN flush-gate (`pageLSN <= flushedLSN`)
- checkpoint + WAL truncation
- persisted checkpoint metadata (`minidb.wal.checkpoint`) for replay-floor fast path

Remaining hardening:
- richer recovery telemetry and operator tooling
- policy-driven checkpoint scheduling and WAL retention

### 2. Indexes are partially query-driven, but durability lifecycle is incomplete

The planner can now choose index scans for a narrow class of predicates (`column = literal`), but index durability lifecycle remains incomplete.

Missing behavior:
- persist index metadata
- rebuild indexes from catalog/data on restart
- maintain indexes transactionally during INSERT/UPDATE/DELETE

### 3. Free-page and allocation metadata are still mostly runtime-only

Table storage currently tracks allocation hints in memory.

Risks:
- restart can lose allocator hints
- page reuse may become inefficient
- large tables can accumulate fragmentation without a durable map

### 4. Query execution still materializes many intermediate rows

The physical plan nodes currently return `List<Row>`.

That is acceptable for small tables, but risky for very large result sets because:
- intermediate results are fully buffered in memory
- `ORDER BY` and `GROUP BY` are already naturally heavy, but even plain scan/filter/projection paths still materialize entire outputs

### 5. Table scan and write paths need scale safeguards

The table page chain is iterative, so node traversal itself does not recurse. That is good.

But the old write path had a serious scaling issue: inserts walked from the root page to the tail page on every write.

That makes bulk inserts trend toward O(n²) page traversal.

Also, scans previously had a fixed page-chain safeguard that would incorrectly break large tables if they exceeded the arbitrary page limit.

### 6. Metadata durability is still basic

Catalog persistence exists, but it still relies on Java serialization and does not yet include:
- versioned migration support
- index metadata persistence
- richer system catalog objects for storage/index state

### 7. Concurrency / isolation is still simplified

The current code is not yet a fully concurrent multi-session database engine.

Missing behavior:
- row-level or page-level locking
- MVCC visibility rules
- transaction isolation levels
- deadlock handling
- concurrent write coordination

### 8. Observability and operational tooling are limited

Missing behavior:
- recovery counters and replay metrics
- page/cache metrics exposed in the server layer
- corruption detection beyond basic exception handling
- checkpoint lifecycle management

## Scale verdict for million-row workloads

### Will the node/page traversal itself break?

No, not because of recursion.
The storage path uses iterative page-chain traversal, not recursive traversal, so it will not stack overflow just because a table is large.

### Will the current implementation still struggle?

Yes, without the changes listed above.
The biggest risks are:
- full buffering of large result sets
- O(n²) insert behavior when appending many rows
- non-durable allocator / index metadata
- lack of startup recovery

### Practical expectation after the immediate fixes in this branch

With tail-page caching, streaming execution, join support, planner index-scan selection, startup recovery, flush-gate enforcement, checkpointing, and WAL truncation, the engine should handle much larger datasets and restart more safely than before.

However, it is still not a full production database until persistent index lifecycle, metadata versioning, and full concurrency/isolation are implemented.

## Required changes

### A. Storage engine

- cache the tail page for append-heavy workloads
- remove fixed scan-chain limits in favor of cycle detection
- expose streaming scan APIs
- persist allocator hints or recompute them safely on startup

### B. Executor

- add streaming execution hooks for scan/filter/project/limit paths
- keep buffering only where required (`ORDER BY`, aggregates, large materializations)
- add index-aware planning

### C. Catalog / recovery

- persist index metadata
- version catalog records
- store storage-format version markers
- add richer recovery/checkpoint telemetry
- add policy-driven checkpoint scheduler and WAL retention controls

### D. Future work for true database behavior

- locking / MVCC / isolation
- background checkpoints and retention policy controls
- durable free-page map
- page checksums and deeper on-disk validation
- query optimizer statistics

## Summary

MiniDatabase already persists data to disk pages, but it is still missing several true-database features.

For large datasets, the highest-value immediate fixes are:
1. avoid root-to-tail traversal on every insert
2. remove artificial scan limits
3. stream simple read paths instead of buffering everything
4. wire WAL recovery into startup
5. persist or rebuild allocation/index metadata reliably

