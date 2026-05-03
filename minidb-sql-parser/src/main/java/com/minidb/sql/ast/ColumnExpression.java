package com.minidb.sql.ast;

/**
 * Represents a column reference in an expression.
 * For example, in the expression "age > 30", "age" would be represented as a ColumnExpression.
 */
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