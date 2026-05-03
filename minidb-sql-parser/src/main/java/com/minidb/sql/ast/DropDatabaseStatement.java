package com.minidb.sql.ast;

public class DropDatabaseStatement implements Statement {

    private final String name;
    private final boolean ifExists;

    public DropDatabaseStatement(String name, boolean ifExists) {
        this.name = name;
        this.ifExists = ifExists;
    }

    public String getName() { return name; }
    public boolean isIfExists() { return ifExists; }
}

