package com.minidb.catalog.model;

import java.io.Serializable;
import java.util.*;

public class Table implements Serializable {

    private final String name;
    private final List<Column> columns;
    private final Map<String, Integer> columnIndex = new HashMap<>();

    public Table(String name, List<Column> columns) {
        this.name = name;
        this.columns = columns;

        for (int i = 0; i < columns.size(); i++) {
            columnIndex.put(columns.get(i).getName(), i);
        }
    }

    public String getName() {
        return name;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public int getColumnIndex(String columnName) {
        Integer idx = columnIndex.get(columnName);
        if (idx == null) {
            throw new RuntimeException("Column not found: " + columnName);
        }
        return idx;
    }
}