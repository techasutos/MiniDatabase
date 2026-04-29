package com.minidb.sql;


import com.minidb.sql.ast.*;
import com.minidb.sql.generated.SQLBaseVisitor;
import com.minidb.sql.generated.SQLParser;

import java.util.*;

public class ASTBuilder extends SQLBaseVisitor<Object> {

    @Override
    public Statement visitSelectStatement(SQLParser.SelectStatementContext ctx) {

        List<SelectItem> items = new ArrayList<>();

        if (ctx.selectElements().getText().equals("*")) {
            items.add(new SelectItem(new ColumnExpression("*"), null));
        } else {
            for (var el : ctx.selectElements().selectElement()) {
                Expression expr = (Expression) visit(el.expression());
                String alias = el.identifier() != null ? el.identifier().getText() : null;
                items.add(new SelectItem(expr, alias));
            }
        }

        String table = ctx.tableSource().qualifiedName().getText();

        Expression where = null;
        if (ctx.whereClause() != null) {
            where = (Expression) visit(ctx.whereClause().expression());
        }

        return new SelectStatement(items, table, where);
    }

    @Override
    public Statement visitInsertStatement(SQLParser.InsertStatementContext ctx) {

        String table = ctx.qualifiedName().getText();

        List<Expression> values = new ArrayList<>();

        for (var v : ctx.value()) {
            values.add((Expression) visit(v));
        }

        return new InsertStatement(table, values);
    }

    @Override
    public Expression visitLiteral(SQLParser.LiteralContext ctx) {
        if (ctx.NUMBER() != null) {
            return new LiteralExpression(Integer.parseInt(ctx.NUMBER().getText()));
        }
        return new LiteralExpression(ctx.STRING_LITERAL().getText().replace("'", ""));
    }

    @Override
    public Expression visitColumnReference(SQLParser.ColumnReferenceContext ctx) {
        return new ColumnExpression(ctx.getText());
    }

    @Override
    public Expression visitComparisonExpression(SQLParser.ComparisonExpressionContext ctx) {
        if (ctx.comparisonOperator() == null) {
            return (Expression) visit(ctx.additiveExpression(0));
        }

        Expression left = (Expression) visit(ctx.additiveExpression(0));
        Expression right = (Expression) visit(ctx.additiveExpression(1));

        return new BinaryExpression(left, right, ctx.comparisonOperator().getText());
    }
}