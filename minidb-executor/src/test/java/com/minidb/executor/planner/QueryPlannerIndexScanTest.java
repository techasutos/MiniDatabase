package com.minidb.executor.planner;

import com.minidb.catalog.CatalogManager;
import com.minidb.catalog.model.Column;
import com.minidb.catalog.model.DataType;
import com.minidb.catalog.model.Table;
import com.minidb.executor.planner.physical.IndexScanNode;
import com.minidb.executor.planner.physical.PlanNode;
import com.minidb.executor.planner.physical.ProjectNode;
import com.minidb.executor.planner.physical.FilterNode;
import com.minidb.executor.planner.physical.TableScanNode;
import com.minidb.sql.SQLParserService;
import com.minidb.sql.ast.SelectStatement;
import com.minidb.storage.engine.StorageEngine;
import com.minidb.storage.engine.TableStorage;
import com.minidb.storage.index.IndexManager;
import com.minidb.storage.row.Row;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueryPlannerIndexScanTest {

    @Test
    void usesIndexScanWhenEqualityPredicateMatchesIndexedColumn() throws Exception {
        CatalogManager catalog = new CatalogManager();
        catalog.createDatabase("testdb");
        catalog.createSchema("testdb", "public");
        catalog.createTable(
                "testdb",
                "public",
                "users",
                List.of(
                        new Column("id", DataType.INT),
                        new Column("name", DataType.STRING, 32)
                )
        );

        Path dataDir = Files.createTempDirectory("minidb-index-scan-");
        StorageEngine storageEngine = new StorageEngine(dataDir.resolve("minidb.data").toString());
        QueryPlanner planner = new QueryPlanner(catalog, storageEngine);
        SQLParserService parser = new SQLParserService();

        Table table = catalog.getDatabase("testdb").getSchema("public").getTable("users");
        TableStorage storage = new TableStorage(storageEngine.getBufferPool(), table);
        storage.insert(new Row(List.of(1, "Alice")));
        storage.insert(new Row(List.of(2, "Bob")));

        IndexManager indexManager = storageEngine.getIndexManager();
        indexManager.createIndex("testdb.public.users", "id");
        storage.scanWithPointers((pointer, row) -> indexManager.insertEntry(
                "testdb.public.users",
                "id",
                (Comparable<?>) row.getValues().get(0),
                pointer
        ));

        SelectStatement indexedSelect = (SelectStatement) parser.parse("SELECT * FROM testdb.public.users WHERE id = 2");
        PlanNode plan = planner.plan(indexedSelect);
        assertInstanceOf(ProjectNode.class, plan);
        assertInstanceOf(IndexScanNode.class, getChild(plan));

        List<Row> rows = plan.execute();
        assertEquals(1, rows.size());
        assertEquals(List.of(2, "Bob"), rows.get(0).getValues());
    }

    @Test
    void fallsBackToTableScanWhenNoIndexExists() throws Exception {
        CatalogManager catalog = new CatalogManager();
        catalog.createDatabase("testdb");
        catalog.createSchema("testdb", "public");
        catalog.createTable(
                "testdb",
                "public",
                "users",
                List.of(
                        new Column("id", DataType.INT),
                        new Column("name", DataType.STRING, 32)
                )
        );

        Path dataDir = Files.createTempDirectory("minidb-index-fallback-");
        StorageEngine storageEngine = new StorageEngine(dataDir.resolve("minidb.data").toString());
        QueryPlanner planner = new QueryPlanner(catalog, storageEngine);
        SQLParserService parser = new SQLParserService();

        Table table = catalog.getDatabase("testdb").getSchema("public").getTable("users");
        TableStorage storage = new TableStorage(storageEngine.getBufferPool(), table);
        storage.insert(new Row(List.of(1, "Alice")));

        SelectStatement fallbackSelect = (SelectStatement) parser.parse("SELECT * FROM testdb.public.users WHERE name = 'Alice'");
        PlanNode plan = planner.plan(fallbackSelect);
        assertInstanceOf(ProjectNode.class, plan);
        Object child = getChild(plan);
        assertInstanceOf(FilterNode.class, child);
        assertInstanceOf(TableScanNode.class, getChild(child));
    }

    private Object getChild(Object node) throws Exception {
        Field field = node.getClass().getDeclaredField("child");
        field.setAccessible(true);
        return field.get(node);
    }
}

