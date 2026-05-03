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
    private final String type;

    public ColumnDefinition(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }
}