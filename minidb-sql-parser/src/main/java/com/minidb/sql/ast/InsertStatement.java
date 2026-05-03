package com.minidb.sql.ast;

import java.util.List;

/**
 * Represents an INSERT statement in SQL.
 * Example: INSERT INTO table_name VALUES (value1, value2, ...);
 * This class holds the table name and the list of values to be inserted.
 * Note: For simplicity, this example assumes a basic INSERT statement without column names or complex expressions.
 * In a real implementation, you would likely want to support more complex syntax and features.
 * This class is part of the Abstract Syntax Tree (AST) for the SQL parser.
 * It implements the Statement interface, which is a common interface for all types of SQL statements in the AST.
 * The values are represented as a list of Expression objects, which can be literals, column references, or more complex expressions.
 * The table name is stored as a string, and the values are stored as a list of Expression objects.
 * This class provides getter methods for the table name and the list of values, allowing other parts of the parser or execution engine to access this information when processing the INSERT statement.
 * Overall, this class serves as a fundamental building block for representing and processing INSERT statements in the SQL parser.
 * Author: Ashutosh Dang
 */
public class InsertStatement implements Statement {

    private final String table;
    private final List<String> columnNames;
    private final List<Expression> values;

    public InsertStatement(String table, List<String> columnNames, List<Expression> values) {
        this.table = table;
        this.columnNames = columnNames;
        this.values = values;
    }

    public String getTable() {
        return table;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public List<Expression> getValues() {
        return values;
    }
}