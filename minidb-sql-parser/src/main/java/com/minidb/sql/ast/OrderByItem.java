package com.minidb.sql.ast;

/**
 * Represents one item in an ORDER BY clause.
 * e.g. "age DESC" or "name ASC"
 */
public class OrderByItem {

    private final Expression expression;
    private final boolean ascending;

    public OrderByItem(Expression expression, boolean ascending) {
        this.expression = expression;
        this.ascending  = ascending;
    }

    public Expression getExpression() { return expression; }
    public boolean    isAscending()   { return ascending; }
}

