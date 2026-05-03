package com.minidb.catalog;

import com.minidb.catalog.model.*;

import java.util.*;

public class CatalogManager {

    private final Map<String, Database> databases;
    private final CatalogStore store;

    public CatalogManager() {
        this.databases = new HashMap<>();
        this.store = null;
    }

    public CatalogManager(CatalogStore store) {
        this.store = Objects.requireNonNull(store);
        this.databases = new HashMap<>(store.load());
    }

    public synchronized void createDatabase(String name) {
        if (databases.containsKey(name)) {
            throw new RuntimeException("Database exists");
        }
        databases.put(name, new Database(name));
        persistIfConfigured();
    }

    public synchronized Database getDatabase(String name) {
        Database db = databases.get(name);
        if (db == null) throw new RuntimeException("Database not found");
        return db;
    }

    public synchronized void createSchema(String dbName, String schemaName) {
        Database db = getDatabase(dbName);
        db.addSchema(new Schema(schemaName));
        persistIfConfigured();
    }

    public synchronized void dropDatabase(String name) {
        if (databases.remove(name) == null) {
            throw new RuntimeException("Database not found");
        }
        persistIfConfigured();
    }

    public synchronized void dropSchema(String dbName, String schemaName) {
        Database db = getDatabase(dbName);
        if (db.removeSchema(schemaName) == null) {
            throw new RuntimeException("Schema not found");
        }
        persistIfConfigured();
    }

    public synchronized void dropTable(String dbName, String schemaName, String tableName) {
        Database db = getDatabase(dbName);
        Schema schema = db.getSchema(schemaName);

        if (schema == null) {
            throw new RuntimeException("Schema not found");
        }

        if (schema.removeTable(tableName) == null) {
            throw new RuntimeException("Table not found");
        }

        persistIfConfigured();
    }

    public synchronized void createTable(
            String dbName,
            String schemaName,
            String tableName,
            List<Column> columns
    ) {
        Database db = getDatabase(dbName);
        Schema schema = db.getSchema(schemaName);

        if (schema == null)
            throw new RuntimeException("Schema not found");

        int tableId = schema.getTables().size();
        schema.addTable(new Table(tableId, tableName, columns));
        persistIfConfigured();
    }

    private void persistIfConfigured() {
        if (store != null) {
            store.save(databases);
        }
    }
}