package com.minidb.storage.row;

import java.util.List;

/**
 * Represents a single row of data in a table.
 * Each row contains a list of values corresponding to the columns of the table.
 * This class is immutable to ensure thread safety and data integrity.
 * In a real implementation, we might want to add metadata like column names or types,
 * but for simplicity, we are just storing the values as a list of objects.
 */
public class Row {
    private final List<Object> values;

    public Row(List<Object> values) {
        this.values = values;
    }

    public List<Object> getValues() {
        return values;
    }
}