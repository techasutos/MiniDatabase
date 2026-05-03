package com.minidb.sql.ast;

import java.util.Map;

/**
 * Represents the context of a single row during SQL execution.
 * It holds the column values for that row, allowing expressions
 * and conditions to access them.
 */
public class RowContext {

    private final Map<String, Object> values;

    public RowContext(Map<String, Object> values) {
        this.values = values;
    }

    public Object get(String column) {
        return values.get(column);
    }
}