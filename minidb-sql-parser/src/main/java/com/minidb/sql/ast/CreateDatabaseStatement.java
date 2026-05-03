package com.minidb.sql.ast;

/**
 * CREATE DATABASE statement.
 * Example: CREATE DATABASE mydb;
 * This statement creates a new database with the specified name.
 * The database name must be unique and cannot already exist in the system.
 * If the database is created successfully, it will be available for use in subsequent SQL statements.
 * Note: This statement does not specify any additional options or parameters for the database creation.
 * The syntax is simple and straightforward, focusing solely on the creation of a new database with the given name.
 * The execution of this statement will typically involve checking for name conflicts, allocating necessary resources, and registering the new database in the system catalog.
 * The CREATE DATABASE statement is a fundamental part of SQL and is essential for setting up the initial structure of a database system.
 * The statement is designed to be easy to understand and use, allowing users to quickly create new databases as needed.
 * Overall, the CREATE DATABASE statement is a crucial component of SQL that enables users to establish new databases and organize their data effectively.
 */
public class CreateDatabaseStatement implements Statement {

    private final String name;

    public CreateDatabaseStatement(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}