package com.minidb.sql;

public class Parser {

    public static String[] parse(String sql) {
        return sql.trim().split(" ");
    }
}