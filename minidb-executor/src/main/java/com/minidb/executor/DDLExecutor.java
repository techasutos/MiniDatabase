package com.minidb.executor;

import com.minidb.catalog.CatalogManager;
import com.minidb.catalog.model.*;
import com.minidb.sql.ast.*;

import java.util.*;
/**
 * DDLExecutor is responsible for executing Data Definition Language (DDL) statements
 * such as CREATE DATABASE, CREATE TABLE, DROP DATABASE, etc. It interacts with the
 * CatalogManager to modify the database schema accordingly.
 */
public class DDLExecutor {

    private final CatalogManager catalog;

    public DDLExecutor(CatalogManager catalog) {
        this.catalog = catalog;
    }

    public String execute(Statement stmt) {

        if (stmt instanceof CreateDatabaseStatement createDb) {
            catalog.createDatabase(createDb.getName());
            return "DATABASE CREATED";
        }

        if (stmt instanceof CreateSchemaStatement createSchema) {
            return createSchema(createSchema);
        }

        if (stmt instanceof CreateTableStatement createTable) {
            return createTable(createTable);
        }

        if (stmt instanceof DropDatabaseStatement dropDatabase) {
            return dropDatabase(dropDatabase);
        }

        if (stmt instanceof DropSchemaStatement dropSchema) {
            return dropSchema(dropSchema);
        }

        if (stmt instanceof DropTableStatement dropTable) {
            return dropTable(dropTable);
        }

        throw new UnsupportedOperationException("Unsupported DDL: " + stmt.getClass());
    }
    /**
     * Handles the creation of a new table in the database. It parses the table name to extract
     * the database, schema, and table components, and then processes the column definitions to
     * create a list of Column objects. Finally, it calls the CatalogManager to create the table.
     *
     * @param stmt The CreateTableStatement containing the details of the table to be created.
     * @return A confirmation message indicating that the table has been created.
     */
    private String createTable(CreateTableStatement stmt) {

        String[] parts = stmt.getTableName().split("\\.");

        if (parts.length != 3) {
            throw new IllegalArgumentException("Use db.schema.table format");
        }

        String db = parts[0];
        String schema = parts[1];
        String table = parts[2];

        List<Column> columns = new ArrayList<>();

        for (ColumnDefinition col : stmt.getColumns()) {
            columns.add(new Column(
                    col.getName(),
                    resolveType(col.getType())
            ));
        }

        catalog.createTable(db, schema, table, columns);

        return "TABLE CREATED";
    }
    /**
     * Handles the creation of a new schema in the database. It parses the schema name to extract
     * the database and schema components, and then calls the CatalogManager to create the schema.
     *
     * @param stmt The CreateSchemaStatement containing the details of the schema to be created.
     * @return A confirmation message indicating that the schema has been created.
     */
    private String createSchema(CreateSchemaStatement stmt) {
        String[] parts = stmt.getSchemaName().split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Use db.schema format");
        }

        catalog.createSchema(parts[0], parts[1]);
        return "SCHEMA CREATED";
    }
    /**
     * Handles the dropping of a database. It calls the CatalogManager to drop the specified database.
     *
     * @param stmt The DropDatabaseStatement containing the name of the database to be dropped.
     * @return A confirmation message indicating that the database has been dropped.
     */
    private String dropDatabase(DropDatabaseStatement stmt) {
        catalog.dropDatabase(stmt.getName());
        return "DATABASE DROPPED";
    }
    /**
     * Handles the dropping of a schema. It parses the schema name to extract the database and schema
     * components, and then calls the CatalogManager to drop the specified schema.
     *
     * @param stmt The DropSchemaStatement containing the name of the schema to be dropped.
     * @return A confirmation message indicating that the schema has been dropped.
     */
    private String dropSchema(DropSchemaStatement stmt) {
        String[] parts = stmt.getSchemaName().split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Use db.schema format");
        }

        catalog.dropSchema(parts[0], parts[1]);
        return "SCHEMA DROPPED";
    }
    /**
     * Handles the dropping of a table. It parses the table name to extract the database, schema, and
     * table components, and then calls the CatalogManager to drop the specified table.
     *
     * @param stmt The DropTableStatement containing the name of the table to be dropped.
     * @return A confirmation message indicating that the table has been dropped.
     */
    private String dropTable(DropTableStatement stmt) {
        String[] parts = stmt.getTableName().split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Use db.schema.table format");
        }

        catalog.dropTable(parts[0], parts[1], parts[2]);
        return "TABLE DROPPED";
    }
    /**
     * Resolves a string representation of a data type to the corresponding DataType enum value.
     * It currently supports VARCHAR, INT, and STRING types. If an unsupported type is provided,
     * it throws an UnsupportedOperationException.
     *
     * @param typeName The string representation of the data type (e.g., "VARCHAR(255)", "INT", "STRING").
     * @return The corresponding DataType enum value.
     */
    private DataType resolveType(String typeName) {
        String normalized = typeName.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("VARCHAR(")) {
            return DataType.STRING;
        }
        if ("INT".equals(normalized) || "STRING".equals(normalized)) {
            return DataType.valueOf(normalized);
        }
        throw new UnsupportedOperationException("Type not implemented yet: " + typeName);
    }
}