package com.minidb.sql;

import com.minidb.sql.ast.Statement;
import com.minidb.sql.generated.SQLLexer;
import com.minidb.sql.generated.SQLParser;
import org.antlr.v4.runtime.*;

/**
 * Service class for parsing SQL statements into an abstract syntax tree (AST).
 *  This class uses ANTLR-generated lexer and parser to process SQL input and build an AST representation of the SQL statement.
 *  The resulting AST can then be used for further processing, such as query execution or optimization
 *  Example usage:
 *  SQLParserService parserService = new SQLParserService();
 *  Statement ast = parserService.parse("SELECT * FROM users WHERE id = 1");
 */
public class SQLParserService {

    public Statement parse(String sql) {

        CharStream input = CharStreams.fromString(sql);

        SQLLexer lexer = new SQLLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        SQLParser parser = new SQLParser(tokens);

        return (Statement) new ASTBuilder().visit(parser.parse());
    }
}