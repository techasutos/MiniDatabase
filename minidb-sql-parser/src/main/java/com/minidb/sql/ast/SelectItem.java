package com.minidb.sql.ast;

/**
 * Represents an item in the SELECT clause, which can be an expression with an optional alias.
 * For example, in the query "SELECT a + b AS sum", the expression is "a + b" and the alias is "sum".
 * This class encapsulates both the expression and its alias, allowing for easy access to both components when processing the SELECT clause.
 * The expression can be any valid SQL expression, such as a column reference, a function call, or a more complex expression.
 * The alias is a string that provides a name for the result of the expression, which can be used in other parts of the query, such as the ORDER BY clause.
 * This class is a fundamental part of the SQL AST (Abstract Syntax Tree) representation, as it allows for the structured representation of SELECT items in a query.
 * The SelectItem class is designed to be immutable, with final fields and no setters, ensuring that once an instance is created, its state cannot be changed. This immutability is important for the integrity of the AST and helps prevent bugs related to unintended modifications.
 * Overall, the SelectItem class serves as a crucial component in the representation of SQL queries, enabling the structured handling of SELECT items and their associated expressions and aliases.
 * @author Ashutosh Dang
 */
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