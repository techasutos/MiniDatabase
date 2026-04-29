package com.minidb.sql.ast;

public class LiteralExpression implements Expression {

    private final Object value;

    public LiteralExpression(Object value) {
        this.value = value;
    }

    public Object evaluate(RowContext ctx) {
        return value;
    }
}