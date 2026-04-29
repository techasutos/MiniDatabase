package com.minidb.executor;

import com.minidb.catalog.CatalogManager;
import com.minidb.catalog.model.*;

import java.util.*;

public class DDLExecutor {

    private final CatalogManager catalog;

    public DDLExecutor(CatalogManager catalog) {
        this.catalog = catalog;
    }

    public String execute(String sql) {

        String[] tokens = sql.split(" ");

        switch (tokens[0].toUpperCase()) {

            case "CREATE":
                if ("DATABASE".equalsIgnoreCase(tokens[1])) {
                    catalog.createDatabase(tokens[2]);
                    return "DATABASE CREATED";
                }

                if ("TABLE".equalsIgnoreCase(tokens[1])) {
                    return createTable(sql);
                }

                break;
        }

        return "DDL ERROR";
    }

    private String createTable(String sql) {

        // Example:
        // CREATE TABLE db.schema.table (id INT, name STRING)

        String namePart = sql.substring("CREATE TABLE".length(), sql.indexOf("(")).trim();

        String[] parts = namePart.split("\\.");
        String db = parts[0];
        String schema = parts[1];
        String table = parts[2];

        String colsPart = sql.substring(sql.indexOf("(") + 1, sql.indexOf(")"));

        List<Column> columns = new ArrayList<>();

        for (String col : colsPart.split(",")) {
            String[] c = col.trim().split(" ");
            columns.add(new Column(c[0], DataType.valueOf(c[1].toUpperCase())));
        }

        catalog.createTable(db, schema, table, columns);

        return "TABLE CREATED";
    }
}