package com.minidb.storage.engine;

import com.minidb.catalog.model.Table;
import com.minidb.storage.buffer.BufferPoolManager;
import com.minidb.storage.page.Page;
import com.minidb.storage.page.TablePage;
import com.minidb.storage.row.Row;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

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

    // Free page list for reuse (in-memory for now)
    private final java.util.Set<Integer> freePages = new java.util.HashSet<>();

    // Simple transaction state (for demonstration)
    private final ThreadLocal<Boolean> inTransaction = ThreadLocal.withInitial(() -> false);

    // Per-thread transaction log: pageId -> original page data
    private final ThreadLocal<java.util.Map<Integer, byte[]>> txLog = ThreadLocal.withInitial(java.util.HashMap::new);

    public TableStorage(BufferPoolManager bufferPool, Table table) {
        this.bufferPool = bufferPool;
        this.table = table;
        // Initialize root header once for new tables.
        try {
            Page rootPage = bufferPool.fetchPage(table.getRootPageId());
            ByteBuffer buffer = ByteBuffer.wrap(rootPage.getData());
            int rowCount = buffer.getInt(0);
            int nextPageId = buffer.getInt(4);

            if (rowCount == 0 && nextPageId == 0) {
                buffer.putInt(0, 0); // rowCount
                buffer.putInt(4, -1); // nextPageId
                rootPage.markDirty();
                bufferPool.flushPage(table.getRootPageId());
            }
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
            page.markDirty();
            bufferPool.flushPage(pageId);
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
        int pageId = table.getRootPageId();
        int lastPageId = pageId;
        Page page = bufferPool.fetchPage(pageId);
        TablePage tablePage = new TablePage(page, table);
        // Traverse to last page with space or allocate new
        while (!tablePage.hasSpace()) {
            int nextPageId = tablePage.getNextPageId();
            if (nextPageId == -1) {
                // Allocate new page
                int newPageId = allocateNewPage();
                TablePage newPage = new TablePage(bufferPool.fetchPage(newPageId), table);
                logPageIfNeeded(newPageId);
                tablePage.setNextPageId(newPageId);
                tablePage = newPage;
                lastPageId = newPageId;
                break;
            } else {
                lastPageId = nextPageId;
                page = bufferPool.fetchPage(nextPageId);
                tablePage = new TablePage(page, table);
            }
        }
        logPageIfNeeded(lastPageId);
        tablePage.insertRow(row);
        bufferPool.flushPage(lastPageId);
    }

    public java.util.List<Row> scan() throws Exception {
        List<Row> allRows = new ArrayList<>();
        int pageId = table.getRootPageId();
        int maxPages = 1000; // safeguard to prevent infinite loop
        int traversed = 0;
        while (pageId != -1) {
            if (++traversed > maxPages) {
                throw new IllegalStateException("Page chain too long or cyclic. Traversed: " + traversed);
            }
            Page page = bufferPool.fetchPage(pageId);
            TablePage tablePage = new TablePage(page, table);
            allRows.addAll(tablePage.getRows());
            pageId = tablePage.getNextPageId();
        }
        return allRows;
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
                page.markDirty();
                bufferPool.flushPage(pageId);
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
            TablePage tablePage = new TablePage(page, table);
            java.util.List<Row> rows = tablePage.getRows();
            int before = rows.size();
            rows.removeIf(predicate);
            int after = rows.size();
            if (after < before) {
                tablePage.overwriteRows(rows);
                page.markDirty();
                bufferPool.flushPage(pageId);
                deleted += (before - after);
                // If page is now empty (not root), add to free list
                if (after == 0 && pageId != table.getRootPageId()) {
                    freePages.add(pageId);
                    // Unlink from chain
                    unlinkPage(pageId);
                }
            }
            pageId = tablePage.getNextPageId();
        }
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
                prevTablePage.setNextPageId(new TablePage(bufferPool.fetchPage(pageId), table).getNextPageId());
                prevPage.markDirty();
                bufferPool.flushPage(prevId);
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
            ByteBuffer buffer = ByteBuffer.wrap(page.getData());
            buffer.putInt(0, 0); // rowCount
            buffer.putInt(4, -1); // nextPageId
            page.markDirty();
            bufferPool.flushPage(reusedPageId);
            return reusedPageId;
        }
        int newPageId = findMaxPageId() + 1;
        Page page = bufferPool.fetchPage(newPageId);
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.putInt(0, 0); // rowCount
        buffer.putInt(4, -1); // nextPageId
        page.markDirty();
        bufferPool.flushPage(newPageId);
        return newPageId;
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
        buffer.putInt(0, 0);
        buffer.putInt(4, -1);
        rootPage.markDirty();
        bufferPool.flushPage(table.getRootPageId());
        // Re-insert all rows
        for (Row row : allRows) {
            insert(row);
        }
        return freed;
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
