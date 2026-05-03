package com.minidb.storage;

import com.minidb.catalog.model.*;
import com.minidb.storage.engine.StorageEngine;
import com.minidb.storage.engine.TableStorage;
import com.minidb.storage.row.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MultiPageStorageTest {
    @Test
    void testInsertAndScanMultiPage() throws Exception {
        Table table = new Table(1, "users", List.of(
                new Column("id", DataType.INT),
                new Column("name", DataType.STRING)
        ));
        StorageEngine engine = new StorageEngine("test-multipage.db");
        TableStorage storage = new TableStorage(engine.getBufferPool(), table);
        // Insert enough rows to require multiple pages
        int rows = 200; // Should exceed single page
        for (int i = 0; i < rows; i++) {
            storage.insert(new Row(List.of(i, "user" + i)));
        }
        List<Row> all = storage.scan();
        assertEquals(rows, all.size());
        assertEquals(0, all.get(0).getValues().get(0));
        assertEquals("user0", all.get(0).getValues().get(1));
        assertEquals(rows - 1, all.get(rows - 1).getValues().get(0));
    }
}

