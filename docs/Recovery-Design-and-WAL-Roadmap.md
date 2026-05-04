# Recovery Design and WAL Roadmap

This document explains the current recovery behavior in MiniDatabase, how the existing WAL and transaction primitives relate to that behavior, and the roadmap to move from the current foundation to a real crash-recovery model.

This guide separates:
- implemented now
- partially implemented but not fully integrated
- roadmap-only design

---

## 1. Recovery model today

MiniDatabase currently has two different recovery-related mechanisms.

## 1.1 Implemented now: engine/storage rollback for active transactions
Current runtime transaction behavior is implemented primarily through:
- `Engine`
- `TableStorage`

The current model is:
1. `BEGIN` marks transaction active in `Engine`
2. Cached `TableStorage` instances enter transaction mode
3. Before a page is modified, original page bytes are copied into per-thread transaction log
4. `ROLLBACK` restores original page images from that per-thread log
5. `COMMIT` clears the in-memory transaction log

This gives MiniDatabase a working session-local rollback path for current mutation operations.

### Important limitation
This rollback model is in-memory and session-bound.
It is not crash recovery.
If the process dies, the in-memory transaction snapshot log is gone.

---

## 2. WAL primitives currently implemented

There is a real WAL subsystem scaffold in code:
- `minidb-transaction/src/main/java/com/minidb/tx/WalManager.java`
- `minidb-transaction/src/main/java/com/minidb/tx/TransactionManager.java`

## 2.1 What `WalManager` currently supports
- append log records
- force flush to disk
- checkpoint marker record
- read all WAL records back

Log record types currently defined:
- `BEGIN`
- `INSERT`
- `UPDATE`
- `DELETE`
- `COMMIT`
- `ABORT`
- `CHECKPOINT`

## 2.2 What `TransactionManager` currently supports
- transaction ID generation
- active/committed/aborted state table
- begin/commit/rollback state transitions
- logging of begin/commit/abort
- helper methods for insert/update/delete WAL records
- helper to recover committed tx IDs from WAL stream

---

## 3. Current gap between rollback and WAL

This is the most important architectural fact right now:

### What is active in runtime flow
- `Engine` currently uses `TableStorage.beginTransaction()/commitTransaction()/rollbackTransaction()`
- mutation code paths are not fully driven by `TransactionManager`

### What exists but is not fully wired
- `TransactionManager`
- `WalManager`
- startup recovery design hooks

### Consequence
MiniDatabase currently has:
- working logical session rollback for active process lifetime
- no fully automated crash recovery on restart yet
- no guarantee that all DML changes are WAL-logged before flush in current runtime path

---

## 4. Desired recovery architecture

The target recovery architecture should become:

1. Client begins transaction
2. `TransactionManager` assigns tx ID
3. Every data-changing operation logs before-image/after-image or equivalent WAL record
4. WAL is flushed before commit acknowledgment
5. Dirty data pages may flush after WAL safety is satisfied
6. On crash/restart, startup recovery replays WAL:
   - REDO committed operations
   - UNDO incomplete/aborted operations if needed

This is the classic write-ahead logging contract:
- log first
- data pages later

---

## 5. Current WAL record structure

`WalManager` currently writes binary records containing fields such as:
- LSN
- transaction ID
- record type
- page ID
- offset
- before image
- after image
- CRC32

This is already a strong foundation for future recovery.

### Current status
- append and read APIs exist
- CRC is written, but full validation flow is not yet enforced during recovery
- startup replay is not yet wired into `DatabaseServer`

---

## 6. Recovery states that exist today

## 6.1 Logical rollback during active session
Implemented now.

If a client does:
- `BEGIN`
- mutation statements
- `ROLLBACK`

Then `TableStorage.rollbackTransaction()` restores captured original pages.

## 6.2 Process crash recovery after restart
Not fully implemented yet.

Pieces available:
- WAL file abstraction
- WAL readback
- committed tx scanning

Missing pieces:
- server bootstrap recovery phase
- page REDO application loop
- incomplete tx handling policy
- checkpoint lifecycle management

---

## 7. Recommended recovery lifecycle for next implementation step

## 7.1 Startup recovery phase
Add startup flow in `DatabaseServer` roughly as:

1. Open catalog and storage
2. Initialize WAL manager
3. Read WAL records
4. Determine committed vs incomplete txs
5. REDO committed records
6. Optionally UNDO incomplete records if using undo-capable scheme
7. Open transport listener only after recovery completes

## 7.2 Mutation path integration
Update DML execution path so that:
- `INSERT`, `UPDATE`, `DELETE` run under `TransactionManager`
- each page mutation writes WAL before data page flush
- commit flushes WAL before success response is returned

## 7.3 Checkpoint integration
Introduce periodic checkpoints to:
- bound replay time
- allow WAL truncation/segmentation later
- improve restart speed

---

## 8. Recommended recovery modes

A practical staged roadmap is:

### Stage 1: REDO-only committed replay
Simplest integration path.
- log committed modifications
- replay committed updates on restart
- incomplete txs ignored if pages were not flushed unsafely

### Stage 2: REDO + abort awareness
- detect incomplete txs
- prevent committed/incomplete ambiguity

### Stage 3: REDO + UNDO recovery
- full crash-safe transactional recovery behavior
- requires well-defined before-image handling and replay ordering

---

## 9. Failure scenarios and how they map to current code

## 9.1 Normal rollback requested by client
Current outcome:
- works in active process using `TableStorage` page snapshot restoration

## 9.2 Server crash before commit
Current outcome:
- behavior depends on whether dirty pages were flushed
- no full startup WAL recovery path yet
- this is why durability semantics are not production-complete yet

## 9.3 Server crash after commit acknowledgment
Target outcome should be:
- committed transaction survives restart because WAL commit record was flushed before ack

Current status:
- that full guarantee is not yet wired end-to-end in execution flow

---

## 10. WAL roadmap by milestone

## Milestone A — Integrate transaction manager into engine
- create shared `TransactionManager` inside server/engine runtime
- replace pure boolean transaction flag with tx-manager-driven transaction lifecycle
- attach tx ID to mutation operations

## Milestone B — Log all DML mutations
- `INSERT` writes WAL record
- `UPDATE` writes before/after image WAL record
- `DELETE` writes before image WAL record

## Milestone C — Enforce write-ahead rule
- do not flush dirty page beyond WAL safety point
- commit must flush WAL before returning success

## Milestone D — Startup recovery
- load WAL on boot
- determine replay plan
- REDO committed records
- establish clean runtime state before opening network listener

## Milestone E — Checkpoints and WAL lifecycle
- checkpoint records
- segment/truncate WAL
- shorter restart time

## Milestone F — Observability and operator tooling
- recovery logs
- replay counters
- checkpoint status
- corruption/CRC validation reports

---

## 11. Suggested engineering design for startup recovery

Recommended order at startup:

1. Open data directory
2. Open catalog store
3. Open disk/storage engine
4. Open WAL manager
5. Scan WAL records
6. Build transaction state map from log
7. REDO committed data modifications in LSN order
8. Mark recovery complete
9. Start transport listener

Why this order matters:
- users should never connect while recovery is still mutating state

---

## 12. Recovery safety checklist

Before calling recovery production-ready, ensure all are true:

- [ ] All DML writes emit WAL
- [ ] Commit acknowledgment happens only after WAL flush
- [ ] Dirty page flush respects WAL ordering
- [ ] Startup recovery runs before listener start
- [ ] Replay order is deterministic
- [ ] Incomplete tx behavior is defined
- [ ] Checkpoints exist
- [ ] CRC validation is enforced on replay

---

## 13. Operational recommendations until full recovery is implemented

Because startup recovery is not fully wired yet:
- treat graceful shutdown as important
- back up `data` directory regularly
- avoid assuming crash-safe durability semantics under abrupt process termination
- use the runbook for manual containment and recovery workflow

---

## 14. Relationship to existing docs

This guide should be read alongside:
- `docs/Operations-Runbook.md`
- `docs/Architecture-and-Execution-Guide.md`
- `docs/End-to-End-Data-Flow.md`

---

## 15. Summary

MiniDatabase already has a meaningful WAL and transaction foundation in code.

Current reality:
- active-session rollback works
- WAL infrastructure exists
- startup recovery is not fully integrated yet

The next major engineering milestone is not inventing WAL from scratch — it is wiring the existing WAL and transaction primitives into the real DML and server startup lifecycle so the database becomes crash-recoverable, not just rollback-capable.

