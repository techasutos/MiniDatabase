package com.minidb.catalog.model;

import java.io.Serializable;

public class Column implements Serializable {

    private final String name;
    private final DataType type;

    public Column(String name, DataType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() { return name; }
    public DataType getType() { return type; }
}