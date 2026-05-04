package com.minidb.sql.ast;

public class UnaryExpression implements Expression {

    private final String operator;
    private final Expression operand;

    public UnaryExpression(String operator, Expression operand) {
        this.operator = operator;
        this.operand = operand;
    }

    public String     getOperator() { return operator; }
    public Expression getOperand()  { return operand; }

    @Override
    public Object evaluate(RowContext ctx) {
        switch (operator) {
            case "NOT" -> {
                Object value = operand.evaluate(ctx);
                if (!(value instanceof Boolean))
                    throw new IllegalArgumentException("NOT expects boolean operand, got: " + value);
                return !((Boolean) value);
            }
            case "IS_NULL"     -> { return operand.evaluate(ctx) == null; }
            case "IS_NOT_NULL" -> { return operand.evaluate(ctx) != null; }
            default -> throw new IllegalArgumentException("Unsupported unary operator: " + operator);
        }
    }
}

