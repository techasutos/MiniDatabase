# Recovery Design and WAL Roadmap

This document tracks what is already implemented for crash recovery and WAL safety, and what is still pending before MiniDatabase can be considered production-grade.

## Achieved in current branch

### 1) WAL-backed transaction lifecycle is wired into runtime

- `Engine` creates a shared `TransactionManager` and `WalManager`.
- `BEGIN`/`COMMIT`/`ROLLBACK` flow is managed through transaction manager APIs.
- Commit records are flushed before commit is acknowledged.

### 2) WAL logging moved to physical page mutation path

- `TableStorage` logs page before-image and after-image around actual page updates.
- DML page mutations (`INSERT`/`UPDATE`/`DELETE` and page-link edits) now emit WAL update records in transaction context.

### 3) PageLSN + flush-gate enforcement is active

- Each dirty page carries a `pageLsn`.
- `BufferPoolManager` enforces `pageLSN <= flushedLSN` before page flush.
- If a WAL flusher callback is configured, buffer flush attempts to flush WAL up to pageLSN first.
- If WAL cannot be advanced, page flush fails fast.

### 4) Startup recovery is implemented and server-gated

- Recovery runs before server starts accepting traffic.
- REDO is applied for committed data records.
- UNDO-style revert is applied for non-committed transactions in reverse order.

### 5) Checkpoint + truncation flow is implemented

- Quiescent checkpoint API (`Engine.checkpointAndTruncateWal`) flushes pages, writes checkpoint, and truncates WAL history before checkpoint LSN.
- WAL file segmentation and truncation are supported.

### 6) WAL integrity checks are enforced

- WAL records include CRC32.
- WAL read path verifies CRC and rejects corrupted records.

### 7) Checkpoint metadata persistence is implemented

- A durable metadata file (`minidb.wal.checkpoint`) stores the last flushed checkpoint LSN.
- Metadata writes are atomic (`.tmp` + move, with non-atomic fallback).
- Recovery uses metadata as fast-path replay floor and falls back to checkpoint record scan if metadata is missing/corrupt/stale.

## Runtime artifacts

Given a data directory with `minidb.wal`, the following files/directories are now expected:

- `minidb.wal`
- `minidb.wal.segments/` (rotated segments)
- `minidb.wal.checkpoint` (latest durable checkpoint LSN)

## Test coverage implemented

- `minidb-transaction/src/test/java/com/minidb/tx/WalManagerTest.java`
  - truncation retains checkpoint and newer records
  - CRC corruption detection path
  - checkpoint metadata persistence across restart
  - fallback when checkpoint metadata is corrupt
- `minidb-storage/src/test/java/com/minidb/storage/BufferPoolWalGateTest.java`
  - WAL gate blocks flush when WAL is behind
  - WAL gate auto-flushes WAL when flusher callback is present
- `minidb-executor/src/test/java/com/minidb/executor/EngineIntegrationTest.java`
  - committed/uncommitted recovery behavior
  - checkpoint truncation data correctness
  - stale checkpoint metadata fallback path

## What is still pending

### 1) Concurrency and isolation

- No MVCC visibility model yet.
- No lock manager/deadlock handling yet.
- Transaction semantics are still simplified for single-node correctness, not full concurrent correctness.

### 2) Index durability and recovery completeness

- Planner index-scan support exists for simple predicates, but index metadata lifecycle still needs durable catalog integration and restart rebuild guarantees.
- WAL does not yet include dedicated index-page logging/replay.

### 3) Stronger checkpoint policy

- Checkpointing is currently manual/quiescent.
- Background/interval/size-triggered checkpoint scheduling is not implemented yet.

### 4) WAL retention policy and operations

- Truncation is checkpoint-driven but not policy-driven (for example, keep last N segments or time-based retention).
- Operator controls and telemetry around WAL growth are minimal.

### 5) Recovery observability

- Recovery emits basic logs.
- Detailed metrics (redo count, undo count, elapsed phases, corruption counters) are not yet exposed as first-class telemetry.

## Practical status summary

MiniDatabase has crossed from "WAL scaffold" to a working crash-recovery baseline:

- write-ahead gate enforcement is active
- startup recovery is integrated
- checkpoint + truncation works
- corruption checks are enforced
- checkpoint replay floor is persisted

The next maturity phase is mainly around concurrent transaction correctness, index durability lifecycle, and operational automation/observability.

