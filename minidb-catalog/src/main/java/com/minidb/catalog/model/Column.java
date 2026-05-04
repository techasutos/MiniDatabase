package com.minidb.catalog.model;

import java.io.Serializable;

/**
 * Represents a table column with its name, type, storage size, and constraints.
 *
 * Constraints supported:
 *  - NOT NULL
 *  - UNIQUE
 *  - PRIMARY KEY  (implies NOT NULL + UNIQUE)
 *  - DEFAULT value
 */
public class Column implements Serializable {

    private final String name;
    private final DataType type;
    private final int storageSize;   // actual byte width (handles VARCHAR(n))

    // Constraints
    private final boolean primaryKey;
    private final boolean notNull;
    private final boolean unique;
    private final Object defaultValue;

    /** Full constructor */
    public Column(String name, DataType type, int storageSize,
                  boolean primaryKey, boolean notNull, boolean unique,
                  Object defaultValue) {
        this.name         = name;
        this.type         = type;
        this.storageSize  = storageSize;
        this.primaryKey   = primaryKey;
        this.notNull      = notNull || primaryKey;
        this.unique       = unique || primaryKey;
        this.defaultValue = defaultValue;
    }

    /** Convenience constructor (no constraints) */
    public Column(String name, DataType type) {
        this(name, type, type.getSize(), false, false, false, null);
    }

    /** Convenience constructor with explicit storage size (for VARCHAR(n)) */
    public Column(String name, DataType type, int storageSize) {
        this(name, type, storageSize, false, false, false, null);
    }

    public String  getName()         { return name; }
    public DataType getType()        { return type; }
    public int     getStorageSize()  { return storageSize; }
    public boolean isPrimaryKey()    { return primaryKey; }
    public boolean isNotNull()       { return notNull; }
    public boolean isUnique()        { return unique; }
    public Object  getDefaultValue() { return defaultValue; }

    @Override
    public String toString() {
        return name + " " + type
                + (storageSize != type.getSize() ? "(" + storageSize + ")" : "")
                + (primaryKey ? " PRIMARY KEY" : "")
                + (notNull    ? " NOT NULL"    : "")
                + (unique && !primaryKey ? " UNIQUE" : "")
                + (defaultValue != null  ? " DEFAULT " + defaultValue : "");
    }
}