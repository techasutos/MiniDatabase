package com.minidb.storage.engine;

import com.minidb.storage.buffer.BufferPoolManager;
import com.minidb.storage.disk.FileDiskManager;

/**
 * StorageEngine is the main entry point for the storage layer.
 * It initializes the BufferPoolManager
 * and provides access to it for higher layers (e.g., execution engine).
 * In a more complete implementation, StorageEngine would also manage transactions,
 * logging, and recovery.
 * For simplicity, this example focuses on the core components needed to read/write pages.
 * In a real implementation, you would also need to handle concurrency control,
 * transaction management, and more complex page formats.
 * This class can be extended in the future to support additional features like:
 * - Transaction management
 * - Logging and recovery
 * - Multiple buffer pool instances for different workloads
 * - Configuration options for buffer pool size, page size, etc.
 */
public class StorageEngine {

    private final BufferPoolManager bufferPool;

    public StorageEngine(String dbFilePath) throws Exception {
        FileDiskManager diskManager = new FileDiskManager(dbFilePath);
        this.bufferPool = new BufferPoolManager(diskManager, 10);
    }

    public BufferPoolManager getBufferPool() {
        return bufferPool;
    }
}