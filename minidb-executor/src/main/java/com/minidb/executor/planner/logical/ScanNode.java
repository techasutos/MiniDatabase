package com.minidb.executor.planner.logical;

import com.minidb.catalog.model.Table;

public class ScanNode implements LogicalPlan {

    private final Table table;

    public ScanNode(Table table) {
        this.table = table;
    }

    public Table getTable() {
        return table;
    }
}