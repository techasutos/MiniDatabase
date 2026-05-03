/*
package com.minidb.storage;

import com.minidb.storage.engine.StorageEngine;
import com.minidb.storage.page.Page;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StorageTest {

    @Test
    void testReadWritePage() throws Exception {
        StorageEngine engine = new StorageEngine("test.db");

        Page page = engine.getBufferPool().fetchPage(1);
        page.getData()[0] = 42;
        page.markDirty();

        engine.getBufferPool().flushPage(1);

        Page loaded = engine.getBufferPool().fetchPage(1);
        assertEquals(42, loaded.getData()[0]);
    }
}*/
