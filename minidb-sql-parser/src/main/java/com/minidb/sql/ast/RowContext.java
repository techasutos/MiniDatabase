package com.minidb.sql.ast;

import java.util.Map;

public class RowContext {

    private final Map<String, Object> values;

    public RowContext(Map<String, Object> values) {
        this.values = values;
    }

    public Object get(String column) {
        return values.get(column);
    }
}