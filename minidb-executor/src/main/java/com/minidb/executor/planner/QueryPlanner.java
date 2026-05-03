package com.minidb.executor.planner;

import com.minidb.catalog.CatalogManager;
import com.minidb.catalog.model.Table;
import com.minidb.executor.planner.physical.PlanNode;
import com.minidb.executor.planner.physical.ProjectNode;
import com.minidb.executor.planner.physical.TableScanNode;
import com.minidb.sql.ast.*;
import com.minidb.storage.engine.StorageEngine;
import com.minidb.storage.engine.TableStorage;
import com.minidb.executor.planner.physical.FilterNode;
/**
 * QueryPlanner is responsible for converting a parsed SQL statement (AST) into an executable plan (PlanNode tree).
 * It uses the CatalogManager to resolve table metadata and the StorageEngine to access data storage.
 * The planning process involves:
 * 1. Resolving the target table from the FROM clause.
 * 2. Creating a TableScanNode to read data from the table.
 * 3. Adding a FilterNode if there is a WHERE clause to filter rows.
 * 4. Adding a ProjectNode to select the specified columns.
 * This is a simplified planner for demonstration purposes and does not include optimizations or support for joins, aggregations, etc.
 * Example usage:
 * CatalogManager catalog = ...; // Initialize catalog with metadata
 * StorageEngine storageEngine = ...; // Initialize storage engine
 * QueryPlanner planner = new QueryPlanner(catalog, storageEngine);
 * SelectStatement stmt = ...; // Parse SQL into AST
 * PlanNode plan = planner.plan(stmt);
 * Author : Ashutosh Dang
 * Date : 03-05-2026
 */
public class QueryPlanner {

    private final CatalogManager catalog;
    private final StorageEngine storageEngine;

    public QueryPlanner(CatalogManager catalog, StorageEngine storageEngine) {
        this.catalog = catalog;
        this.storageEngine = storageEngine;
    }

    public PlanNode plan(SelectStatement stmt) {

        Table table = resolveTable(stmt.getTable());

        TableStorage storage = new TableStorage(
                storageEngine.getBufferPool(),
                table
        );

        // Step 1: Scan
        PlanNode root = new TableScanNode(storage);

        // Step 2: Filter
        if (stmt.getWhere() != null) {
            root = new FilterNode(root, stmt.getWhere(), table);
        }

        // Step 3: Project
        root = new ProjectNode(root, stmt.getItems(), table);

        return root;
    }

    private Table resolveTable(String qualifiedName) {

        String[] parts = qualifiedName.split("\\.");

        return catalog
                .getDatabase(parts[0])
                .getSchema(parts[1])
                .getTable(parts[2]);
    }
}