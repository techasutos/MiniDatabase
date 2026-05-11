package com.minidb.executor.planner.physical;

import com.minidb.catalog.model.Table;
import com.minidb.sql.ast.Expression;
import com.minidb.sql.ast.RowContext;
import com.minidb.storage.row.Row;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Simple INNER JOIN physical operator using nested loops.
 */
public class NestedLoopJoinNode implements PlanNode {

    private final PlanNode left;
    private final PlanNode right;
    private final Table leftTable;
    private final Table rightTable;
    private final Expression condition;

    public NestedLoopJoinNode(PlanNode left, PlanNode right, Table leftTable, Table rightTable, Expression condition) {
        this.left = left;
        this.right = right;
        this.leftTable = leftTable;
        this.rightTable = rightTable;
        this.condition = condition;
    }

    @Override
    public List<Row> execute() throws Exception {
        List<Row> rows = new ArrayList<>();
        forEachRow(rows::add);
        return rows;
    }

    @Override
    public void forEachRow(Consumer<Row> consumer) throws Exception {
        List<Row> rightRows = right.execute();
        left.forEachRow(leftRow -> {
            for (Row rightRow : rightRows) {
                Map<String, Object> ctxMap = RowContextBuilder.build(leftTable, leftRow, rightTable, rightRow);
                Object result = condition == null ? Boolean.TRUE : condition.evaluate(new RowContext(ctxMap));
                if (result instanceof Boolean && (Boolean) result) {
                    List<Object> values = new ArrayList<>(leftRow.getValues().size() + rightRow.getValues().size());
                    values.addAll(leftRow.getValues());
                    values.addAll(rightRow.getValues());
                    consumer.accept(new Row(values));
                }
            }
        });
    }
}

