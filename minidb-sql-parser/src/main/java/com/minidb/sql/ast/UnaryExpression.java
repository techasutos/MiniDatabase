package com.minidb.sql.ast;

public class UnaryExpression implements Expression {

    private final String operator;
    private final Expression operand;

    public UnaryExpression(String operator, Expression operand) {
        this.operator = operator;
        this.operand = operand;
    }

    @Override
    public Object evaluate(RowContext ctx) {
        Object value = operand.evaluate(ctx);

        if ("NOT".equals(operator)) {
            if (!(value instanceof Boolean)) {
                throw new IllegalArgumentException("NOT expects boolean operand");
            }
            return !((Boolean) value);
        }

        throw new IllegalArgumentException("Unsupported unary operator: " + operator);
    }
}

