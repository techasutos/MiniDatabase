package com.minidb.storage;

import com.minidb.storage.buffer.BufferPoolManager;
import com.minidb.storage.disk.FileDiskManager;
import com.minidb.storage.page.Page;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BufferPoolWalGateTest {

    @Test
    void autoFlushesWalBeforeFlushingDirtyPageWhenFlusherProvided() throws Exception {
        Path dbFile = Files.createTempFile("minidb-buffer-walgate-", ".db");

        AtomicLong flushedLsn = new AtomicLong(0L);
        BufferPoolManager bufferPool = new BufferPoolManager(
                new FileDiskManager(dbFile.toString()),
                4,
                flushedLsn::get,
                flushedLsn::set
        );

        Page page = bufferPool.fetchPage(1);
        page.getData()[0] = 42;
        page.setPageLsn(10L);
        page.markDirty();

        bufferPool.flushPage(1);
        assertEquals(10L, flushedLsn.get());
        assertFalse(page.isDirty());
    }

    @Test
    void blocksFlushWhenPageLsnIsAheadOfWalFlushedLsnWithoutFlusher() throws Exception {
        Path dbFile = Files.createTempFile("minidb-buffer-walgate-fail-", ".db");

        AtomicLong flushedLsn = new AtomicLong(5L);
        BufferPoolManager bufferPool = new BufferPoolManager(
                new FileDiskManager(dbFile.toString()),
                4,
                flushedLsn::get
        );

        Page page = bufferPool.fetchPage(2);
        page.getData()[0] = 7;
        page.setPageLsn(10L);
        page.markDirty();

        assertThrows(IOException.class, () -> bufferPool.flushPage(2));
    }
}

