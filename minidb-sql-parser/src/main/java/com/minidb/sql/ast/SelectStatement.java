package com.minidb.sql.ast;

import java.util.List;

/**
 * Represents a SELECT statement in SQL.
 * Example: SELECT name, age FROM users WHERE age > 30;
 * This class is immutable and serves as a data structure for the parsed SQL statement.
 * It contains the list of selected items, the table name, and an optional WHERE clause.
 * The WHERE clause is represented as an Expression, which can be a complex tree of conditions.
 * This class does not contain any logic for execution or optimization; it is purely a representation of the SQL syntax.
 * The items list can contain column names or expressions (e.g., COUNT(*), name AS username).
 * The table is a simple string representing the table name (no support for JOINs or subqueries in this basic version).
 * The where expression can be null if there is no WHERE clause in the SQL statement.
 * This class is part of the AST (Abstract Syntax Tree) for the SQL parser and will be used by the execution engine to perform the actual query on the database.
 * Future enhancements may include support for JOINs, subqueries, GROUP BY, ORDER BY, and other SQL features, which would require additional fields and classes in the AST.
 */
public class SelectStatement implements Statement {

    private final List<SelectItem> items;
    private final String table;
    private final Expression where;

    public SelectStatement(List<SelectItem> items, String table, Expression where) {
        this.items = items;
        this.table = table;
        this.where = where;
    }

    public List<SelectItem> getItems() {
        return items;
    }

    public String getTable() {
        return table;
    }

    public Expression getWhere() {
        return where;
    }
}