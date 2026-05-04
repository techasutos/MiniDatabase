package com.minidb.storage.row;

import com.minidb.catalog.model.Column;
import com.minidb.catalog.model.DataType;
import com.minidb.catalog.model.Table;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes / deserializes a Row to/from a fixed-width binary format.
 *
 * Layout per row:
 *   For each column in schema order:
 *     - INT      → 4 bytes (big-endian int)
 *     - BIGINT   → 8 bytes (big-endian long)
 *     - DOUBLE   → 8 bytes (IEEE 754 double)
 *     - BOOLEAN  → 1 byte  (0 = false, 1 = true)
 *     - STRING / VARCHAR(n) → n bytes UTF-8, null-padded on right
 *     - DATE     → 8 bytes (epoch-days as long)
 *     - TIMESTAMP→ 8 bytes (epoch-millis as long)
 */
public class RowSerializer {

    public static byte[] serialize(Row row, Table table) {
        ByteBuffer buffer = ByteBuffer.allocate(table.getRowSize());

        for (int i = 0; i < table.getColumns().size(); i++) {
            Column col = table.getColumns().get(i);
            Object val = row.getValues().get(i);
            writeValue(buffer, col, val);
        }
        return buffer.array();
    }

    public static Row deserialize(byte[] data, Table table) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        List<Object> values = new ArrayList<>();
        for (Column col : table.getColumns()) {
            values.add(readValue(buffer, col));
        }
        return new Row(values);
    }

    // ── Write ──────────────────────────────────────────────────────────────

    private static void writeValue(ByteBuffer buffer, Column col, Object val) {
        int size = col.getStorageSize();
        switch (col.getType()) {
            case INT -> buffer.putInt(val == null ? 0 : ((Number) val).intValue());
            case BIGINT, DATE, TIMESTAMP -> buffer.putLong(val == null ? 0L : ((Number) val).longValue());
            case DOUBLE -> buffer.putDouble(val == null ? 0.0 : ((Number) val).doubleValue());
            case BOOLEAN -> buffer.put(val == null ? (byte)0 : ((Boolean) val ? (byte)1 : (byte)0));
            case STRING -> {
                byte[] bytes = val == null ? new byte[0]
                        : val.toString().getBytes(StandardCharsets.UTF_8);
                byte[] fixed = new byte[size];
                System.arraycopy(bytes, 0, fixed, 0, Math.min(bytes.length, fixed.length));
                buffer.put(fixed);
            }
            default -> throw new IllegalArgumentException("Unsupported DataType: " + col.getType());
        }
    }

    // ── Read ───────────────────────────────────────────────────────────────

    private static Object readValue(ByteBuffer buffer, Column col) {
        int size = col.getStorageSize();
        return switch (col.getType()) {
            case INT       -> buffer.getInt();
            case BIGINT    -> buffer.getLong();
            case DATE      -> buffer.getLong();
            case TIMESTAMP -> buffer.getLong();
            case DOUBLE    -> buffer.getDouble();
            case BOOLEAN   -> buffer.get() != 0;
            case STRING    -> {
                byte[] bytes = new byte[size];
                buffer.get(bytes);
                // trim null padding
                int len = bytes.length;
                while (len > 0 && bytes[len - 1] == 0) len--;
                yield new String(bytes, 0, len, StandardCharsets.UTF_8);
            }
            default -> throw new IllegalArgumentException("Unsupported DataType: " + col.getType());
        };
    }
}