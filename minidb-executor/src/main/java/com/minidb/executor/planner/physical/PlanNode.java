package com.minidb.executor.planner.physical;

import com.minidb.storage.row.Row;
import java.util.List;
import java.util.function.Consumer;
/**
 * Represents a physical plan node in the execution plan.
 * Each node corresponds to a specific operation (e.g., scan, filter, join).
 * The execute() method runs the operation and returns the resulting rows.
 */
public interface PlanNode {
    List<Row> execute() throws Exception;

    default void forEachRow(Consumer<Row> consumer) throws Exception {
        for (Row row : execute()) {
            consumer.accept(row);
        }
    }
}