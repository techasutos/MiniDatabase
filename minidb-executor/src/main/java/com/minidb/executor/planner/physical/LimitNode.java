package com.minidb.executor.planner.physical;

import com.minidb.storage.row.Row;

import java.util.List;

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
        List<Row> rows = child.execute();

        int from = Math.min(offset, rows.size());
        int to   = limit < 0 ? rows.size() : Math.min(from + limit, rows.size());

        return rows.subList(from, to);
    }
}

