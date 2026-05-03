package com.minidb.executor.planner.physical;

import com.minidb.sql.ast.Expression;
import com.minidb.sql.ast.RowContext;
import com.minidb.storage.row.Row;
import com.minidb.catalog.model.Table;

import java.util.*;
/**
 * FilterNode applies a boolean condition to each row produced by its child node.
 * Only rows that satisfy the condition are passed through to the output.
 * This is a fundamental operator in query execution, enabling the implementation of WHERE clauses
 * and other filtering logic.
 * The FilterNode evaluates the condition for each row in the input,
 * using a RowContext to provide access to column values.
 * If the condition evaluates to true, the row is included in the output; otherwise, it is discarded.
 * This node is essential for optimizing query performance by reducing
 * the number of rows processed in subsequent operations, such as joins or aggregations.
 *Author: Ashutosh Dang
 * Date: 03-05-2026
 */
public class FilterNode implements PlanNode {

    private final PlanNode child;
    private final Expression condition;
    private final Table table;

    public FilterNode(PlanNode child, Expression condition, Table table) {
        this.child = child;
        this.condition = condition;
        this.table = table;
    }
    /**
     * Executes the filter operation by evaluating the condition on each row produced by the child node.
     * It constructs a RowContext for each row to evaluate the condition expression.
     * Only rows that satisfy the condition (i.e., where the condition evaluates to true) are included in the output list.
     *
     * @return A list of rows that satisfy the filter condition.
     * @throws Exception If there is an error during execution or evaluation of the condition.
     */
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