package com.minidb.executor.planner.physical;

import com.minidb.storage.row.Row;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Applies LIMIT and OFFSET to the child's output.
 */
public class LimitNode implements PlanNode {

    private final PlanNode child;
    private final int limit;
    private final int offset;

    public LimitNode(PlanNode child, int limit, int offset) {
        this.child  = child;
        this.limit  = limit;
        this.offset = offset;
    }

    @Override
    public List<Row> execute() throws Exception {
        List<Row> rows = new ArrayList<>();
        forEachRow(rows::add);
        int from = Math.min(offset, rows.size());
        int to   = limit < 0 ? rows.size() : Math.min(from + limit, rows.size());

        return rows.subList(from, to);
    }

    @Override
    public void forEachRow(Consumer<Row> consumer) throws Exception {
        final int[] seen = {0};
        final int[] emitted = {0};

        child.forEachRow(row -> {
            if (seen[0]++ < offset) {
                return;
            }
            if (limit >= 0 && emitted[0] >= limit) {
                return;
            }
            emitted[0]++;
            consumer.accept(row);
        });
    }
}

