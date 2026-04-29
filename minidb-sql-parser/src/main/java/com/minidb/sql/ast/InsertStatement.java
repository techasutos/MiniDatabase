package com.minidb.sql.ast;

import java.util.List;

public class InsertStatement implements Statement {

    private final String table;
    private final List<Expression> values;

    public InsertStatement(String table, List<Expression> values) {
        this.table = table;
        this.values = values;
    }

    public String getTable() {
        return table;
    }

    public List<Expression> getValues() {
        return values;
    }
}