package com.minidb.storage.buffer;

import com.minidb.storage.disk.DiskManager;
import com.minidb.storage.page.Page;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LRU Buffer Pool Manager.
 *
 * Uses an access-ordered LinkedHashMap as the LRU cache.
 * Pages with a non-zero pin count are not eligible for eviction.
 * Thread-safe via synchronized methods.
 *
 * Design mirrors PostgreSQL's shared_buffers concept:
 *   - fetchPage → pin (increment ref count)
 *   - unpinPage → unpin (decrement ref count, eligible for eviction)
 */
public class BufferPoolManager {

    private final DiskManager diskManager;
    private final int poolSize;

    /** LRU map: access-ordered → eldest = least recently used */
    private final LinkedHashMap<Integer, Page> lruCache;

    /** Pin counts per page to prevent eviction of in-use pages */
    private final Map<Integer, Integer> pinCounts = new java.util.HashMap<>();

    // ── Stats ──────────────────────────────────────────────────────────────
    private final AtomicLong cacheHits   = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong evictions   = new AtomicLong();

    public BufferPoolManager(DiskManager diskManager, int poolSize) {
        this.diskManager = diskManager;
        this.poolSize    = poolSize;
        // access-order = true → get() moves entry to tail (most-recently-used position)
        this.lruCache = new LinkedHashMap<>(poolSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Page> eldest) {
                // we manage eviction manually; disable auto-eviction
                return false;
            }
        };
    }

    /**
     * Fetch a page into the buffer pool (or return cached copy).
     *
     * NOTE: In current engine flow pages are not explicitly pinned/unpinned by callers,
     * so fetch does not auto-pin to avoid all pages becoming unevictable.
     */
    public synchronized Page fetchPage(int pageId) throws IOException {

        if (lruCache.containsKey(pageId)) {
            cacheHits.incrementAndGet();
            return lruCache.get(pageId); // access-order map updates LRU position on get
        }

        cacheMisses.incrementAndGet();

        if (lruCache.size() >= poolSize) {
            evictOne();
        }

        byte[] data = diskManager.readPage(pageId);
        Page page   = new Page(pageId);
        System.arraycopy(data, 0, page.getData(), 0, data.length);

        lruCache.put(pageId, page);
        pinCounts.putIfAbsent(pageId, 0);
        return page;
    }

    /** Explicitly pin a page when a caller needs eviction protection. */
    public synchronized void pinPage(int pageId) {
        pinCounts.merge(pageId, 1, Integer::sum);
    }

    /**
     * Decrement pin count. Page becomes evictable when count reaches 0.
     */
    public synchronized void unpinPage(int pageId) {
        pinCounts.computeIfPresent(pageId, (k, v) -> v > 1 ? v - 1 : 0);
    }

    /**
     * Write a dirty page back to disk and clear the dirty flag.
     */
    public synchronized void flushPage(int pageId) throws IOException {
        Page page = lruCache.get(pageId);
        if (page != null && page.isDirty()) {
            diskManager.writePage(pageId, page.getData());
            page.clearDirty();
        }
    }

    /**
     * Flush all dirty pages — called on shutdown or checkpoint.
     */
    public synchronized void flushAll() throws IOException {
        for (int pageId : lruCache.keySet()) {
            flushPage(pageId);
        }
    }

    /**
     * Remove a page from the buffer pool (force-evict, used during DROP TABLE).
     */
    public synchronized void evictPage(int pageId) throws IOException {
        flushPage(pageId);
        lruCache.remove(pageId);
        pinCounts.remove(pageId);
    }

    // ── Stats ──────────────────────────────────────────────────────────────

    public long getCacheHits()   { return cacheHits.get(); }
    public long getCacheMisses() { return cacheMisses.get(); }
    public long getEvictions()   { return evictions.get(); }
    public int  getPoolSize()    { return poolSize; }
    public int  getUsedFrames()  { return lruCache.size(); }

    // ── Internal ───────────────────────────────────────────────────────────

    /**
     * Evict the least-recently-used unpinned page.
     * Iterates from the head (LRU side) of the access-ordered LinkedHashMap.
     */
    private void evictOne() throws IOException {
        for (Map.Entry<Integer, Page> entry : lruCache.entrySet()) {
            int pageId  = entry.getKey();
            int pinCount = pinCounts.getOrDefault(pageId, 0);
            if (pinCount == 0) {
                flushPage(pageId);
                lruCache.remove(pageId);
                pinCounts.remove(pageId);
                evictions.incrementAndGet();
                return;
            }
        }
        throw new IOException("Buffer pool full and all pages are pinned — cannot evict");
    }
}