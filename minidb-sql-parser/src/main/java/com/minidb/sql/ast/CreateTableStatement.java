package com.minidb.sql.ast;

import java.util.List;

/**
 * Represents a CREATE TABLE statement in SQL.
 * Example: CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(255));
 * This class holds the table name and a list of column definitions.
 * The ColumnDefinition class (not shown here) would include details like column name, data type, and constraints.
 * This AST node is used by the parser to build a structured representation of the SQL statement, which can then be processed by the execution engine.
 * Note: This is a simplified version and does not cover all possible features of CREATE TABLE (like foreign keys, indexes, etc.).
 * Future enhancements could include support for additional table options, constraints, and more complex data types.
 * Author: Ashutosh Dang
 */
public class CreateTableStatement implements Statement {

    private final String tableName;
    private final List<ColumnDefinition> columns;

    public CreateTableStatement(String tableName, List<ColumnDefinition> columns) {
        this.tableName = tableName;
        this.columns = columns;
    }

    public String getTableName() {
        return tableName;
    }

    public List<ColumnDefinition> getColumns() {
        return columns;
    }
}