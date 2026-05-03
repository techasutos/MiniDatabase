package com.minidb.catalog.model;

public enum DataType {
    INT(4),
    STRING(255); // fixed for now

    private final int size;

    DataType(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }
}