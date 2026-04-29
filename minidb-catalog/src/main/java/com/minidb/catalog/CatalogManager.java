    package com.minidb.catalog;

    import com.minidb.catalog.model.*;

    import java.util.*;

public class CatalogManager {

    private final Map<String, Database> databases = new HashMap<>();

    public synchronized void createDatabase(String name) {
        if (databases.containsKey(name)) {
            throw new RuntimeException("Database exists");
        }
        databases.put(name, new Database(name));
    }

    public synchronized Database getDatabase(String name) {
        Database db = databases.get(name);
        if (db == null) throw new RuntimeException("Database not found");
        return db;
    }

    public synchronized void createSchema(String dbName, String schemaName) {
        Database db = getDatabase(dbName);
        db.addSchema(new Schema(schemaName));
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

        schema.addTable(new Table(tableName, columns));
    }
}