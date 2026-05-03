package com.minidb.sql.ast;

public class DropSchemaStatement implements Statement {

    private final String schemaName;
    private final boolean ifExists;

    public DropSchemaStatement(String schemaName, boolean ifExists) {
        this.schemaName = schemaName;
        this.ifExists = ifExists;
    }

    public String getSchemaName() { return schemaName; }
    public boolean isIfExists() { return ifExists; }
}

