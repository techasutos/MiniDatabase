package com.minidb.sql.ast;

/**
 * Represents a binary expression in the SQL AST, such as "a = b" or "x > y".
 * This class evaluates the left and right expressions and applies the specified operator.
 * Supported operators include: "=", "!=", ">", "<", "+", and "-".
 * Note: For simplicity, this implementation assumes that the left and
 * right expressions evaluate to integers for comparison and arithmetic operations.
 */
public class BinaryExpression implements Expression {

    private final Expression left;
    private final Expression right;
    private final String op;

    public BinaryExpression(Expression left, Expression right, String op) {
        this.left = left;
        this.right = right;
        this.op = op;
    }

    @Override
    public Object evaluate(RowContext ctx) {

        Object l = left.evaluate(ctx);
        Object r = right.evaluate(ctx);

        switch (op) {
            case "=": return l.equals(r);
            case "!=": return !l.equals(r);
            case ">": return toInt(l) > toInt(r);
            case "<": return toInt(l) < toInt(r);
            case ">=": return toInt(l) >= toInt(r);
            case "<=": return toInt(l) <= toInt(r);
            case "AND": return toBoolean(l) && toBoolean(r);
            case "OR": return toBoolean(l) || toBoolean(r);
            case "+": return toInt(l) + toInt(r);
            case "-": return toInt(l) - toInt(r);
            case "*": return toInt(l) * toInt(r);
            case "/":
                int divisor = toInt(r);
                if (divisor == 0) {
                    throw new ArithmeticException("Division by zero");
                }
                return toInt(l) / divisor;
        }

        throw new RuntimeException("Unsupported operator: " + op);
    }

    private int toInt(Object value) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Expected numeric value but got: " + value);
        }
        return ((Number) value).intValue();
    }

    private boolean toBoolean(Object value) {
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("Expected boolean value but got: " + value);
        }
        return (Boolean) value;
    }
}