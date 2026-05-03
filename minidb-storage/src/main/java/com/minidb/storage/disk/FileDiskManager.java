package com.minidb.storage.disk;

import java.io.RandomAccessFile;
import java.io.IOException;

/**
 * FileDiskManager is a simple implementation of DiskManager that uses a file to store pages.
 * Each page is of fixed size (PAGE_SIZE) and is accessed by its pageId, which determines its
 * offset in the file.
 * This implementation is thread-safe by synchronizing the read and write operations.
 * Note: In a production system, you would want to add error handling, caching, and possibly
 * support for multiple files or a more complex file structure.
 * For simplicity, this implementation assumes that the file is large enough to hold all pages and does not
 * handle file growth or fragmentation.
 * Also, it does not implement any caching or buffering, which would be essential for performance in a real system.
 * In a real implementation, you would also want to consider how to handle page eviction, dirty pages, and other
 * aspects of a full-fledged disk manager.
 * This is a basic starting point for a disk manager that can be expanded upon as needed.
 */
public class FileDiskManager implements DiskManager {

    private final RandomAccessFile file;
    private static final int PAGE_SIZE = 4096;

    public FileDiskManager(String filePath) throws IOException {
        this.file = new RandomAccessFile(filePath, "rw");
    }

    @Override
    public synchronized void writePage(int pageId, byte[] data) throws IOException {
        file.seek((long) pageId * PAGE_SIZE);
        file.write(data);
    }

    @Override
    public synchronized byte[] readPage(int pageId) throws IOException {
        byte[] data = new byte[PAGE_SIZE];
        file.seek((long) pageId * PAGE_SIZE);
        file.read(data);
        return data;
    }
}