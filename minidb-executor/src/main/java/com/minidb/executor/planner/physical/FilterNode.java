package com.minidb.executor.planner.physical;

import com.minidb.sql.ast.Expression;
import com.minidb.sql.ast.RowContext;
import com.minidb.storage.row.Row;
import com.minidb.catalog.model.Table;

import java.util.*;

public class FilterNode implements PlanNode {

    private final PlanNode child;
    private final Expression condition;
    private final Table table;

    public FilterNode(PlanNode child, Expression condition, Table table) {
        this.child = child;
        this.condition = condition;
        this.table = table;
    }

    @Override
    public List<Row> execute() throws Exception {

        List<Row> input = child.execute();
        List<Row> output = new ArrayList<>();

        for (Row r : input) {

            Map<String, Object> map = new HashMap<>();

            for (int i = 0; i < table.getColumns().size(); i++) {
                map.put(table.getColumns().get(i).getName(), r.getValues().get(i));
            }

            RowContext ctx = new RowContext(map);

            Object result = condition.evaluate(ctx);

            if (result instanceof Boolean && (Boolean) result) {
                output.add(r);
            }
        }

        return output;
    }
}