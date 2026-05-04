package com.minidb.executor;

import com.minidb.catalog.CatalogManager;
import com.minidb.catalog.model.*;
import com.minidb.sql.ast.*;

import java.util.*;

/**
 * DDLExecutor — executes CREATE/DROP DATABASE/SCHEMA/TABLE with full constraint support.
 */
public class DDLExecutor {

    private final CatalogManager catalog;

    public DDLExecutor(CatalogManager catalog) {
        this.catalog = catalog;
    }

    public String execute(Statement stmt) {
        if (stmt instanceof CreateDatabaseStatement s) {
            catalog.createDatabase(s.getName());
            return "DATABASE CREATED";
        }
        if (stmt instanceof CreateSchemaStatement s)  return createSchema(s);
        if (stmt instanceof CreateTableStatement  s)  return createTable(s);
        if (stmt instanceof DropDatabaseStatement  s)  return dropDatabase(s);
        if (stmt instanceof DropSchemaStatement    s)  return dropSchema(s);
        if (stmt instanceof DropTableStatement     s)  return dropTable(s);
        throw new UnsupportedOperationException("Unsupported DDL: " + stmt.getClass());
    }

    private String createTable(CreateTableStatement stmt) {
        String[] parts = stmt.getTableName().split("\\.");
        if (parts.length != 3)
            throw new IllegalArgumentException("Use db.schema.table format");

        List<Column> columns = new ArrayList<>();
        for (ColumnDefinition def : stmt.getColumns()) {
            DataType type     = DataType.fromSql(def.getType());
            int      size     = DataType.sizeFor(def.getType());
            columns.add(new Column(
                    def.getName(), type, size,
                    def.isPrimaryKey(), def.isNotNull(), def.isUnique(),
                    def.getDefaultValue()
            ));
        }

        catalog.createTable(parts[0], parts[1], parts[2], columns);
        return "TABLE CREATED";
    }

    private String createSchema(CreateSchemaStatement stmt) {
        String[] p = stmt.getSchemaName().split("\\.");
        if (p.length != 2) throw new IllegalArgumentException("Use db.schema format");
        catalog.createSchema(p[0], p[1]);
        return "SCHEMA CREATED";
    }

    private String dropDatabase(DropDatabaseStatement stmt) {
        catalog.dropDatabase(stmt.getName());
        return "DATABASE DROPPED";
    }

    private String dropSchema(DropSchemaStatement stmt) {
        String[] p = stmt.getSchemaName().split("\\.");
        if (p.length != 2) throw new IllegalArgumentException("Use db.schema format");
        catalog.dropSchema(p[0], p[1]);
        return "SCHEMA DROPPED";
    }

    private String dropTable(DropTableStatement stmt) {
        String[] p = stmt.getTableName().split("\\.");
        if (p.length != 3) throw new IllegalArgumentException("Use db.schema.table format");
        catalog.dropTable(p[0], p[1], p[2]);
        return "TABLE DROPPED";
    }
}