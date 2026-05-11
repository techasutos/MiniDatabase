package com.minidb.executor.planner.physical;

import com.minidb.sql.ast.*;
import com.minidb.catalog.model.Table;
import com.minidb.storage.row.Row;

import java.util.*;
import java.util.function.Consumer;
/**
 * ProjectNode is responsible for evaluating the SELECT items and producing the final output rows.
 * It takes the output from its child node (which could be a ScanNode, FilterNode, etc.) and applies
 * the projection logic based on the SELECT items specified in the query.
 * For example, if the query is "SELECT name, age FROM users",
 * the ProjectNode will evaluate the expressions for "name" and "age"
 * for each input row and produce output rows containing only those fields.
 *
 * The ProjectNode also handles the special case of "SELECT *",
 * where it simply passes through all columns from the input rows without modification.
 * The ProjectNode uses a RowContext to evaluate expressions in the context of the current row,
 * allowing it to access column values and perform any necessary computations.
 * Overall, the ProjectNode is a crucial part of the execution plan that transforms
 * the intermediate results from its child node into the final result set that matches the SELECT clause of the query.
 * Author: Ashutosh Dang
 * Date:03-05-2026
 */
public class ProjectNode implements PlanNode {

    private final PlanNode child;
    private final List<SelectItem> items;
    private final Table table;

    public ProjectNode(PlanNode child, List<SelectItem> items, Table table) {
        this.child = child;
        this.items = items;
        this.table = table;
    }
    /**
     * Executes the projection logic by iterating over the input rows from the child node,
     * evaluating the SELECT items for each row, and producing the output rows accordingly.
     * It handles both regular column projections and the special case of "SELECT *".
     *
     * @return A list of output rows that match the projection specified in the SELECT clause.
     * @throws Exception If any error occurs during expression evaluation or row processing.
     */
    @Override
    public List<Row> execute() throws Exception {
        List<Row> output = new ArrayList<>();
        forEachRow(output::add);
        return output;
    }

    @Override
    public void forEachRow(Consumer<Row> consumer) throws Exception {
        child.forEachRow(r -> {
            RowContext ctx = new RowContext(RowContextBuilder.build(table, r));
            List<Object> values = new ArrayList<>();

            for (SelectItem item : items) {
                if (item.getExpression() instanceof ColumnExpression col &&
                        col.getColumn().equals("*")) {
                    values.addAll(r.getValues());
                } else {
                    values.add(item.getExpression().evaluate(ctx));
                }
            }

            consumer.accept(new Row(values));
        });
    }
}