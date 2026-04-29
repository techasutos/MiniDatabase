package com.minidb.sql.ast;

public interface Expression {
    Object evaluate(RowContext ctx);
}