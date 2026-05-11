package com.minidb.sql;

import com.minidb.sql.ast.*;
import com.minidb.sql.generated.SQLBaseVisitor;
import com.minidb.sql.generated.SQLParser;

import java.util.*;

/**
 * ASTBuilder — ANTLR parse-tree → AST.
 * Fully implements all grammar rules including:
 *  - GROUP BY, HAVING, ORDER BY, LIMIT/OFFSET
 *  - IS NULL / IS NOT NULL predicates
 *  - Column constraints (PRIMARY KEY, NOT NULL, UNIQUE, DEFAULT)
 *  - All data types (INT, BIGINT, VARCHAR(n), DOUBLE, BOOLEAN, DATE, TIMESTAMP)
 *  - Aggregate functions (COUNT, SUM, MIN, MAX, AVG)
 */
public class ASTBuilder extends SQLBaseVisitor<Object> {

    @Override
    public Object visitParse(SQLParser.ParseContext ctx) {
        return visit(ctx.statement());
    }

    // ── SELECT ─────────────────────────────────────────────────────────────

    @Override
    public Statement visitSelectStatement(SQLParser.SelectStatementContext ctx) {

        // SELECT items
        List<SelectItem> items = new ArrayList<>();
        if (ctx.selectElements().STAR() != null) {
            items.add(new SelectItem(new ColumnExpression("*"), null));
        } else {
            for (var el : ctx.selectElements().selectElement()) {
                Expression expr = (Expression) visit(el.expression());
                String alias = el.identifier() != null ? normalizeIdentifier(el.identifier().getText()) : null;
                items.add(new SelectItem(expr, alias));
            }
        }

        String table = ctx.tableSource().qualifiedName().getText();

        List<JoinClause> joins = new ArrayList<>();
        if (ctx.joinClause() != null) {
            for (var joinCtx : ctx.joinClause()) {
                joins.add((JoinClause) visit(joinCtx));
            }
        }

        // WHERE
        Expression where = ctx.whereClause() != null
                ? (Expression) visit(ctx.whereClause().expression()) : null;

        // GROUP BY
        List<Expression> groupBy = new ArrayList<>();
        if (ctx.groupByClause() != null) {
            for (var expr : ctx.groupByClause().expression()) {
                groupBy.add((Expression) visit(expr));
            }
        }

        // HAVING
        Expression having = ctx.havingClause() != null
                ? (Expression) visit(ctx.havingClause().expression()) : null;

        // ORDER BY
        List<OrderByItem> orderBy = new ArrayList<>();
        if (ctx.orderByClause() != null) {
            for (var el : ctx.orderByClause().orderByElement()) {
                Expression expr = (Expression) visit(el.expression());
                boolean asc = el.DESC() == null; // default ASC
                orderBy.add(new OrderByItem(expr, asc));
            }
        }

        // LIMIT / OFFSET
        int limit  = -1;
        int offset = 0;
        if (ctx.limitClause() != null) {
            limit  = Integer.parseInt(ctx.limitClause().NUMBER(0).getText());
            if (ctx.limitClause().OFFSET() != null) {
                offset = Integer.parseInt(ctx.limitClause().NUMBER(1).getText());
            }
        }

        return new SelectStatement(items, table, joins, where, groupBy, having, orderBy, limit, offset);
    }

    @Override
    public JoinClause visitJoinClause(SQLParser.JoinClauseContext ctx) {

        String table = ctx.tableSource().qualifiedName().getText();
        Expression condition = (Expression) visit(ctx.expression());
        return new JoinClause(table, condition);
    }

    // ── DML ────────────────────────────────────────────────────────────────

    @Override
    public Statement visitInsertStatement(SQLParser.InsertStatementContext ctx) {
        String table = ctx.qualifiedName().getText();
        List<String> columnNames = new ArrayList<>();
        for (var identifier : ctx.identifier()) {
            columnNames.add(normalizeIdentifier(identifier.getText()));
        }
        List<Expression> values = new ArrayList<>();
        for (var v : ctx.value()) {
            values.add((Expression) visit(v));
        }
        return new InsertStatement(table, columnNames, values);
    }

    @Override
    public Statement visitUpdateStatement(SQLParser.UpdateStatementContext ctx) {
        Map<String, Expression> assignments = new LinkedHashMap<>();
        for (var a : ctx.assignment()) {
            assignments.put(normalizeIdentifier(a.identifier().getText()), (Expression) visit(a.expression()));
        }
        Expression where = ctx.whereClause() == null ? null : (Expression) visit(ctx.whereClause().expression());
        return new UpdateStatement(ctx.qualifiedName().getText(), assignments, where);
    }

    @Override
    public Statement visitDeleteStatement(SQLParser.DeleteStatementContext ctx) {
        Expression where = ctx.whereClause() == null ? null : (Expression) visit(ctx.whereClause().expression());
        return new DeleteStatement(ctx.qualifiedName().getText(), where);
    }

    // ── DDL ────────────────────────────────────────────────────────────────

    @Override
    public Statement visitCreateDatabase(SQLParser.CreateDatabaseContext ctx) {
        return new CreateDatabaseStatement(normalizeIdentifier(ctx.identifier().getText()));
    }

    @Override
    public Statement visitCreateSchema(SQLParser.CreateSchemaContext ctx) {
        return new CreateSchemaStatement(ctx.qualifiedName().getText());
    }

    @Override
    public Statement visitCreateTable(SQLParser.CreateTableContext ctx) {
        String tableName = ctx.qualifiedName().getText();
        List<ColumnDefinition> cols = new ArrayList<>();
        for (var c : ctx.columnDef()) {
            cols.add((ColumnDefinition) visit(c));
        }
        return new CreateTableStatement(tableName, cols);
    }

    @Override
    public ColumnDefinition visitColumnDef(SQLParser.ColumnDefContext ctx) {
        String name     = normalizeIdentifier(ctx.identifier().getText());
        String typeName = ctx.typeName().getText();

        boolean primaryKey   = false;
        boolean notNull      = false;
        boolean unique       = false;
        Object  defaultValue = null;

        for (var constraint : ctx.columnConstraint()) {
            if (constraint.PRIMARY() != null)  primaryKey = true;
            if (constraint.NOT() != null)      notNull    = true;
            if (constraint.UNIQUE() != null)   unique     = true;
            if (constraint.DEFAULT() != null && constraint.literal() != null) {
                defaultValue = ((LiteralExpression) visit(constraint.literal())).evaluate(null);
            }
        }

        return new ColumnDefinition(name, typeName, primaryKey, notNull, unique, defaultValue);
    }

    @Override
    public Statement visitDropDatabase(SQLParser.DropDatabaseContext ctx) {
        return new DropDatabaseStatement(normalizeIdentifier(ctx.identifier().getText()), ctx.ifExists() != null);
    }

    @Override
    public Statement visitDropSchema(SQLParser.DropSchemaContext ctx) {
        return new DropSchemaStatement(ctx.qualifiedName().getText(), ctx.ifExists() != null);
    }

    @Override
    public Statement visitDropTable(SQLParser.DropTableContext ctx) {
        return new DropTableStatement(ctx.qualifiedName().getText(), ctx.ifExists() != null);
    }

    // ── Transactions ───────────────────────────────────────────────────────

    @Override
    public Statement visitBeginTransaction(SQLParser.BeginTransactionContext ctx) { return new BeginTransactionStatement(); }

    @Override
    public Statement visitCommitTransaction(SQLParser.CommitTransactionContext ctx) { return new CommitTransactionStatement(); }

    @Override
    public Statement visitRollbackTransaction(SQLParser.RollbackTransactionContext ctx) { return new RollbackTransactionStatement(); }

    // ── Expressions ────────────────────────────────────────────────────────

    @Override
    public Expression visitLogicalExpression(SQLParser.LogicalExpressionContext ctx) {
        if (ctx.AND() != null)
            return new BinaryExpression((Expression) visit(ctx.logicalExpression(0)), (Expression) visit(ctx.logicalExpression(1)), "AND");
        if (ctx.OR() != null)
            return new BinaryExpression((Expression) visit(ctx.logicalExpression(0)), (Expression) visit(ctx.logicalExpression(1)), "OR");
        if (ctx.NOT() != null)
            return new UnaryExpression("NOT", (Expression) visit(ctx.logicalExpression(0)));
        return (Expression) visit(ctx.predicate());
    }

    @Override
    public Expression visitPredicate(SQLParser.PredicateContext ctx) {
        Expression base = (Expression) visit(ctx.comparisonExpression());
        if (ctx.IS() != null) {
            // IS NULL or IS NOT NULL
            boolean isNotNull = ctx.NOT() != null;
            return new UnaryExpression(isNotNull ? "IS_NOT_NULL" : "IS_NULL", base);
        }
        return base;
    }

    @Override
    public Expression visitComparisonExpression(SQLParser.ComparisonExpressionContext ctx) {
        if (ctx.comparisonOperator() == null) {
            return (Expression) visit(ctx.additiveExpression(0));
        }
        Expression left  = (Expression) visit(ctx.additiveExpression(0));
        Expression right = (Expression) visit(ctx.additiveExpression(1));
        return new BinaryExpression(left, right, ctx.comparisonOperator().getText());
    }

    @Override
    public Expression visitAdditiveExpression(SQLParser.AdditiveExpressionContext ctx) {
        Expression current = (Expression) visit(ctx.multiplicativeExpression(0));
        for (int i = 1; i < ctx.multiplicativeExpression().size(); i++) {
            String op    = ctx.getChild(2 * i - 1).getText();
            Expression r = (Expression) visit(ctx.multiplicativeExpression(i));
            current = new BinaryExpression(current, r, op);
        }
        return current;
    }

    @Override
    public Expression visitMultiplicativeExpression(SQLParser.MultiplicativeExpressionContext ctx) {
        Expression current = (Expression) visit(ctx.primaryExpression(0));
        for (int i = 1; i < ctx.primaryExpression().size(); i++) {
            String op    = ctx.getChild(2 * i - 1).getText();
            Expression r = (Expression) visit(ctx.primaryExpression(i));
            current = new BinaryExpression(current, r, op);
        }
        return current;
    }

    @Override
    public Expression visitColumnReference(SQLParser.ColumnReferenceContext ctx) {
        return new ColumnExpression(ctx.getText());
    }

    @Override
    public Expression visitFunctionCall(SQLParser.FunctionCallContext ctx) {
        String name = ctx.functionName().getText().toUpperCase(Locale.ROOT);
        FunctionCallExpression.AggregateFunction fn = FunctionCallExpression.AggregateFunction.valueOf(name);
        Expression arg = ctx.STAR() != null ? null : (Expression) visit(ctx.expression());
        return new FunctionCallExpression(fn, arg);
    }

    @Override
    public Expression visitValue(SQLParser.ValueContext ctx) {
        if (ctx.NULL() != null) return new LiteralExpression(null);
        return (Expression) visit(ctx.literal());
    }

    @Override
    public Expression visitLiteral(SQLParser.LiteralContext ctx) {
        if (ctx.NUMBER()       != null) return new LiteralExpression(Integer.parseInt(ctx.NUMBER().getText()));
        if (ctx.FLOAT_NUMBER() != null) return new LiteralExpression(Double.parseDouble(ctx.FLOAT_NUMBER().getText()));
        if (ctx.TRUE()         != null) return new LiteralExpression(Boolean.TRUE);
        if (ctx.FALSE()        != null) return new LiteralExpression(Boolean.FALSE);
        return new LiteralExpression(unquoteString(ctx.STRING_LITERAL().getText()));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String normalizeIdentifier(String text) {
        if (text.startsWith("`") && text.endsWith("`") && text.length() >= 2)
            return text.substring(1, text.length() - 1);
        return text;
    }

    private String unquoteString(String text) {
        String s = text.substring(1, text.length() - 1);
        return s.replace("\\'", "'").replace("\\\\", "\\");
    }
}

