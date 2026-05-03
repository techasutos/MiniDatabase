package com.minidb.sql.ast;

public class DeleteStatement implements Statement {

    private final String table;
    private final Expression where;

    public DeleteStatement(String table, Expression where) {
        this.table = table;
        this.where = where;
    }

    public String getTable() { return table; }
    public Expression getWhere() { return where; }
}

