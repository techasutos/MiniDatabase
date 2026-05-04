package com.minidb.executor.planner;

import com.minidb.catalog.CatalogManager;
import com.minidb.catalog.model.Table;
import com.minidb.executor.planner.physical.*;
import com.minidb.sql.ast.*;
import com.minidb.storage.engine.StorageEngine;
import com.minidb.storage.engine.TableStorage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * QueryPlanner — converts SelectStatement AST → physical PlanNode tree.
 *
 * Pipeline (bottom to top):
 *  TableScanNode → [FilterNode] → [AggregateNode | ProjectNode]
 *                             → [SortNode] → [LimitNode]
 */
public class QueryPlanner {

    private final CatalogManager catalog;
    private final StorageEngine  storageEngine;

    public QueryPlanner(CatalogManager catalog, StorageEngine storageEngine) {
        this.catalog       = catalog;
        this.storageEngine = storageEngine;
    }

    public PlanNode plan(SelectStatement stmt) {

        Table        table   = resolveTable(stmt.getTable());
        TableStorage storage = new TableStorage(storageEngine.getBufferPool(), table);

        List<String> colNames = table.getColumns().stream()
                .map(c -> c.getName())
                .collect(Collectors.toList());

        // 1. Scan
        PlanNode root = new TableScanNode(storage);

        // 2. Filter (WHERE)
        if (stmt.getWhere() != null) {
            root = new FilterNode(root, stmt.getWhere(), table);
        }

        // 3. Aggregate (GROUP BY / aggregate functions) or plain Project
        boolean hasAgg = stmt.hasGroupBy() || hasAggregateInItems(stmt.getItems());

        if (hasAgg) {
            root = new AggregateNode(
                    root,
                    stmt.getGroupBy(),
                    stmt.getItems(),
                    stmt.getHaving(),
                    colNames
            );
        } else {
            root = new ProjectNode(root, stmt.getItems(), table);
        }

        // 4. Sort (ORDER BY)
        if (stmt.hasOrderBy()) {
            // After projection the column names may change — use select-item aliases
            List<String> projectedNames = buildProjectedColumnNames(stmt.getItems(), colNames);
            root = new SortNode(root, stmt.getOrderBy(), projectedNames);
        }

        // 5. Limit / Offset
        if (stmt.hasLimit()) {
            root = new LimitNode(root, stmt.getLimit(), stmt.getOffset());
        }

        return root;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Table resolveTable(String qualifiedName) {
        String[] parts = qualifiedName.split("\\.");
        return catalog.getDatabase(parts[0]).getSchema(parts[1]).getTable(parts[2]);
    }

    private boolean hasAggregateInItems(List<SelectItem> items) {
        for (SelectItem item : items) {
            if (item.getExpression() instanceof FunctionCallExpression) return true;
        }
        return false;
    }

    private List<String> buildProjectedColumnNames(List<SelectItem> items, List<String> origCols) {
        return items.stream().map(item -> {
            if (item.getAlias() != null) return item.getAlias();
            Expression e = item.getExpression();
            if (e instanceof ColumnExpression col) return col.getColumn();
            return e.toString();
        }).collect(Collectors.toList());
    }
}