package com.minidb.executor;

import com.minidb.catalog.CatalogManager;
import com.minidb.catalog.model.Table;
import com.minidb.sql.ast.*;
import com.minidb.storage.engine.TableStorage;
import com.minidb.storage.row.Row;

import java.nio.file.Path;
import java.util.*;

public class Engine {

    private final Path dataDir;
    private final CatalogManager catalog;

    public Engine(Path dataDir, CatalogManager catalog) {
        this.dataDir = dataDir;
        this.catalog = catalog;
    }

    public String execute(Statement stmt) {

        if (stmt instanceof InsertStatement insert) {
            return insert(insert);
        }

        if (stmt instanceof SelectStatement select) {
            return select(select);
        }

        throw new RuntimeException("Unsupported statement");
    }

    // ================= INSERT =================

    private String insert(InsertStatement stmt) {

        Table table = resolveTable(stmt.getTable());

        if (stmt.getValues().size() != table.getColumns().size()) {
            throw new RuntimeException("Column count mismatch");
        }

        Path file = dataDir.resolve(stmt.getTable() + ".tbl");

        TableStorage storage = new TableStorage(file);

        List<Object> values = new ArrayList<>();

        for (Expression e : stmt.getValues()) {
            values.add(e.evaluate(null));
        }

        storage.insert(new Row(values));

        return "OK";
    }

    // ================= SELECT =================

    private String select(SelectStatement stmt) {

        Table table = resolveTable(stmt.getTable());

        Path file = dataDir.resolve(stmt.getTable() + ".tbl");

        TableStorage storage = new TableStorage(file);

        List<Row> rows = storage.scan();

        StringBuilder result = new StringBuilder();

        for (Row r : rows) {

            Map<String, Object> map = new HashMap<>();

            // 🔥 bind real column names
            for (int i = 0; i < table.getColumns().size(); i++) {
                String colName = table.getColumns().get(i).getName();
                map.put(colName, r.getValues().get(i));
            }

            RowContext ctx = new RowContext(map);

            // WHERE
            if (stmt.getWhere() != null) {
                Object cond = stmt.getWhere().evaluate(ctx);
                if (!(Boolean) cond) continue;
            }

            List<Object> out = new ArrayList<>();

            for (SelectItem item : stmt.getItems()) {

                if (item.getExpression() instanceof ColumnExpression col &&
                        col.getColumn().equals("*")) {

                    out.addAll(r.getValues());

                } else {
                    out.add(item.getExpression().evaluate(ctx));
                }
            }

            result.append(out).append("\n");
        }

        return result.toString();
    }

    // ================= RESOLUTION =================

    private Table resolveTable(String qualifiedName) {

        String[] parts = qualifiedName.split("\\.");

        if (parts.length != 3) {
            throw new RuntimeException("Use db.schema.table format");
        }

        return catalog
                .getDatabase(parts[0])
                .getSchema(parts[1])
                .getTable(parts[2]);
    }
}