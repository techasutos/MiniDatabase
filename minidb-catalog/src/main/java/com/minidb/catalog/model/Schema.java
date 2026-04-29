package com.minidb.catalog.model;

import java.io.Serializable;
import java.util.*;

public class Schema implements Serializable {

    private final String name;
    private final Map<String, Table> tables = new HashMap<>();

    public Schema(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void addTable(Table table) {
        tables.put(table.getName(), table);
    }

    public Table getTable(String name) {
        return tables.get(name);
    }

    public Collection<Table> getTables() {
        return tables.values();
    }
}