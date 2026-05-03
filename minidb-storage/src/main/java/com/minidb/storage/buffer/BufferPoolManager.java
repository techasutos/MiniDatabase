package com.minidb.storage.buffer;

import com.minidb.storage.disk.DiskManager;
import com.minidb.storage.page.Page;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * BufferPoolManager is responsible for managing in-memory pages.
 * It handles fetching pages from disk, caching them, and flushing dirty pages back to disk.
 * This implementation uses a simple eviction strategy (first-in-first-out) for demonstration purposes.
 * In a production system, you would likely want to implement a more sophisticated eviction policy (e.g., LRU, LFU).
 *
 */
public class BufferPoolManager {

    private final DiskManager diskManager;
    private final int poolSize;

    private final Map<Integer, Page> pageTable;

    public BufferPoolManager(DiskManager diskManager, int poolSize) {
        this.diskManager = diskManager;
        this.poolSize = poolSize;
        this.pageTable = new HashMap<>();
    }

    public Page fetchPage(int pageId) throws IOException {

        // 1. Check cache
        if (pageTable.containsKey(pageId)) {
            return pageTable.get(pageId);
        }

        // 2. If full → evict (simple strategy)
        if (pageTable.size() >= poolSize) {
            evictPage();
        }

        // 3. Load from disk
        byte[] data = diskManager.readPage(pageId);
        Page page = new Page(pageId);
        System.arraycopy(data, 0, page.getData(), 0, data.length);

        pageTable.put(pageId, page);
        return page;
    }

    public void flushPage(int pageId) throws IOException {
        Page page = pageTable.get(pageId);
        if (page != null && page.isDirty()) {
            diskManager.writePage(pageId, page.getData());
            page.clearDirty(); // reset dirty flag after flush
        }
    }

    private void evictPage() throws IOException {
        Integer victim = pageTable.keySet().iterator().next();
        flushPage(victim);
        pageTable.remove(victim);
    }
}