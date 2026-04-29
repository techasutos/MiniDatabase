package com.minidb.sql;

import com.minidb.sql.ast.Statement;
import com.minidb.sql.generated.SQLLexer;
import com.minidb.sql.generated.SQLParser;
import org.antlr.v4.runtime.*;

public class SQLParserService {

    public Statement parse(String sql) {

        CharStream input = CharStreams.fromString(sql);

        SQLLexer lexer = new SQLLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        SQLParser parser = new SQLParser(tokens);

        return (Statement) new ASTBuilder().visit(parser.parse());
    }
}