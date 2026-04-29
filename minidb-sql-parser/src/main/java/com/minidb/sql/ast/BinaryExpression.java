package com.minidb.sql.ast;

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
            case ">": return ((Integer) l) > ((Integer) r);
            case "<": return ((Integer) l) < ((Integer) r);
            case "+": return ((Integer) l) + ((Integer) r);
            case "-": return ((Integer) l) - ((Integer) r);
        }

        throw new RuntimeException("Unsupported operator: " + op);
    }
}