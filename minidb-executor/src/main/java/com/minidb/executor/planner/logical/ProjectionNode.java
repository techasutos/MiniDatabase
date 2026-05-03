package com.minidb.executor.planner.logical;

import com.minidb.sql.ast.SelectItem;

import java.util.List;

/**
 * ProjectionNode represents the projection operation in a logical query plan.
 * It takes an input logical plan and a list of select items (columns or expressions)
 * to be projected in the output.
 * This node is responsible for transforming the input data according to the specified select items,
 * which may include column references, expressions, or even aggregate functions.
 * The ProjectionNode is a crucial part of the logical plan as it defines what data
 * will be returned to the user after executing the query.
 * The ProjectionNode does not perform any actual data retrieval or computation;
 * it simply defines the structure of the output based on the input plan and the select items.
 * The execution engine will later use this node to determine how to fetch and compute the required data during query execution.
 * The ProjectionNode can be used in various scenarios, such as:
 * - Simple SELECT queries where specific columns are selected from a table.
 * - Complex queries involving expressions, functions, or aggregations in the SELECT clause.
 * - Queries with JOINs where the projection defines which columns from the joined tables are included in
 * the final output.
 * Overall, the ProjectionNode is a fundamental component in the logical query plan
 * that defines the shape of the output data based on the input plan and the specified select items.
 * Author: Ashutosh Dang
 * Date: 03-05-2026
 */
public class ProjectionNode implements LogicalPlan {

    private final LogicalPlan input;
    private final List<SelectItem> items;

    public ProjectionNode(LogicalPlan input, List<SelectItem> items) {
        this.input = input;
        this.items = items;
    }

    public LogicalPlan getInput() {
        return input;
    }

    public List<SelectItem> getItems() {
        return items;
    }
}