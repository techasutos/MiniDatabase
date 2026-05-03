package com.minidb.sql.ast;

/**
 * Represents a literal value in an SQL expression, such as a number, string, or boolean.
 * This class implements the Expression interface and simply returns the literal value when evaluated.
 * For example, in the SQL statement "SELECT * FROM users WHERE age > 30", the number "30" would be represented as a LiteralExpression.
 * The value can be of any type (e.g., Integer, String, Boolean) depending on the context in which it is used.
 * This class is immutable, as the value is set at construction and cannot be changed afterwards.
 * The evaluate method simply returns the stored literal value, as it does not depend on any context or variables.
 * This class is a fundamental building block for constructing more complex expressions in the SQL abstract syntax tree (AST).
 * Example usage:
 * LiteralExpression literal = new LiteralExpression(42);
 * Object result = literal.evaluate(null); // result will be 42
 * In this example, we create a LiteralExpression with the integer value 42, and when we evaluate it, it simply returns that value.
 * This class can be used in various parts of the SQL AST, such as in WHERE clauses, SELECT lists, or as part of more complex expressions.
 * Overall, the LiteralExpression class provides a simple way to represent constant values in the SQL AST and is essential for evaluating expressions that involve literals.
 * @see Expression
 * @see RowContext
 * @author Ashutosh Dang
 */
public class LiteralExpression implements Expression {

    private final Object value;

    public LiteralExpression(Object value) {
        this.value = value;
    }

    public Object evaluate(RowContext ctx) {
        return value;
    }
}