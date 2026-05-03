package com.minidb.storage.row;

import com.minidb.catalog.model.Column;
import com.minidb.catalog.model.DataType;
import com.minidb.catalog.model.Table;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * RowSerializer handles the conversion of Row objects to byte arrays for storage,
 * and vice versa. It uses the Table schema to determine how to serialize/deserialize
 * each column value.
 * This is a simplified implementation and may not cover all edge cases or data types.
 * In a production system, you would need to handle more complex types, null values,
 * and ensure proper error handling and performance optimizations.
 */
public class RowSerializer {

    public static byte[] serialize(Row row, Table table) {
        ByteBuffer buffer = ByteBuffer.allocate(table.getRowSize());

        for (int i = 0; i < table.getColumns().size(); i++) {
            Column col = table.getColumns().get(i);
            Object val = row.getValues().get(i);

            if (col.getType() == DataType.INT) {
                buffer.putInt((Integer) val);
            } else if (col.getType() == DataType.STRING) {
                byte[] bytes = ((String) val).getBytes(StandardCharsets.UTF_8);
                byte[] fixed = new byte[col.getType().getSize()];
                System.arraycopy(bytes, 0, fixed, 0, Math.min(bytes.length, fixed.length));
                buffer.put(fixed);
            }
        }

        return buffer.array();
    }

    public static Row deserialize(byte[] data, Table table) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        java.util.List<Object> values = new java.util.ArrayList<>();

        for (Column col : table.getColumns()) {
            if (col.getType() == DataType.INT) {
                values.add(buffer.getInt());
            } else if (col.getType() == DataType.STRING) {
                byte[] bytes = new byte[col.getType().getSize()];
                buffer.get(bytes);
                values.add(new String(bytes).trim());
            }
        }

        return new Row(values);
    }
}