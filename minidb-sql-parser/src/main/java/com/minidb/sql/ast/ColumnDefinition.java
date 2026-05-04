package com.minidb.sql.ast;

/**
 * Represents a column definition in a CREATE TABLE statement.
 * For example, in the statement:
 * CREATE TABLE users (
 *     id INT,
 *     name VARCHAR(255),
 *     email VARCHAR(255)
 * );
 * Each line inside the parentheses represents a ColumnDefinition.
 * This class holds the column name and its data type.
 */
public class ColumnDefinition {

    private final String name;
    private final String type;        // raw type string e.g. "VARCHAR(100)", "INT"
    private final boolean primaryKey;
    private final boolean notNull;
    private final boolean unique;
    private final Object  defaultValue;

    /** Full constructor */
    public ColumnDefinition(String name, String type,
                            boolean primaryKey, boolean notNull,
                            boolean unique, Object defaultValue) {
        this.name         = name;
        this.type         = type;
        this.primaryKey   = primaryKey;
        this.notNull      = notNull;
        this.unique       = unique;
        this.defaultValue = defaultValue;
    }

    /** Backward-compatible no-constraint constructor */
    public ColumnDefinition(String name, String type) {
        this(name, type, false, false, false, null);
    }

    public String  getName()         { return name; }
    public String  getType()         { return type; }
    public boolean isPrimaryKey()    { return primaryKey; }
    public boolean isNotNull()       { return notNull; }
    public boolean isUnique()        { return unique; }
    public Object  getDefaultValue() { return defaultValue; }
}