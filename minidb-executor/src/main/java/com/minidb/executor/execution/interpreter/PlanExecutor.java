package com.minidb.executor.execution.interpreter;

import com.minidb.executor.planner.logical.*;
import com.minidb.storage.engine.TableStorage;
import com.minidb.storage.row.Row;
import com.minidb.sql.ast.*;

import java.util.*;

public class PlanExecutor {

    private final com.minidb.storage.engine.StorageEngine storageEngine;

    public PlanExecutor(com.minidb.storage.engine.StorageEngine storageEngine) {
        this.storageEngine = storageEngine;
    }

    public String execute(LogicalPlan plan) throws Exception {

        List<Map<String, Object>> rows = executeNode(plan);

        StringBuilder sb = new StringBuilder();

        for (Map<String, Object> row : rows) {
            sb.append(row.values()).append("\n");
        }

        return sb.toString();
    }

    private List<Map<String, Object>> executeNode(LogicalPlan node) throws Exception {

        if (node instanceof ScanNode scan) {
            return executeScan(scan);
        }

        if (node instanceof FilterNode filter) {
            return executeFilter(filter);
        }

        if (node instanceof ProjectionNode proj) {
            return executeProjection(proj);
        }

        throw new RuntimeException("Unknown node: " + node.getClass());
    }

    private List<Map<String, Object>> executeScan(ScanNode node) throws Exception {

        TableStorage storage = new TableStorage(
                storageEngine.getBufferPool(),
                node.getTable()
        );

        List<Row> rows = storage.scan();

        List<Map<String, Object>> result = new ArrayList<>();

        for (Row r : rows) {

            Map<String, Object> map = new HashMap<>();

            for (int i = 0; i < node.getTable().getColumns().size(); i++) {
                map.put(
                        node.getTable().getColumns().get(i).getName(),
                        r.getValues().get(i)
                );
            }

            result.add(map);
        }

        return result;
    }

    private List<Map<String, Object>> executeFilter(FilterNode node) throws Exception {

        List<Map<String, Object>> input = executeNode(node.getInput());

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : input) {

            RowContext ctx = new RowContext(row);

            Object val = node.getPredicate().evaluate(ctx);

            if (val instanceof Boolean && (Boolean) val) {
                result.add(row);
            }
        }

        return result;
    }

    private List<Map<String, Object>> executeProjection(ProjectionNode node) throws Exception {

        List<Map<String, Object>> input = executeNode(node.getInput());

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : input) {

            RowContext ctx = new RowContext(row);

            Map<String, Object> out = new LinkedHashMap<>();

            for (SelectItem item : node.getItems()) {

                if (item.getExpression() instanceof ColumnExpression col &&
                        col.getColumn().equals("*")) {

                    out.putAll(row);

                } else {
                    Object val = item.getExpression().evaluate(ctx);
                    out.put(item.toString(), val);
                }
            }

            result.add(out);
        }

        return result;
    }
}