package com.minidb.executor.planner.physical;

import com.minidb.sql.ast.Expression;
import com.minidb.sql.ast.OrderByItem;
import com.minidb.storage.row.Row;

import java.util.*;

/**
 * Sorts the output of its child according to an ORDER BY clause.
 * Implements a full in-memory sort (for now).
 */
public class SortNode implements PlanNode {

    private final PlanNode child;
    private final List<OrderByItem> orderBy;
    private final List<String> columnNames;

    public SortNode(PlanNode child, List<OrderByItem> orderBy, List<String> columnNames) {
        this.child       = child;
        this.orderBy     = orderBy;
        this.columnNames = columnNames;
    }

    @Override
    public List<Row> execute() throws Exception {
        List<Row> rows = new ArrayList<>(child.execute());

        rows.sort((a, b) -> {
            for (OrderByItem item : orderBy) {
                Object va = evalForRow(a, item.getExpression());
                Object vb = evalForRow(b, item.getExpression());

                int cmp = compareValues(va, vb);
                if (cmp != 0) return item.isAscending() ? cmp : -cmp;
            }
            return 0;
        });

        return rows;
    }

    @SuppressWarnings("unchecked")
    private int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return  1;
        if (a instanceof Number && b instanceof Number)
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        if (a instanceof Comparable)
            return ((Comparable<Object>) a).compareTo(b);
        return 0;
    }

    private Object evalForRow(Row row, Expression expr) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < columnNames.size(); i++) {
            String columnName = columnNames.get(i);
            map.put(columnName, row.getValues().get(i));
            int dot = columnName.lastIndexOf('.');
            if (dot >= 0 && dot < columnName.length() - 1) {
                map.putIfAbsent(columnName.substring(dot + 1), row.getValues().get(i));
            }
        }
        return expr.evaluate(new com.minidb.sql.ast.RowContext(map));
    }
}

