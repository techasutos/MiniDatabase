package com.minidb.sql.ast;

/**
 * Represents a SQL statement in the abstract syntax tree (AST).
 * This is a marker interface that serves as the base for all specific SQL statement types,
 * such as SELECT, INSERT, UPDATE, DELETE, etc. Each specific statement type will implement
 * this interface and contain the necessary fields and methods to represent its structure and semantics.
 */
public interface Statement {
}
