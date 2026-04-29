package com.minidb.sql.ast;

public class SelectItem {

    private final Expression expression;
    private final String alias;

    public SelectItem(Expression expression, String alias) {
        this.expression = expression;
        this.alias = alias;
    }

    public Expression getExpression() {
        return expression;
    }

    public String getAlias() {
        return alias;
    }
}