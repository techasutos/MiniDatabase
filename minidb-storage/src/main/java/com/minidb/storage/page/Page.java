package com.minidb.storage.page;

/**
 * Represents a page in the storage system.
 * Each page has a fixed size and can be marked as dirty when modified.
 * The Page class provides methods to access page data and manage its dirty state.
 * This class is used by the BufferPoolManager to manage pages in memory and handle disk I/O efficiently.
 * The PAGE_SIZE constant defines the size of each page, which is typically 4096 bytes (4 KB).
 * The pageId is a unique identifier for the page, and the data array holds the actual content of the page.
 * The dirty flag indicates whether the page has been modified and needs to be written back to disk
 * before being evicted from the buffer pool.
 * This class is a fundamental building block for the storage engine, enabling efficient management of data pages in memory and on disk.
 * The BufferPoolManager will use instances of the Page class to keep track of which pages are currently in memory, which ones are dirty, and when to flush them back to disk.
 * Overall, the Page class is a crucial component of the storage system, providing a structured way to represent and manage data pages in memory and on disk.
 *
 */
public class Page {

    public static final int PAGE_SIZE = 4096;

    private final int pageId;
    private final byte[] data;
    private boolean dirty;
    private long pageLsn;

    public Page(int pageId) {
        this.pageId = pageId;
        this.data = new byte[PAGE_SIZE];
        this.dirty = false;
        this.pageLsn = 0L;
    }

    public int getPageId() {
        return pageId;
    }

    public byte[] getData() {
        return data;
    }

    public void markDirty() {
        this.dirty = true;
    }

    // Added for BufferPoolManager dirty flag reset
    public void clearDirty() {
        this.dirty = false;
    }

    public boolean isDirty() {
        return dirty;
    }

    public long getPageLsn() {
        return pageLsn;
    }

    public void setPageLsn(long pageLsn) {
        this.pageLsn = pageLsn;
    }
}