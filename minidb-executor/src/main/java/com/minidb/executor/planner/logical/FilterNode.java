package com.minidb.executor.planner.logical;

import com.minidb.sql.ast.Expression;

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