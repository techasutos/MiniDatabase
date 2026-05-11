package com.minidb.executor.planner;

import com.minidb.catalog.CatalogManager;
import com.minidb.catalog.model.Table;
import com.minidb.executor.planner.physical.*;
import com.minidb.sql.ast.*;
import com.minidb.storage.engine.StorageEngine;
import com.minidb.storage.engine.TableStorage;
import com.minidb.storage.index.IndexManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * QueryPlanner — converts SelectStatement AST → physical PlanNode tree.
 *
 * Pipeline (bottom to top):
 *  TableScanNode / IndexScanNode → [NestedLoopJoinNode]* → [FilterNode] → [AggregateNode | ProjectNode]
 *                                                          → [SortNode] → [LimitNode]
 */
public class QueryPlanner {

    private final CatalogManager catalog;
    private final StorageEngine storageEngine;

    public QueryPlanner(CatalogManager catalog, StorageEngine storageEngine) {
        this.catalog = catalog;
        this.storageEngine = storageEngine;
    }

    public PlanNode plan(SelectStatement stmt) {

        Table baseTable = resolveTable(stmt.getTable());
        TableStorage baseStorage = new TableStorage(storageEngine.getBufferPool(), baseTable);
        IndexManager indexManager = storageEngine.getIndexManager();

        Table currentTable = baseTable;

        // 1. Scan (prefer index for simple equality predicates)
        PlanNode root;
        Expression remainingWhere = stmt.getWhere();
        IndexPredicate indexPredicate = findIndexPredicate(stmt.getWhere());
        String indexedColumn = indexPredicate == null ? null : resolveIndexedColumnName(stmt.getTable(), indexPredicate.columnName(), indexManager);

        if (indexPredicate != null && indexedColumn != null && !stmt.hasJoins()) {
            root = new IndexScanNode(baseStorage, indexManager, stmt.getTable(), indexedColumn, indexPredicate.literalValue());
            remainingWhere = null;
        } else {
            root = new TableScanNode(baseStorage);
        }

        // 1b. INNER JOIN(s)
        if (stmt.hasJoins()) {
            for (JoinClause join : stmt.getJoins()) {
                Table rightTable = resolveTable(join.getTable());
                TableStorage rightStorage = new TableStorage(storageEngine.getBufferPool(), rightTable);
                root = new NestedLoopJoinNode(root, new TableScanNode(rightStorage), currentTable, rightTable, join.getCondition());
                currentTable = mergeTables(currentTable, rightTable);
            }
        }

        // 2. Filter (WHERE)
        if (remainingWhere != null) {
            root = new FilterNode(root, remainingWhere, currentTable);
        }

        // 3. Aggregate (GROUP BY / aggregate functions) or plain Project
        boolean hasAgg = stmt.hasGroupBy() || hasAggregateInItems(stmt.getItems());

        if (hasAgg) {
            root = new AggregateNode(
                    root,
                    stmt.getGroupBy(),
                    stmt.getItems(),
                    stmt.getHaving(),
                    currentTable
            );
        } else {
            root = new ProjectNode(root, stmt.getItems(), currentTable);
        }

        // 4. Sort (ORDER BY)
        if (stmt.hasOrderBy()) {
            List<String> projectedNames = buildProjectedColumnNames(
                    stmt.getItems(),
                    currentTable.getColumns().stream().map(c -> c.getName()).collect(Collectors.toList())
            );
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
        if (items.size() == 1 && items.get(0).getExpression() instanceof ColumnExpression col && "*".equals(col.getColumn())) {
            return origCols;
        }
        return items.stream().flatMap(item -> {
            if (item.getAlias() != null) return Stream.of(item.getAlias());
            Expression e = item.getExpression();
            if (e instanceof ColumnExpression col && "*".equals(col.getColumn())) return origCols.stream();
            if (e instanceof ColumnExpression col) return Stream.of(col.getColumn());
            return Stream.of(e.toString());
        }).collect(Collectors.toList());
    }

    private IndexPredicate findIndexPredicate(Expression where) {
        if (!(where instanceof BinaryExpression binary)) {
            return null;
        }
        if (!"=".equals(binary.getOp())) {
            return null;
        }
        if (binary.getLeft() instanceof ColumnExpression leftCol && binary.getRight() instanceof LiteralExpression rightLit) {
            Object value = rightLit.evaluate(null);
            return value instanceof Comparable<?> comparable ? new IndexPredicate(leftCol.getColumn(), comparable) : null;
        }
        if (binary.getRight() instanceof ColumnExpression rightCol && binary.getLeft() instanceof LiteralExpression leftLit) {
            Object value = leftLit.evaluate(null);
            return value instanceof Comparable<?> comparable ? new IndexPredicate(rightCol.getColumn(), comparable) : null;
        }
        return null;
    }

    private String resolveIndexedColumnName(String qualifiedTable, String columnName, IndexManager indexManager) {
        if (indexManager.hasIndex(qualifiedTable, columnName)) {
            return columnName;
        }
        int dot = columnName.lastIndexOf('.');
        if (dot >= 0 && dot < columnName.length() - 1) {
            String simple = columnName.substring(dot + 1);
            if (indexManager.hasIndex(qualifiedTable, simple)) {
                return simple;
            }
        }
        return null;
    }

    private record IndexPredicate(String columnName, Comparable<?> literalValue) {}

    private Table mergeTables(Table left, Table right) {
        List<com.minidb.catalog.model.Column> columns = new ArrayList<>();
        addPrefixedColumns(columns, left);
        addPrefixedColumns(columns, right);
        return new Table(-1, left.getName() + "__JOIN__" + right.getName(), columns);
    }

    private void addPrefixedColumns(List<com.minidb.catalog.model.Column> columns, Table table) {
        for (com.minidb.catalog.model.Column column : table.getColumns()) {
            String columnName = column.getName().contains(".")
                    ? column.getName()
                    : table.getName() + "." + column.getName();
            columns.add(new com.minidb.catalog.model.Column(
                    columnName,
                    column.getType(),
                    column.getStorageSize(),
                    column.isPrimaryKey(),
                    column.isNotNull(),
                    column.isUnique(),
                    column.getDefaultValue()));
        }
    }
}
