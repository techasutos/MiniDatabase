package com.minidb.sql.ast;

import java.util.List;

public class FunctionCallExpression implements Expression {

    public enum AggregateFunction { COUNT, SUM, MIN, MAX, AVG }

    private final AggregateFunction function;
    private final Expression argument; // null means COUNT(*)

    public FunctionCallExpression(AggregateFunction function, Expression argument) {
        this.function = function;
        this.argument = argument;
    }

    public AggregateFunction getFunction() { return function; }
    public Expression getArgument() { return argument; }

    @Override
    public Object evaluate(RowContext ctx) {
        // Aggregate functions can't be evaluated on a single row;
        // they are handled at the aggregation plan node level.
        throw new UnsupportedOperationException(
            "Aggregate function " + function + " must be evaluated by an AggregateNode, not per-row"
        );
    }
}

