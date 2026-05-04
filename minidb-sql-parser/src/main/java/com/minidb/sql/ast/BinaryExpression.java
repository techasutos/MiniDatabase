package com.minidb.sql.ast;

/**
 * Binary expression supporting: =, !=, <, <=, >, >=, AND, OR, +, -, *, /, LIKE
 * Handles numeric comparison across INT, BIGINT, DOUBLE types.
 */
public class BinaryExpression implements Expression {

    private final Expression left;
    private final Expression right;
    private final String op;

    public BinaryExpression(Expression left, Expression right, String op) {
        this.left  = left;
        this.right = right;
        this.op    = op;
    }

    public Expression getLeft()  { return left; }
    public Expression getRight() { return right; }
    public String     getOp()    { return op; }

    @Override
    public Object evaluate(RowContext ctx) {
        // Short-circuit logical operators
        if ("AND".equals(op)) {
            Object l = left.evaluate(ctx);
            if (l instanceof Boolean && !(Boolean) l) return false;
            Object r = right.evaluate(ctx);
            return toBoolean(l) && toBoolean(r);
        }
        if ("OR".equals(op)) {
            Object l = left.evaluate(ctx);
            if (l instanceof Boolean && (Boolean) l) return true;
            Object r = right.evaluate(ctx);
            return toBoolean(l) || toBoolean(r);
        }

        Object l = left.evaluate(ctx);
        Object r = right.evaluate(ctx);

        return switch (op) {
            case "="    -> equalsValue(l, r);
            case "!="   -> !equalsValue(l, r);
            case ">"    -> compareNumeric(l, r) > 0;
            case ">="   -> compareNumeric(l, r) >= 0;
            case "<"    -> compareNumeric(l, r) < 0;
            case "<="   -> compareNumeric(l, r) <= 0;
            case "LIKE" -> likeMatch(l, r);
            case "+"    -> addValues(l, r);
            case "-"    -> subtractValues(l, r);
            case "*"    -> multiplyValues(l, r);
            case "/"    -> divideValues(l, r);
            default -> throw new RuntimeException("Unsupported operator: " + op);
        };
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private boolean equalsValue(Object l, Object r) {
        if (l == null && r == null) return true;
        if (l == null || r == null) return false;
        if (l instanceof Number && r instanceof Number)
            return toDouble(l) == toDouble(r);
        return l.equals(r);
    }

    @SuppressWarnings("unchecked")
    private int compareNumeric(Object l, Object r) {
        if (l instanceof Number && r instanceof Number)
            return Double.compare(toDouble(l), toDouble(r));
        if (l instanceof Comparable && r instanceof Comparable)
            return ((Comparable<Object>) l).compareTo(r);
        throw new IllegalArgumentException("Cannot compare " + l + " with " + r);
    }

    private boolean likeMatch(Object l, Object r) {
        if (l == null || r == null) return false;
        String value   = l.toString();
        String pattern = r.toString()
                .replace("\\", "\\\\")
                .replace(".", "\\.")
                .replace("%", ".*")
                .replace("_", ".");
        return value.matches("(?i)" + pattern);
    }

    private Object addValues(Object l, Object r) {
        if (l instanceof Number && r instanceof Number) {
            if (l instanceof Double || r instanceof Double) return toDouble(l) + toDouble(r);
            if (l instanceof Long   || r instanceof Long)   return toLong(l)   + toLong(r);
            return toInt(l) + toInt(r);
        }
        return l.toString() + r.toString(); // string concatenation
    }

    private Object subtractValues(Object l, Object r) {
        if (l instanceof Double || r instanceof Double) return toDouble(l) - toDouble(r);
        if (l instanceof Long   || r instanceof Long)   return toLong(l)   - toLong(r);
        return toInt(l) - toInt(r);
    }

    private Object multiplyValues(Object l, Object r) {
        if (l instanceof Double || r instanceof Double) return toDouble(l) * toDouble(r);
        if (l instanceof Long   || r instanceof Long)   return toLong(l)   * toLong(r);
        return toInt(l) * toInt(r);
    }

    private Object divideValues(Object l, Object r) {
        if (toDouble(r) == 0) throw new ArithmeticException("Division by zero");
        if (l instanceof Double || r instanceof Double) return toDouble(l) / toDouble(r);
        if (l instanceof Long   || r instanceof Long)   return toLong(l)   / toLong(r);
        return toInt(l) / toInt(r);
    }

    private double  toDouble(Object v) { return ((Number) v).doubleValue(); }
    private long    toLong(Object v)   { return ((Number) v).longValue(); }
    private int     toInt(Object v)    { return ((Number) v).intValue(); }
    private boolean toBoolean(Object v){
        if (v instanceof Boolean) return (Boolean) v;
        throw new IllegalArgumentException("Expected boolean, got: " + v);
    }
}