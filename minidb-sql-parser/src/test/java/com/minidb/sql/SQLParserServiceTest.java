package com.minidb.sql;

import com.minidb.sql.ast.CreateSchemaStatement;
import com.minidb.sql.ast.Expression;
import com.minidb.sql.ast.JoinClause;
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

    @Test
    void parsesInnerJoinSelectStatement() {
        SQLParserService parser = new SQLParserService();

        Statement stmt = parser.parse(
                "SELECT * FROM testdb.public.users INNER JOIN testdb.public.orders ON testdb.public.users.id = testdb.public.orders.user_id"
        );

        assertInstanceOf(SelectStatement.class, stmt);
        SelectStatement select = (SelectStatement) stmt;

        assertEquals("testdb.public.users", select.getTable());
        assertTrue(select.hasJoins());
        assertEquals(1, select.getJoins().size());

        JoinClause join = select.getJoins().get(0);
        assertEquals("testdb.public.orders", join.getTable());
        assertNotNull(join.getCondition());
        assertEquals(Boolean.TRUE, join.getCondition().evaluate(new RowContext(Map.of(
                "testdb.public.users.id", 1,
                "testdb.public.orders.user_id", 1
        ))));
    }
}

