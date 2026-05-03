package com.minidb.sql.ast;

public class DropTableStatement implements Statement {

    private final String tableName;
    private final boolean ifExists;

    public DropTableStatement(String tableName, boolean ifExists) {
        this.tableName = tableName;
        this.ifExists = ifExists;
    }

    public String getTableName() { return tableName; }
    public boolean isIfExists() { return ifExists; }
}

