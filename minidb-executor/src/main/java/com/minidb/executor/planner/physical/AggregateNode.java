package com.minidb.executor.planner.physical;

import com.minidb.sql.ast.*;
import com.minidb.storage.row.Row;

import java.util.*;

/**
 * Aggregate + GROUP BY node.
 *
 * Supports: COUNT(*), COUNT(col), SUM(col), AVG(col), MIN(col), MAX(col)
 * With optional HAVING filter.
 *
 * Groups rows by a set of groupBy expressions, then applies aggregate functions
 * over each group. Outputs one row per group.
 */
public class AggregateNode implements PlanNode {

    private final PlanNode          child;
    private final List<Expression>  groupByExprs;
    private final List<SelectItem>  selectItems;
    private final Expression        having;
    private final List<String>      inputColumnNames;

    public AggregateNode(PlanNode child,
                         List<Expression>  groupByExprs,
                         List<SelectItem>  selectItems,
                         Expression        having,
                         List<String>      inputColumnNames) {
        this.child            = child;
        this.groupByExprs     = groupByExprs;
        this.selectItems      = selectItems;
        this.having           = having;
        this.inputColumnNames = inputColumnNames;
    }

    @Override
    public List<Row> execute() throws Exception {

        List<Row> input = child.execute();

        // ── Group rows ──────────────────────────────────────────────────────
        // Key: tuple of group-by values; Value: list of rows in that group
        LinkedHashMap<List<Object>, List<Row>> groups = new LinkedHashMap<>();

        for (Row row : input) {
            List<Object> key = buildGroupKey(row);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        // ── Aggregate each group ────────────────────────────────────────────
        List<Row> result = new ArrayList<>();

        for (Map.Entry<List<Object>, List<Row>> entry : groups.entrySet()) {
            List<Object> groupKey = entry.getKey();
            List<Row>    groupRows = entry.getValue();

            List<Object> outputValues = new ArrayList<>();

            for (SelectItem item : selectItems) {
                Expression expr = item.getExpression();

                if (expr instanceof FunctionCallExpression fn) {
                    outputValues.add(aggregate(fn, groupRows));
                } else if (expr instanceof ColumnExpression col && col.getColumn().equals("*")) {
                    // SELECT * with groupBy — emit group key columns
                    outputValues.addAll(groupKey);
                } else {
                    // plain column or scalar expression — take value from first row of group
                    outputValues.add(evalRow(groupRows.get(0), expr));
                }
            }

            Row outRow = new Row(outputValues);

            // ── HAVING filter ─────────────────────────────────────────────────
            if (having != null) {
                Map<String, Object> havingCtx = buildOutputContext(outRow);
                Object havingResult = having.evaluate(new RowContext(havingCtx));
                if (!(havingResult instanceof Boolean && (Boolean) havingResult)) continue;
            }

            result.add(outRow);
        }

        return result;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private List<Object> buildGroupKey(Row row) {
        if (groupByExprs.isEmpty()) {
            return List.of(); // single group (no GROUP BY clause)
        }
        List<Object> key = new ArrayList<>();
        for (Expression expr : groupByExprs) {
            key.add(evalRow(row, expr));
        }
        return key;
    }

    private Object aggregate(FunctionCallExpression fn, List<Row> groupRows) {
        return switch (fn.getFunction()) {
            case COUNT -> {
                if (fn.getArgument() == null) {
                    yield (long) groupRows.size();           // COUNT(*)
                }
                yield groupRows.stream()
                        .filter(r -> evalRow(r, fn.getArgument()) != null)
                        .count();
            }
            case SUM -> {
                double sum = groupRows.stream()
                        .mapToDouble(r -> {
                            Object v = evalRow(r, fn.getArgument());
                            return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
                        }).sum();
                yield sum;
            }
            case AVG -> {
                OptionalDouble avg = groupRows.stream()
                        .mapToDouble(r -> {
                            Object v = evalRow(r, fn.getArgument());
                            return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
                        }).average();
                yield avg.orElse(0.0);
            }
            case MIN -> groupRows.stream()
                    .map(r -> evalRow(r, fn.getArgument()))
                    .filter(Objects::nonNull)
                    .min(AggregateNode::compareValues)
                    .orElse(null);
            case MAX -> groupRows.stream()
                    .map(r -> evalRow(r, fn.getArgument()))
                    .filter(Objects::nonNull)
                    .max(AggregateNode::compareValues)
                    .orElse(null);
        };
    }

    private Object evalRow(Row row, Expression expr) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < inputColumnNames.size(); i++) {
            map.put(inputColumnNames.get(i), row.getValues().get(i));
        }
        return expr.evaluate(new RowContext(map));
    }

    private Map<String, Object> buildOutputContext(Row row) {
        Map<String, Object> ctx = new HashMap<>();
        for (int i = 0; i < selectItems.size(); i++) {
            SelectItem item = selectItems.get(i);
            String key = item.getAlias() != null ? item.getAlias()
                    : item.getExpression().toString();
            ctx.put(key, row.getValues().get(i));
        }
        return ctx;
    }

    @SuppressWarnings("unchecked")
    private static int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return  1;
        if (a instanceof Number && b instanceof Number)
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        if (a instanceof Comparable)
            return ((Comparable<Object>) a).compareTo(b);
        return 0;
    }
}

