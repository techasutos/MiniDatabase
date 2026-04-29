package com.minidb.sql.ast;

import java.util.List;

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