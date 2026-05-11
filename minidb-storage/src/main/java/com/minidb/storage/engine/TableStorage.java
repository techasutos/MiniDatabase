package com.minidb.storage.engine;

import com.minidb.catalog.model.Table;
import com.minidb.storage.buffer.BufferPoolManager;
import com.minidb.storage.index.IndexManager;
import com.minidb.storage.page.Page;
import com.minidb.storage.page.TablePage;
import com.minidb.storage.row.Row;
import com.minidb.tx.TransactionManager;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

/**
 * Manages storage of a single table, handling page allocation,
 * row insertion, and scanning. It interacts with the BufferPoolManager to fetch and flush pages.
 * Each table is organized as a linked list of TablePages, starting from the root page.
 * This is a simplified implementation for demonstration purposes. In a production system,
 * you would need to handle free page management, concurrency control, and more complex page structures.
 *
 */
public class TableStorage {

    private final BufferPoolManager bufferPool;
    private final Table table;
    private final TransactionManager transactionManager;
    // Monotonic page-id allocator for this table instance.
    private int nextPageIdCounter;

    // Cached tail page for append-heavy workloads.
    private int tailPageId;

    // Free page list for reuse (in-memory for now)
    private final java.util.Set<Integer> freePages = new java.util.HashSet<>();

    // Simple transaction state (for demonstration)
    private final ThreadLocal<Boolean> inTransaction = ThreadLocal.withInitial(() -> false);

    // Per-thread transaction log: pageId -> original page data
    private final ThreadLocal<java.util.Map<Integer, byte[]>> txLog = ThreadLocal.withInitial(java.util.HashMap::new);

    public TableStorage(BufferPoolManager bufferPool, Table table) {
        this(bufferPool, table, null);
    }

    public TableStorage(BufferPoolManager bufferPool, Table table, TransactionManager transactionManager) {
        this.bufferPool = bufferPool;
        this.table = table;
        this.transactionManager = transactionManager;
        // Initialize root header once for new tables.
        try {
            Page rootPage = bufferPool.fetchPage(table.getRootPageId());
            ByteBuffer buffer = ByteBuffer.wrap(rootPage.getData());
            int rowCount = buffer.getInt(0);
            int nextPageId = buffer.getInt(4);

            if (rowCount == 0 && nextPageId == 0) {
                buffer.putInt(0, 0); // rowCount
                buffer.putInt(4, -1); // nextPageId
                markDirtyAndMaybeFlush(rootPage, 0L);
            }
            // Initialize allocator once from current known max.
            this.nextPageIdCounter = findMaxPageId();
            this.tailPageId = findTailPageId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize root page header", e);
        }
    }

    /**
     * Begins a transaction for the current thread.
     */
    public synchronized void beginTransaction() {
        inTransaction.set(true);
        txLog.get().clear();
    }

    /**
     * Commits the transaction for the current thread.
     */
    public synchronized void commitTransaction() {
        // In a real system, flush/commit all changes atomically
        inTransaction.set(false);
        txLog.get().clear();
    }

    /**
     * Rolls back the transaction for the current thread.
     */
    public synchronized void rollbackTransaction() throws Exception {
        // Restore original page data from log
        for (var entry : txLog.get().entrySet()) {
            int pageId = entry.getKey();
            byte[] original = entry.getValue();
            Page page = bufferPool.fetchPage(pageId);
            System.arraycopy(original, 0, page.getData(), 0, original.length);
            markDirtyAndMaybeFlush(page, 0L);
        }
        inTransaction.set(false);
        txLog.get().clear();
    }

    // Helper: log original page data if in transaction
    private void logPageIfNeeded(int pageId) throws Exception {
        if (inTransaction.get() && !txLog.get().containsKey(pageId)) {
            Page page = bufferPool.fetchPage(pageId);
            byte[] copy = java.util.Arrays.copyOf(page.getData(), page.getData().length);
            txLog.get().put(pageId, copy);
        }
    }

    public synchronized void insert(Row row) throws Exception {
        int pageId = resolveTailPageId();
        Page page = bufferPool.fetchPage(pageId);
        TablePage tablePage = new TablePage(page, table);

        if (!tablePage.hasSpace()) {
            byte[] tailBefore = copyPageBytes(page);
            int newPageId = allocateNewPage();
            TablePage newPage = new TablePage(bufferPool.fetchPage(newPageId), table);
            logPageIfNeeded(newPageId);
            tablePage.setNextPageId(newPageId);
            long tailLsn = logWalChange(page.getPageId(), 0, tailBefore, copyPageBytes(page));
            markDirtyAndMaybeFlush(page, tailLsn);
            tablePage = newPage;
            pageId = newPageId;
            tailPageId = newPageId;
        }

        logPageIfNeeded(pageId);
        Page targetPage = bufferPool.fetchPage(pageId);
        byte[] before = copyPageBytes(targetPage);
        tablePage.insertRow(row);
        long lsn = logWalChange(pageId, 0, before, copyPageBytes(targetPage));
        markDirtyAndMaybeFlush(targetPage, lsn);
    }

    public java.util.List<Row> scan() throws Exception {
        List<Row> allRows = new ArrayList<>();
        scan(allRows::add);
        return allRows;
    }

    /**
     * Streams rows together with their encoded row pointers.
     */
    public void scanWithPointers(BiConsumer<Long, Row> consumer) throws Exception {
        Set<Integer> visited = new HashSet<>();
        int pageId = table.getRootPageId();
        while (pageId != -1) {
            if (!visited.add(pageId)) {
                throw new IllegalStateException("Page chain contains a cycle at page " + pageId);
            }
            Page page = bufferPool.fetchPage(pageId);
            TablePage tablePage = new TablePage(page, table);
            int rowCount = tablePage.getRowCount();
            for (int slot = 0; slot < rowCount; slot++) {
                Row row = tablePage.getRow(slot);
                consumer.accept(IndexManager.encodePointer(pageId, slot), row);
            }
            pageId = tablePage.getNextPageId();
        }
    }

    public Row readRow(long rowPointer) throws Exception {
        int pageId = IndexManager.decodePageId(rowPointer);
        int slot = IndexManager.decodeSlot(rowPointer);
        Page page = bufferPool.fetchPage(pageId);
        TablePage tablePage = new TablePage(page, table);
        return tablePage.getRow(slot);
    }

    /**
     * Streams rows through a consumer without materializing the whole table in memory.
     */
    public void scan(Consumer<Row> consumer) throws Exception {
        Set<Integer> visited = new HashSet<>();
        int pageId = table.getRootPageId();
        while (pageId != -1) {
            if (!visited.add(pageId)) {
                throw new IllegalStateException("Page chain contains a cycle at page " + pageId);
            }
            Page page = bufferPool.fetchPage(pageId);
            TablePage tablePage = new TablePage(page, table);
            for (Row row : tablePage.getRows()) {
                consumer.accept(row);
            }
            pageId = tablePage.getNextPageId();
        }
    }

    /**
     * Updates rows matching the given predicate with the provided updater.
     * @param predicate a function to select rows to update
     * @param updater a function that modifies a row in place
     * @return number of rows updated
     */
    public synchronized int update(java.util.function.Predicate<Row> predicate, java.util.function.Consumer<Row> updater) throws Exception {
        int updated = 0;
        int pageId = table.getRootPageId();
        while (pageId != -1) {
            logPageIfNeeded(pageId);
            Page page = bufferPool.fetchPage(pageId);
            byte[] before = copyPageBytes(page);
            TablePage tablePage = new TablePage(page, table);
            java.util.List<Row> rows = tablePage.getRows();
            boolean dirty = false;
            for (Row row : rows) {
                if (predicate.test(row)) {
                    updater.accept(row);
                    dirty = true;
                    updated++;
                }
            }
            if (dirty) {
                tablePage.overwriteRows(rows);
                long lsn = logWalChange(pageId, 0, before, copyPageBytes(page));
                markDirtyAndMaybeFlush(page, lsn);
            }
            pageId = tablePage.getNextPageId();
        }
        return updated;
    }

    /**
     * Deletes rows matching the given predicate.
     * @param predicate a function to select rows to delete
     * @return number of rows deleted
     */
    public synchronized int delete(java.util.function.Predicate<Row> predicate) throws Exception {
        int deleted = 0;
        int pageId = table.getRootPageId();
        while (pageId != -1) {
            logPageIfNeeded(pageId);
            Page page = bufferPool.fetchPage(pageId);
            byte[] pageBefore = copyPageBytes(page);
            TablePage tablePage = new TablePage(page, table);
            java.util.List<Row> rows = tablePage.getRows();
            int beforeCount = rows.size();
            rows.removeIf(predicate);
            int afterCount = rows.size();
            if (afterCount < beforeCount) {
                tablePage.overwriteRows(rows);
                long lsn = logWalChange(pageId, 0, pageBefore, copyPageBytes(page));
                markDirtyAndMaybeFlush(page, lsn);
                deleted += (beforeCount - afterCount);
                // If page is now empty (not root), add to free list
                if (afterCount == 0 && pageId != table.getRootPageId()) {
                    freePages.add(pageId);
                    // Unlink from chain
                    unlinkPage(pageId);
                }
            }
            pageId = tablePage.getNextPageId();
        }
        tailPageId = findTailPageId();
        return deleted;
    }

    // Unlink a page from the chain (helper for delete)
    private void unlinkPage(int pageId) throws Exception {
        int prevId = table.getRootPageId();
        Page prevPage = bufferPool.fetchPage(prevId);
        TablePage prevTablePage = new TablePage(prevPage, table);
        while (prevTablePage.getNextPageId() != -1) {
            int nextId = prevTablePage.getNextPageId();
            if (nextId == pageId) {
                // Bypass the page
                byte[] before = copyPageBytes(prevPage);
                prevTablePage.setNextPageId(new TablePage(bufferPool.fetchPage(pageId), table).getNextPageId());
                long lsn = logWalChange(prevId, 0, before, copyPageBytes(prevPage));
                markDirtyAndMaybeFlush(prevPage, lsn);
                break;
            }
            prevId = nextId;
            prevPage = bufferPool.fetchPage(prevId);
            prevTablePage = new TablePage(prevPage, table);
        }
    }

    private int allocateNewPage() throws Exception {
        if (!freePages.isEmpty()) {
            int reusedPageId = freePages.iterator().next();
            freePages.remove(reusedPageId);
            Page page = bufferPool.fetchPage(reusedPageId);
            byte[] before = copyPageBytes(page);
            ByteBuffer buffer = ByteBuffer.wrap(page.getData());
            buffer.putInt(0, 0); // rowCount
            buffer.putInt(4, -1); // nextPageId
            long lsn = logWalChange(reusedPageId, 0, before, copyPageBytes(page));
            markDirtyAndMaybeFlush(page, lsn);
            return reusedPageId;
        }

        // Monotonic allocation avoids accidental page-id reuse.
        int newPageId = ++nextPageIdCounter;
        Page page = bufferPool.fetchPage(newPageId);
        byte[] before = copyPageBytes(page);
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.putInt(0, 0); // rowCount
        buffer.putInt(4, -1); // nextPageId
        long lsn = logWalChange(newPageId, 0, before, copyPageBytes(page));
        markDirtyAndMaybeFlush(page, lsn);
        return newPageId;
    }

    private byte[] copyPageBytes(Page page) {
        return java.util.Arrays.copyOf(page.getData(), page.getData().length);
    }

    private long logWalChange(int pageId, int offset, byte[] before, byte[] after) throws Exception {
        if (transactionManager == null || !transactionManager.hasActiveTx()) {
            return 0L;
        }
        // Use UPDATE-style records with before/after images so both REDO and UNDO are possible.
        return transactionManager.logUpdate(pageId, offset, before, after);
    }

    private void markDirtyAndMaybeFlush(Page page, long lsn) throws Exception {
        if (lsn > 0) {
            page.setPageLsn(lsn);
        }
        page.markDirty();
        if (transactionManager == null || !transactionManager.hasActiveTx()) {
            bufferPool.flushPage(page.getPageId());
        }
    }

    /**
     * Compacts the table by moving rows to fill pages and freeing up empty pages.
     * Returns the number of pages freed.
     */
    public synchronized int compact() throws Exception {
        List<Row> allRows = scan();
        // Reset all pages except root
        int pageId = table.getRootPageId();
        int freed = 0;
        int nextId = new TablePage(bufferPool.fetchPage(pageId), table).getNextPageId();
        while (nextId != -1) {
            freePages.add(nextId);
            unlinkPage(nextId);
            freed++;
            TablePage tp = new TablePage(bufferPool.fetchPage(nextId), table);
            nextId = tp.getNextPageId();
        }
        // Clear root page
        Page rootPage = bufferPool.fetchPage(table.getRootPageId());
        ByteBuffer buffer = ByteBuffer.wrap(rootPage.getData());
        byte[] beforeRoot = copyPageBytes(rootPage);
        buffer.putInt(0, 0);
        buffer.putInt(4, -1);
        long rootLsn = logWalChange(table.getRootPageId(), 0, beforeRoot, copyPageBytes(rootPage));
        markDirtyAndMaybeFlush(rootPage, rootLsn);
        // Re-insert all rows
        for (Row row : allRows) {
            insert(row);
        }
        tailPageId = findTailPageId();
        return freed;
    }

    private int resolveTailPageId() throws Exception {
        if (tailPageId < 0) {
            tailPageId = findTailPageId();
            return tailPageId;
        }

        Page tailPage = bufferPool.fetchPage(tailPageId);
        TablePage tablePage = new TablePage(tailPage, table);
        if (tablePage.getNextPageId() != -1) {
            tailPageId = findTailPageId();
        }
        return tailPageId;
    }

    private int findTailPageId() throws Exception {
        int pageId = table.getRootPageId();
        int lastSeen = pageId;
        Set<Integer> visited = new HashSet<>();
        while (pageId != -1) {
            if (!visited.add(pageId)) {
                throw new IllegalStateException("Page chain contains a cycle at page " + pageId);
            }
            lastSeen = pageId;
            Page page = bufferPool.fetchPage(pageId);
            TablePage tablePage = new TablePage(page, table);
            pageId = tablePage.getNextPageId();
        }
        return lastSeen;
    }

    private int findMaxPageId() throws Exception {
        // Traverse the page chain to find the highest page ID
        int maxPageId = table.getRootPageId();
        int pageId = table.getRootPageId();
        while (pageId != -1) {
            Page page = bufferPool.fetchPage(pageId);
            TablePage tablePage = new TablePage(page, table);
            int nextPageId = tablePage.getNextPageId();
            if (nextPageId > maxPageId) {
                maxPageId = nextPageId;
            }
            pageId = nextPageId;
        }
        return maxPageId;
    }
}
