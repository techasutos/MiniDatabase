package com.minidb.executor.planner.physical;

import com.minidb.executor.planner.physical.PlanNode;
import com.minidb.sql.ast.*;
import com.minidb.catalog.model.Table;
import com.minidb.storage.row.Row;

import java.util.*;

public class ProjectNode implements PlanNode {

    private final PlanNode child;
    private final List<SelectItem> items;
    private final Table table;

    public ProjectNode(PlanNode child, List<SelectItem> items, Table table) {
        this.child = child;
        this.items = items;
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

            List<Object> values = new ArrayList<>();

            for (SelectItem item : items) {

                if (item.getExpression() instanceof ColumnExpression col &&
                        col.getColumn().equals("*")) {

                    values.addAll(r.getValues());

                } else {
                    values.add(item.getExpression().evaluate(ctx));
                }
            }

            output.add(new Row(values));
        }

        return output;
    }
}