package com.minidb.executor.planner.logical;

import com.minidb.sql.ast.Expression;
/**
 * Represents a filter operation in the logical plan.
 * It applies a predicate to the input data and produces a filtered result.
 * The FilterNode is a unary operator that takes one input logical plan and a predicate expression.
 * Example usage:
 * <pre>
 * LogicalPlan scan = new TableScanNode("employees");
 * Expression predicate = new ComparisonExpression("age", ">", 30);
 * LogicalPlan filter = new FilterNode(scan, predicate);
 * </pre>
 * This node will filter the rows from the "employees" table where the "age" column is greater than 30.
 *
 * Author : Ashutosh Dang
 * Date : 03-05-2026
 */
public class FilterNode implements LogicalPlan {

    private final LogicalPlan input;
    private final Expression predicate;

    public FilterNode(LogicalPlan input, Expression predicate) {
        this.input = input;
        this.predicate = predicate;
    }

    public LogicalPlan getInput() {
        return input;
    }

    public Expression getPredicate() {
        return predicate;
    }
}