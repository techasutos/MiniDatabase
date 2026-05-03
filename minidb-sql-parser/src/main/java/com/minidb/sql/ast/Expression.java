package com.minidb.sql.ast;

/**
 * Represents an expression in the SQL AST.
 * This can be a literal, column reference, function call, etc.
 * The evaluate method will be used to compute the value of the expression
 * given a RowContext which provides access to the current row's data.
 * For example, a column reference expression would look up the column value in the RowContext,
 * while a literal expression would simply return its value.
 * Function call expressions would evaluate their arguments and then apply the function logic.
 * This interface allows for a flexible representation of various types of expressions in the SQL AST.
 * The actual implementation of the evaluate method will depend on the specific type of expression.
 * For instance, a ColumnReferenceExpression would implement evaluate by fetching the column value from the RowContext,
 * while a LiteralExpression would return its stored literal value.
 * The Expression interface is a key part of the SQL AST as it allows
 * for the representation of complex expressions that can be evaluated at runtime.
 * The RowContext parameter in the evaluate method provides the necessary
 * context for evaluating expressions that depend on the current row's data, such as column references.
 *
 */
public interface Expression {
    Object evaluate(RowContext ctx);
}