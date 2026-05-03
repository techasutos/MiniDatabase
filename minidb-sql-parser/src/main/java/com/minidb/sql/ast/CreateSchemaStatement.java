package com.minidb.sql.ast;

public class CreateSchemaStatement implements Statement {

    private final String schemaName;

    public CreateSchemaStatement(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getSchemaName() {
        return schemaName;
    }
}

