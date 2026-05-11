package com.minidb.executor.planner.physical;

import com.minidb.catalog.model.Table;
import com.minidb.storage.row.Row;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper for building row contexts that support both qualified and unqualified column names.
 */
final class RowContextBuilder {

    private RowContextBuilder() {
    }

    static Map<String, Object> build(Table table, Row row) {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < table.getColumns().size(); i++) {
            addValue(values, table.getColumns().get(i).getName(), row.getValues().get(i));
        }
        return values;
    }

    static Map<String, Object> build(Table leftTable, Row leftRow, Table rightTable, Row rightRow) {
        Map<String, Object> values = new HashMap<>();
        addRow(values, leftTable, leftRow);
        addRow(values, rightTable, rightRow);
        return values;
    }

    private static void addRow(Map<String, Object> values, Table table, Row row) {
        for (int i = 0; i < table.getColumns().size(); i++) {
            addValue(values, table.getColumns().get(i).getName(), row.getValues().get(i));
        }
    }

    private static void addValue(Map<String, Object> values, String columnName, Object value) {
        values.put(columnName, value);
        int dot = columnName.lastIndexOf('.');
        if (dot >= 0 && dot < columnName.length() - 1) {
            values.putIfAbsent(columnName.substring(dot + 1), value);
        }
    }
}

