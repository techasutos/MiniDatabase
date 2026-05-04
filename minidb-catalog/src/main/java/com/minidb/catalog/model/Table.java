package com.minidb.catalog.model;

import java.io.Serializable;
import java.util.*;

public class Table implements Serializable {

    private final String name;
    private final List<Column> columns;
    private final Map<String, Integer> columnIndex;

    // Future-proofing
    private final int rowSize;
    private final int tableId;      // unique identifier
    private int rootPageId = 0;     // first page (Phase 2+)

    public Table(int tableId, String name, List<Column> columns) {
        this.name = Objects.requireNonNull(name);
        this.tableId = tableId;

        // Immutable copy
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));

        this.columnIndex = new HashMap<>();

        int size = 0;

        for (int i = 0; i < columns.size(); i++) {
            Column col = columns.get(i);
            columnIndex.put(col.getName(), i);

            // CRITICAL: compute row size (use storageSize for VARCHAR(n) support)
            size += col.getStorageSize();
        }

        this.rowSize = size;
    }

    public String getName() {
        return name;
    }

    public int getTableId() {
        return tableId;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public int getColumnIndex(String columnName) {
        Integer idx = columnIndex.get(columnName);
        if (idx == null) {
            throw new IllegalArgumentException("Column not found: " + columnName);
        }
        return idx;
    }

    public int getRowSize() {
        return rowSize;
    }

    public int getRootPageId() {
        return rootPageId;
    }

    public void setRootPageId(int rootPageId) {
        this.rootPageId = rootPageId;
    }
}