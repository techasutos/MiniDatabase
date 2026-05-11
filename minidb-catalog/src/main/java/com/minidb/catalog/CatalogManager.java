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

    public synchronized List<String> listDatabaseNames() {
        List<String> names = new ArrayList<>(databases.keySet());
        Collections.sort(names);
        return names;
    }

    public synchronized List<String> listSchemaNames(String dbName) {
        Database db = getDatabase(dbName);
        List<String> names = new ArrayList<>();
        for (Schema schema : db.getSchemas()) {
            names.add(schema.getName());
        }
        Collections.sort(names);
        return names;
    }

    public synchronized List<String> listTableNames(String dbName, String schemaName) {
        Database db = getDatabase(dbName);
        Schema schema = db.getSchema(schemaName);
        if (schema == null) {
            throw new RuntimeException("Schema not found");
        }
        List<String> names = new ArrayList<>();
        for (Table table : schema.getTables()) {
            names.add(table.getName());
        }
        Collections.sort(names);
        return names;
    }

    public synchronized List<String> listColumnDefinitions(String dbName, String schemaName, String tableName) {
        Database db = getDatabase(dbName);
        Schema schema = db.getSchema(schemaName);
        if (schema == null) {
            throw new RuntimeException("Schema not found");
        }
        Table table = schema.getTable(tableName);
        if (table == null) {
            throw new RuntimeException("Table not found");
        }
        List<String> defs = new ArrayList<>();
        for (Column column : table.getColumns()) {
            defs.add(column.getName() + ":" + column.getType().name());
        }
        return defs;
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
        Table table = new Table(tableId, tableName, columns);
        table.setRootPageId(nextRootPageId());
        schema.addTable(table);
        persistIfConfigured();
    }

    private int nextRootPageId() {
        int max = -1;
        for (Database db : databases.values()) {
            for (Schema schema : db.getSchemas()) {
                for (Table table : schema.getTables()) {
                    if (table.getRootPageId() > max) {
                        max = table.getRootPageId();
                    }
                }
            }
        }
        return max + 1;
    }

    private void persistIfConfigured() {
        if (store != null) {
            store.save(databases);
        }
    }
}