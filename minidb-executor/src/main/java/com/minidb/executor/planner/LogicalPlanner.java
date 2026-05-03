package com.minidb.executor.planner;

import com.minidb.catalog.CatalogManager;
import com.minidb.catalog.model.Table;
import com.minidb.executor.planner.logical.*;
import com.minidb.sql.ast.*;
/**
 * LogicalPlanner is responsible for converting a parsed SQL statement (AST) into a logical plan.
 * It uses the CatalogManager to resolve table metadata and constructs a tree of logical operators
 * that represent the execution strategy for the query.
 * Currently, it supports only SELECT statements, but it can be extended to handle INSERT, UPDATE, DELETE, etc.
 * The logical plan is an abstract representation of the query execution steps, which can later be optimized
 * and converted into a physical plan for execution.
 *
 * Example usage:
 * CatalogManager catalog = new CatalogManager();
 * LogicalPlanner planner = new LogicalPlanner(catalog);
 * Statement stmt = parseSql("SELECT name FROM users WHERE age > 30");
 * LogicalPlan plan = planner.plan(stmt);
 * Author: Ashutosh Dang
 * Date: 03-05-2026
 */
public class LogicalPlanner {

    private final CatalogManager catalog;

    public LogicalPlanner(CatalogManager catalog) {
        this.catalog = catalog;
    }
    /**
     * Main entry point for planning a SQL statement.
     * It checks the type of the statement and delegates to the appropriate planning method.
     * Currently, it only supports SELECT statements.
     *
     * @param stmt The parsed SQL statement (AST)
     * @return A LogicalPlan representing the execution strategy for the query
     */
    public LogicalPlan plan(Statement stmt) {

        if (stmt instanceof SelectStatement select) {
            return planSelect(select);
        }

        throw new UnsupportedOperationException("Only SELECT supported in planner");
    }
    /**
     * Plans a SELECT statement by constructing a logical plan that includes scanning the table,
     * applying filters based on the WHERE clause, and projecting the selected columns.
     *
     * Steps:
     * 1. Resolve the table from the catalog and create a ScanNode.
     * 2. If there is a WHERE clause, wrap the ScanNode in a FilterNode.
     * 3. Finally, wrap everything in a ProjectionNode to select the desired columns.
     *
     * @param stmt The SELECT statement AST
     * @return A LogicalPlan representing the execution strategy for the SELECT query
     */
    private LogicalPlan planSelect(SelectStatement stmt) {

        Table table = resolveTable(stmt.getTable());

        // Step 1: Scan
        LogicalPlan plan = new ScanNode(table);

        // Step 2: Filter
        if (stmt.getWhere() != null) {
            plan = new FilterNode(plan, stmt.getWhere());
        }

        // Step 3: Projection
        plan = new ProjectionNode(plan, stmt.getItems());

        return plan;
    }
    /**
     * Resolves a table name from the catalog. The qualified name is expected to be in the format "database.schema.table".
     * It splits the qualified name into parts and retrieves the corresponding Table object from the CatalogManager.
     *
     * @param qualifiedName The fully qualified table name (e.g., "mydb.public.users")
     * @return The Table object representing the resolved table
     */
    private Table resolveTable(String qualifiedName) {

        String[] parts = qualifiedName.split("\\.");

        return catalog
                .getDatabase(parts[0])
                .getSchema(parts[1])
                .getTable(parts[2]);
    }
}