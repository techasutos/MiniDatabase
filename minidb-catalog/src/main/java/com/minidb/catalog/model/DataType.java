package com.minidb.catalog.model;

import java.io.Serializable;

/**
 * Supported column data types.
 *
 * Size is the fixed on-disk byte width for serialization.
 * VARCHAR(n) stores n bytes (UTF-8 padded/truncated).
 * DATE / TIMESTAMP stored as 8-byte epoch millis.
 */
public enum DataType implements Serializable {
    INT    (4),
    BIGINT (8),
    DOUBLE (8),
    BOOLEAN(1),
    STRING (255),   // legacy alias for VARCHAR(255)
    DATE   (8),     // epoch days as long
    TIMESTAMP(8);   // epoch millis as long

    // VARCHAR(n) is not an enum constant — it is represented as a parameterized type
    // via DataType.varchar(n). When serialized/persisted it maps to this constant
    // with the runtime size stored alongside in Column.

    private final int size;

    DataType(int size) { this.size = size; }

    public int getSize() { return size; }

    /**
     * Parse a type name from SQL DDL, e.g. "INT", "VARCHAR(100)", "BIGINT".
     * For VARCHAR(n) returns DataType.STRING with length n stored in Column.
     */
    public static DataType fromSql(String typeName) {
        if (typeName == null) throw new IllegalArgumentException("Type name is null");
        String upper = typeName.trim().toUpperCase();
        if (upper.startsWith("VARCHAR")) return STRING;
        return switch (upper) {
            case "INT", "INTEGER"   -> INT;
            case "BIGINT"           -> BIGINT;
            case "DOUBLE", "FLOAT"  -> DOUBLE;
            case "BOOLEAN", "BOOL"  -> BOOLEAN;
            case "STRING", "TEXT"   -> STRING;
            case "DATE"             -> DATE;
            case "TIMESTAMP"        -> TIMESTAMP;
            default -> throw new IllegalArgumentException("Unsupported type: " + typeName);
        };
    }

    /**
     * Resolve the storage size for a given typeName (handles VARCHAR(n)).
     */
    public static int sizeFor(String typeName) {
        if (typeName == null) return STRING.size;
        String upper = typeName.trim().toUpperCase();
        if (upper.startsWith("VARCHAR(") && upper.endsWith(")")) {
            try {
                return Integer.parseInt(upper.substring(8, upper.length() - 1));
            } catch (NumberFormatException ignored) {}
        }
        return fromSql(typeName).getSize();
    }
}