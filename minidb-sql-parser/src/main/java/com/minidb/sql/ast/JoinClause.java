package com.minidb.sql.ast;

/**
 * Represents a single INNER JOIN clause in a SELECT statement.
 */
public class JoinClause {

    private final String table;
    private final Expression condition;

    public JoinClause(String table, Expression condition) {
        this.table = table;
        this.condition = condition;
    }

    public String getTable() {
        return table;
    }

    public Expression getCondition() {
        return condition;
    }
}

