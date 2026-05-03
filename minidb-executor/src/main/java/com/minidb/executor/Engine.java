package com.minidb.executor;

import com.minidb.catalog.CatalogManager;
import com.minidb.catalog.model.Table;
import com.minidb.executor.planner.QueryPlanner;
import com.minidb.executor.planner.physical.PlanNode;
import com.minidb.sql.ast.*;
import com.minidb.storage.engine.StorageEngine;
import com.minidb.storage.engine.TableStorage;
import com.minidb.storage.row.Row;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The main entry point for executing SQL statements.
 * It handles DDL, DML, and SELECT statements by coordinating between the CatalogManager, StorageEngine, and QueryPlanner.
 * It also manages transaction state and a simple cache of TableStorage instances for active tables.
 * This class is designed to be extended in the future to support more complex features like joins, aggregates, and advanced transaction management.
 * The current implementation focuses on correctness and clarity, with a straightforward execution flow for each type of statement.
 * Note: This is a simplified version and does not include optimizations, error handling, or support for all SQL features.
 * Future improvements could include better error messages, support for more complex queries, and a more robust transaction system.
 * Overall, this class serves as the core of the MiniDB execution engine, providing a foundation for executing SQL statements against the underlying storage and catalog.
 * Author: Ashutosh Dang
 * Date: 03-05-2026
 */
public class Engine {

    private final CatalogManager catalog;
    private final StorageEngine storageEngine;
    private final QueryPlanner planner;
    private final Map<String, TableStorage> tableStorageCache = new HashMap<>();
    private boolean transactionActive;

    public Engine(Path dataDir, CatalogManager catalog) throws Exception {
        this.catalog = catalog;

        this.storageEngine = new StorageEngine(
                dataDir.resolve("minidb.data").toString()
        );

        this.planner = new QueryPlanner(catalog, storageEngine);
    }

    /**
     * Executes a given SQL statement and returns the result as a string.
     * The method dispatches the statement to the appropriate handler based on its type (DDL, DML, SELECT).
     * It also manages transaction state for BEGIN, COMMIT, and ROLLBACK statements.
     * For DDL statements, it delegates to the DDLExecutor.
     * For DML statements (INSERT, UPDATE, DELETE), it performs direct operations on the TableStorage.
     * For SELECT statements, it uses the QueryPlanner to create an execution plan and executes it to get results.
     * Note: This method is designed to be simple and clear for demonstration purposes. In a production system, you would want more robust error handling and support for additional SQL features.
     *
     * @param stmt The SQL statement to execute
     * @return The result of the execution as a string
     * @throws Exception If any error occurs during execution
     */
    public String execute(Statement stmt) throws Exception {

        // ================= DDL =================
        if (stmt instanceof CreateDatabaseStatement ||
                stmt instanceof CreateSchemaStatement ||
                stmt instanceof CreateTableStatement ||
                stmt instanceof DropDatabaseStatement ||
                stmt instanceof DropSchemaStatement ||
                stmt instanceof DropTableStatement) {

            return new DDLExecutor(catalog).execute(stmt);
        }

        if (stmt instanceof BeginTransactionStatement) {
            return beginTransaction();
        }

        if (stmt instanceof CommitTransactionStatement) {
            return commitTransaction();
        }

        if (stmt instanceof RollbackTransactionStatement) {
            return rollbackTransaction();
        }

        // ================= INSERT =================
        if (stmt instanceof InsertStatement insert) {
            return insert(insert);
        }

        if (stmt instanceof UpdateStatement update) {
            return update(update);
        }

        if (stmt instanceof DeleteStatement delete) {
            return delete(delete);
        }

        // ================= SELECT =================
        if (stmt instanceof SelectStatement select) {
            return select(select);
        }

        throw new UnsupportedOperationException("Unsupported statement: " + stmt.getClass());
    }

    // ================= INSERT =================

    /**
     * Handles the execution of an INSERT statement.
     * It resolves the target table, evaluates the provided values, and inserts a new row into the table storage.
     * The method supports both column-specified and column-omitted INSERT syntax, ensuring that values are correctly ordered according to the table schema.
     * Note: This implementation does not currently support partial column inserts (where some columns are omitted). All columns must be provided in the correct order or specified explicitly.
     *
     * @param stmt The InsertStatement to execute
     * @return A string indicating the result of the operation (e.g., "OK")
     * @throws Exception If any error occurs during execution (e.g., table not found, column mismatch)
     */
    private String insert(InsertStatement stmt) throws Exception {

        Table table = resolveTable(stmt.getTable());
        TableStorage storage = getTableStorage(stmt.getTable(), table);

        List<Object> values = new ArrayList<>();

        for (Expression e : stmt.getValues()) {
            values.add(e.evaluate(null));
        }

        List<Object> orderedValues;
        if (stmt.getColumnNames().isEmpty()) {
            if (values.size() != table.getColumns().size()) {
                throw new IllegalArgumentException("Column count mismatch");
            }
            orderedValues = values;
        } else {
            if (stmt.getColumnNames().size() != values.size()) {
                throw new IllegalArgumentException("Column list/value count mismatch");
            }
            orderedValues = new ArrayList<>(java.util.Collections.nCopies(table.getColumns().size(), null));
            for (int i = 0; i < stmt.getColumnNames().size(); i++) {
                int columnIndex = table.getColumnIndex(stmt.getColumnNames().get(i));
                orderedValues.set(columnIndex, values.get(i));
            }
            if (orderedValues.contains(null)) {
                throw new UnsupportedOperationException("Partial column INSERT is not implemented yet");
            }
        }

        storage.insert(new Row(orderedValues));

        return "OK";
    }

    /**
     * Handles the execution of an UPDATE statement.
     * It resolves the target table, evaluates the WHERE clause to find matching rows, and applies the specified assignments to those rows.
     * The method returns a string indicating how many rows were updated.
     * Note: This implementation does not currently support complex expressions in the WHERE clause or assignments. It assumes that the expressions can be evaluated directly against each row.
     *
     * @param stmt The UpdateStatement to execute
     * @return A string indicating how many rows were updated (e.g., "3 ROWS UPDATED")
     * @throws Exception If any error occurs during execution (e.g., table not found, column mismatch)
     */
    private String update(UpdateStatement stmt) throws Exception {
        Table table = resolveTable(stmt.getTable());
        TableStorage storage = getTableStorage(stmt.getTable(), table);

        int updated = storage.update(
                row -> matches(table, row, stmt.getWhere()),
                row -> applyAssignments(table, row, stmt.getAssignments())
        );

        return updated + " ROWS UPDATED";
    }
    /**
     * Handles the execution of a DELETE statement.
     * It resolves the target table, evaluates the WHERE clause to find matching rows, and deletes those rows from the table storage.
     * The method returns a string indicating how many rows were deleted.
     * Note: This implementation does not currently support complex expressions in the WHERE clause. It assumes that the expression can be evaluated directly against each row.
     *
     * @param stmt The DeleteStatement to execute
     * @return A string indicating how many rows were deleted (e.g., "2 ROWS DELETED")
     * @throws Exception If any error occurs during execution (e.g., table not found)
     */
    private String delete(DeleteStatement stmt) throws Exception {
        Table table = resolveTable(stmt.getTable());
        TableStorage storage = getTableStorage(stmt.getTable(), table);

        int deleted = storage.delete(row -> matches(table, row, stmt.getWhere()));
        return deleted + " ROWS DELETED";
    }

    // ================= SELECT (NEW FLOW) =================
    /**
     * Handles the execution of a SELECT statement.
     * It first checks if the SELECT statement contains any unsupported features (like aggregate functions) and throws an exception if it does.
     * Then it uses the QueryPlanner to create an execution plan for the SELECT statement, executes the plan to get the resulting rows, and formats those rows into a string for output.
     * Note: This implementation currently does not support aggregate functions or complex expressions in the SELECT items or WHERE clause. It focuses on basic SELECT functionality with simple expressions.
     *
     * @param stmt The SelectStatement to execute
     * @return A string representation of the query results, with each row on a new line
     * @throws Exception If any error occurs during execution (e.g., unsupported features, table not found)
     */
    private String select(SelectStatement stmt) throws Exception {
        ensureSelectIsSupported(stmt);

        PlanNode plan = planner.plan(stmt);

        List<Row> rows = plan.execute();

        StringBuilder result = new StringBuilder();

        for (Row r : rows) {
            result.append(r.getValues()).append("\n");
        }

        return result.toString();
    }
    /**
     * Handles the execution of a BEGIN TRANSACTION statement.
     * It checks if a transaction is already active and throws an exception if it is.
     * Otherwise, it sets the transaction state to active and begins a transaction on all currently cached TableStorage instances.
     * The method returns a string indicating that the transaction has started.
     * Note: This implementation assumes a simple transaction model where all operations are part
     * of a single transaction. In a more complex system, you would want to support multiple concurrent transactions
     * and more robust isolation levels.
     *
     * @return A string indicating that the transaction has started (e.g., "TRANSACTION STARTED")
     * @throws IllegalStateException If a transaction is already active
     */
    private String beginTransaction() {
        if (transactionActive) {
            throw new IllegalStateException("Transaction already active");
        }
        transactionActive = true;
        for (TableStorage storage : tableStorageCache.values()) {
            storage.beginTransaction();
        }
        return "TRANSACTION STARTED";
    }
    /**
     * Handles the execution of a COMMIT statement.
     * It checks if a transaction is active and throws an exception if it is not.
     * Otherwise, it commits the transaction on all currently cached TableStorage instances
     * and sets the transaction state to inactive.
     * The method returns a string indicating that the transaction has been committed.
     * Note: This implementation assumes a simple transaction model where all operations are
     * part of a single transaction. In a more complex system, you would want to support multiple
     * concurrent transactions and more robust isolation levels.
     *
     * @return A string indicating that the transaction has been committed (e.g., "TRANSACTION COMMITTED")
     * @throws IllegalStateException If no transaction is active
     */
    private String commitTransaction() {
        if (!transactionActive) {
            throw new IllegalStateException("No active transaction");
        }
        for (TableStorage storage : tableStorageCache.values()) {
            storage.commitTransaction();
        }
        transactionActive = false;
        return "TRANSACTION COMMITTED";
    }
    /**
     * Handles the execution of a ROLLBACK statement.
     * It checks if a transaction is active and throws an exception if it is not.
     * Otherwise, it rolls back the transaction on all currently cached TableStorage instances
     * and sets the transaction state to inactive.
     * The method returns a string indicating that the transaction has been rolled back.
     * Note: This implementation assumes a simple transaction model where all operations are
     * part of a single transaction. In a more complex system, you would want to support multiple
     * concurrent transactions and more robust isolation levels.
     *
     * @return A string indicating that the transaction has been rolled back (e.g., "TRANSACTION ROLLED BACK")
     * @throws IllegalStateException If no transaction is active
     */
    private String rollbackTransaction() throws Exception {
        if (!transactionActive) {
            throw new IllegalStateException("No active transaction");
        }
        for (TableStorage storage : tableStorageCache.values()) {
            storage.rollbackTransaction();
        }
        transactionActive = false;
        return "TRANSACTION ROLLED BACK";
    }

    // ================= RESOLUTION =================
    /**
     * Resolves a fully qualified table name (in the format "db.schema.table") to a Table object from the catalog.
     * The method splits the qualified name into its components and navigates through the catalog to find the corresponding Table.
     * If the format is incorrect or any component (database, schema, table) is not found, it throws an IllegalArgumentException.
     *
     * @param qualifiedName The fully qualified name of the table (e.g., "mydb.public.users")
     * @return The Table object corresponding to the qualified name
     * @throws IllegalArgumentException If the qualified name format is incorrect or if any component is not found in the catalog
     */
    private Table resolveTable(String qualifiedName) {

        String[] parts = qualifiedName.split("\\.");

        if (parts.length != 3) {
            throw new IllegalArgumentException("Use db.schema.table format");
        }

        return catalog
                .getDatabase(parts[0])
                .getSchema(parts[1])
                .getTable(parts[2]);
    }
    /**
     * Retrieves a TableStorage instance for the specified qualified table name.
     * If a TableStorage instance for the table is already cached, it returns that instance.
     * Otherwise, it creates a new TableStorage using the StorageEngine, begins a transaction
     * if one is active, caches it, and then returns it.
     *
     * @param qualifiedName The fully qualified name of the table (e.g., "mydb.public.users")
     * @param table The Table object corresponding to the qualified name
     * @return A TableStorage instance for the specified table
     * @throws Exception If any error occurs while creating the TableStorage
     */
    private TableStorage getTableStorage(String qualifiedName, Table table) throws Exception {
        TableStorage storage = tableStorageCache.get(qualifiedName);
        if (storage == null) {
            storage = new TableStorage(storageEngine.getBufferPool(), table);
            if (transactionActive) {
                storage.beginTransaction();
            }
            tableStorageCache.put(qualifiedName, storage);
        }
        return storage;
    }

    /**
     * Evaluates whether a given row from a table matches the specified predicate expression.
     * If the predicate is null, it returns true (indicating that all rows match).
     * Otherwise, it evaluates the predicate expression in the context of the given row and returns true
     * if the result is a Boolean and is true, or false otherwise.
     * Note: This implementation assumes that the predicate expression can be evaluated directly against the row context.
     * In a more complex system, you would want to support more complex expressions and possibly a more robust expression evaluation mechanism.
     *
     * @param table
     * @param row
     * @param predicate
     * @return
     */
    private boolean matches(Table table, Row row, Expression predicate) {
        if (predicate == null) {
            return true;
        }
        Object result = predicate.evaluate(toRowContext(table, row));
        return result instanceof Boolean && (Boolean) result;
    }
    /**
     * Applies the specified assignments to a given row of a table.
     * It evaluates each assignment expression in the context of the current row and updates the row's values accordingly.
     * The method iterates through the assignments, evaluates each expression, and updates
     * the corresponding column in the row based on the column index.
     * Note: This implementation assumes that the assignment expressions
     * can be evaluated directly against the row context and that all columns being
     * assigned are valid for the table schema.
     *
     * @param table The Table object representing the schema of the row
     * @param row The Row object to which the assignments will be applied
     * @param assignments A map of column names to their corresponding assignment expressions
     */
    private void applyAssignments(Table table, Row row, Map<String, Expression> assignments) {
        RowContext ctx = toRowContext(table, row);
        for (Map.Entry<String, Expression> entry : assignments.entrySet()) {
            int columnIndex = table.getColumnIndex(entry.getKey());
            Object value = entry.getValue().evaluate(ctx);
            row.getValues().set(columnIndex, value);
            ctx = toRowContext(table, row);
        }
    }
    /**
     * Converts a given table and row into a RowContext, which is a mapping of column names
     * (both qualified and unqualified) to their corresponding values in the row.
     * The method iterates through the columns of the table, retrieves the corresponding
     * value from the row, and populates a map with both the unqualified column name and
     * the fully qualified column name as keys.
     * This RowContext can then be used for expression evaluation in predicates and assignments.
     *
     * @param table The Table object representing the schema of the row
     * @param row The Row object containing the values to be mapped
     * @return A RowContext containing mappings of column names to their corresponding values in the row
     */
    private RowContext toRowContext(Table table, Row row) {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < table.getColumns().size(); i++) {
            String columnName = table.getColumns().get(i).getName();
            Object value = row.getValues().get(i);
            values.put(columnName, value);
            values.put(table.getName() + "." + columnName, value);
        }
        return new RowContext(values);
    }
    /**
     * Checks if the given SELECT statement contains any unsupported features, such as aggregate functions.
     * It iterates through the SELECT items and the WHERE clause (if present) to check for the presence of aggregate functions.
     * If any aggregate function is found, it throws an UnsupportedOperationException indicating that this feature is not implemented yet.
     * Note: This method is a temporary measure to ensure that only supported features are executed.
     * In a future implementation, you would want to fully support aggregate functions and remove this check.
     *
     * @param stmt The SelectStatement to check for unsupported features
     * @throws UnsupportedOperationException If any unsupported features are found in the SELECT statement
     */
    private void ensureSelectIsSupported(SelectStatement stmt) {
        for (SelectItem item : stmt.getItems()) {
            if (containsAggregate(item.getExpression())) {
                throw new UnsupportedOperationException("Aggregate functions are not implemented yet");
            }
        }
        if (stmt.getWhere() != null && containsAggregate(stmt.getWhere())) {
            throw new UnsupportedOperationException("Aggregate functions in WHERE are not implemented yet");
        }
    }
    /**
     * Recursively checks if a given expression contains any aggregate function calls.
     * It checks if the expression is a FunctionCallExpression (indicating an aggregate function)
     * or if it is a BinaryExpression or UnaryExpression that contains an aggregate function in its sub-expressions.
     * The method uses reflection to access the left and right sub-expressions of BinaryExpression
     * and the operand of UnaryExpression, allowing it to traverse the expression tree without relying on specific getters.
     * Note: This method is a temporary measure to detect unsupported features.
     * In a future implementation, you would want to fully support aggregate functions and remove this check.
     *
     * @param expression The Expression to check for aggregate functions
     * @return true if the expression contains an aggregate function, false otherwise
     */
    private boolean containsAggregate(Expression expression) {
        if (expression == null) {
            return false;
        }
        if (expression instanceof FunctionCallExpression) {
            return true;
        }
        if (expression instanceof BinaryExpression binary) {
            try {
                java.lang.reflect.Field leftField = BinaryExpression.class.getDeclaredField("left");
                java.lang.reflect.Field rightField = BinaryExpression.class.getDeclaredField("right");
                leftField.setAccessible(true);
                rightField.setAccessible(true);
                return containsAggregate((Expression) leftField.get(binary)) || containsAggregate((Expression) rightField.get(binary));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to inspect expression tree", e);
            }
        }
        if (expression instanceof UnaryExpression unary) {
            try {
                java.lang.reflect.Field operandField = UnaryExpression.class.getDeclaredField("operand");
                operandField.setAccessible(true);
                return containsAggregate((Expression) operandField.get(unary));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to inspect unary expression tree", e);
            }
        }
        return false;
    }
}