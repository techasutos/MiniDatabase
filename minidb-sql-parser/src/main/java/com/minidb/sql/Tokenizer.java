package com.minidb.sql;

import java.util.*;

/**
 * A simple SQL tokenizer that splits a SQL query into its components.
 * It handles basic SQL syntax, including parentheses and commas.
 * This is a very basic implementation and does not handle all edge cases (like quoted strings, comments, etc.).
 * For a production-ready SQL parser, consider using a library like JSQLParser.
 * Example usage:
 * String sql = "SELECT name, age FROM users WHERE age > 30";
 * List<String> tokens = Tokenizer.tokenize(sql);
 * System.out.println(tokens);
 * Output: [SELECT, name, ,, age, FROM, users, WHERE, age, >, 30]
 */
public class Tokenizer {

    public static List<String> tokenize(String sql) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (char c : sql.toCharArray()) {

            if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else if (c == '(' || c == ')' || c == ',') {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0)
            tokens.add(current.toString());

        return tokens;
    }
}