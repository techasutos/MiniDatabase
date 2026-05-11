package com.minidb.tx;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Transaction Manager — coordinates ACID transactions.
 *
 * Responsibilities:
 *  - Assign unique transaction IDs (XID)
 *  - Track active / committed / aborted transactions
 *  - Write WAL BEGIN / COMMIT / ABORT records
 *  - Support MVCC-style visibility checks in the future
 *
 * Each session calls beginTransaction(), then operates, then commit() or rollback().
 */
public class TransactionManager {

    private static final Logger LOG = Logger.getLogger(TransactionManager.class.getName());

    public enum TxState { ACTIVE, COMMITTED, ABORTED }

    public record TransactionContext(long txId, long startLsn, TxState state) {}

    // ── State ──────────────────────────────────────────────────────────────

    private final WalManager wal;
    private final AtomicLong txIdGen = new AtomicLong(1L);

    /** Active and recent transactions, keyed by txId */
    private final ConcurrentHashMap<Long, TxState> txTable = new ConcurrentHashMap<>();

    /** Per-thread current transaction */
    private final ThreadLocal<Long> currentTx = new ThreadLocal<>();

    // ── Constructor ────────────────────────────────────────────────────────

    public TransactionManager(WalManager wal) {
        this.wal = wal;
    }

    // ── Transaction Lifecycle ──────────────────────────────────────────────

    /**
     * Begin a new transaction for the current thread.
     * @return assigned transaction ID
     */
    public long begin() throws IOException {
        if (currentTx.get() != null) {
            throw new IllegalStateException("Thread already has an active transaction: " + currentTx.get());
        }
        long txId = txIdGen.getAndIncrement();
        txTable.put(txId, TxState.ACTIVE);
        currentTx.set(txId);
        wal.append(txId, WalManager.RecordType.BEGIN, -1, -1, null, null);
        LOG.fine(() -> "TX BEGIN txId=" + txId);
        return txId;
    }

    /**
     * Commit the current thread's transaction.
     */
    public void commit() throws IOException {
        long txId = requireActive();
        long lsn  = wal.append(txId, WalManager.RecordType.COMMIT, -1, -1, null, null);
        wal.flush(lsn); // force WAL to disk before acknowledging commit
        txTable.put(txId, TxState.COMMITTED);
        currentTx.remove();
        LOG.fine(() -> "TX COMMIT txId=" + txId);
    }

    /**
     * Abort / rollback the current thread's transaction.
     * The caller is responsible for applying UNDO using WAL records.
     */
    public void rollback() throws IOException {
        long txId = requireActive();
        wal.append(txId, WalManager.RecordType.ABORT, -1, -1, null, null);
        txTable.put(txId, TxState.ABORTED);
        currentTx.remove();
        LOG.fine(() -> "TX ABORT txId=" + txId);
    }

    /**
     * Log a data-modification record on behalf of the active transaction.
     */
    public long logUpdate(int pageId, int offset, byte[] before, byte[] after) throws IOException {
        long txId = requireActive();
        return wal.append(txId, WalManager.RecordType.UPDATE, pageId, offset, before, after);
    }

    public long logInsert(int pageId, int offset, byte[] after) throws IOException {
        long txId = requireActive();
        return wal.append(txId, WalManager.RecordType.INSERT, pageId, offset, null, after);
    }

    public long logDelete(int pageId, int offset, byte[] before) throws IOException {
        long txId = requireActive();
        return wal.append(txId, WalManager.RecordType.DELETE, pageId, offset, before, null);
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public Long currentTxId() {
        return currentTx.get();
    }

    public boolean isActive(long txId) {
        return txTable.getOrDefault(txId, TxState.ABORTED) == TxState.ACTIVE;
    }

    public boolean hasActiveTx() {
        return currentTx.get() != null;
    }

    public boolean hasAnyActiveTx() {
        return txTable.containsValue(TxState.ACTIVE);
    }

    public long getFlushedLsn() {
        return wal.getFlushedLsn();
    }

    public void flushWalUpTo(long lsn) throws IOException {
        wal.flush(lsn);
    }

    public long latestCheckpointLsn() throws IOException {
        return wal.latestCheckpointLsn();
    }

    public long writeCheckpoint() throws IOException {
        long checkpointLsn = wal.checkpoint(0L);
        wal.flush(checkpointLsn);
        return checkpointLsn;
    }

    public void truncateWalBefore(long keepFromLsn) throws IOException {
        wal.truncateBeforeLsn(keepFromLsn);
    }

    /**
     * Return all WAL records for crash recovery (REDO pass).
     */
    public List<WalManager.WalRecord> getWalRecordsForRecovery() throws IOException {
        return wal.readAll();
    }

    // ── Crash Recovery ─────────────────────────────────────────────────────

    /**
     * Perform REDO recovery from WAL on startup.
     * Returns the set of committed transaction IDs found in the log.
     */
    public Set<Long> recoverCommittedTxIds() throws IOException {
        Set<Long> committed = new HashSet<>();
        for (WalManager.WalRecord record : wal.readAll()) {
            if (record.type() == WalManager.RecordType.COMMIT) {
                committed.add(record.txId());
            }
            txIdGen.updateAndGet(v -> Math.max(v, record.txId() + 1));
        }
        return committed;
    }

    public void close() throws IOException {
        wal.close();
    }

    // ── Internal ───────────────────────────────────────────────────────────

    private long requireActive() {
        Long txId = currentTx.get();
        if (txId == null) {
            throw new IllegalStateException("No active transaction on current thread");
        }
        return txId;
    }
}

