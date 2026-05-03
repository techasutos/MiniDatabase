package com.minidb.catalog.model;

import java.io.Serializable;
import java.util.*;

public class Database implements Serializable {

    private final String name;
    private final Map<String, Schema> schemas = new HashMap<>();

    public Database(String name) {
        this.name = name;
        schemas.put("public", new Schema("public"));
    }

    public String getName() {
        return name;
    }

    public Schema getSchema(String name) {
        return schemas.get(name);
    }

    public void addSchema(Schema schema) {
        schemas.put(schema.getName(), schema);
    }

    public Schema removeSchema(String schemaName) {
        return schemas.remove(schemaName);
    }

    public Collection<Schema> getSchemas() {
        return schemas.values();
    }
}