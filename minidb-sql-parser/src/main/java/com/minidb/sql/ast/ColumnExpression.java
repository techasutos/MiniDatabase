package com.minidb.sql.ast;

public class ColumnExpression implements Expression {

    private final String column;

    public ColumnExpression(String column) {
        this.column = column;
    }

    // 🔥 THIS WAS MISSING
    public String getColumn() {
        return column;
    }

    @Override
    public Object evaluate(RowContext ctx) {
        return ctx.get(column);
    }
}