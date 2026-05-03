package com.minidb.sql.ast;

import java.util.List;
import java.util.Map;

public class UpdateStatement implements Statement {

    private final String table;
    private final Map<String, Expression> assignments;
    private final Expression where;

    public UpdateStatement(String table, Map<String, Expression> assignments, Expression where) {
        this.table = table;
        this.assignments = assignments;
        this.where = where;
    }

    public String getTable() { return table; }
    public Map<String, Expression> getAssignments() { return assignments; }
    public Expression getWhere() { return where; }
}

