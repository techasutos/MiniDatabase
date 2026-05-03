package com.minidb.sql;

import com.minidb.sql.ast.CreateSchemaStatement;
import com.minidb.sql.ast.Expression;
import com.minidb.sql.ast.RowContext;
import com.minidb.sql.ast.SelectStatement;
import com.minidb.sql.ast.Statement;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SQLParserServiceTest {

    @Test
    void parsesCreateSchemaStatement() {
        SQLParserService parser = new SQLParserService();

        Statement stmt = parser.parse("CREATE SCHEMA testdb.analytics");

        assertInstanceOf(CreateSchemaStatement.class, stmt);
        CreateSchemaStatement createSchema = (CreateSchemaStatement) stmt;
        assertEquals("testdb.analytics", createSchema.getSchemaName());
    }

    @Test
    void parsesAndEvaluatesComplexWhereExpression() {
        SQLParserService parser = new SQLParserService();

        Statement stmt = parser.parse("SELECT * FROM testdb.public.users WHERE id <= 10 AND score + 2 > 5");
        assertInstanceOf(SelectStatement.class, stmt);

        Expression where = ((SelectStatement) stmt).getWhere();
        assertNotNull(where);

        assertEquals(Boolean.TRUE, where.evaluate(new RowContext(Map.of("id", 10, "score", 4))));
        assertEquals(Boolean.FALSE, where.evaluate(new RowContext(Map.of("id", 11, "score", 4))));
    }
}

