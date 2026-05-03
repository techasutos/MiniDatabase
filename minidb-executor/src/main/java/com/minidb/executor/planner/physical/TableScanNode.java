package com.minidb.executor.planner.physical;

import com.minidb.storage.engine.TableStorage;
import com.minidb.storage.row.Row;

import java.util.List;

public class TableScanNode implements PlanNode {

    private final TableStorage storage;

    public TableScanNode(TableStorage storage) {
        this.storage = storage;
    }

    @Override
    public List<Row> execute() throws Exception {
        return storage.scan();
    }
}