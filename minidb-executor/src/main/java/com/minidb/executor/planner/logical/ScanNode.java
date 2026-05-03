package com.minidb.executor.planner.logical;

import com.minidb.catalog.model.Table;
/**
 * Represents a logical plan node for scanning a table.
 * This node is responsible for reading data from a specified table and producing a stream of rows as output.
 * It serves as a leaf node in the logical plan tree and does not have any child nodes.
 * The ScanNode is typically used as the starting point for query execution,
 * where it retrieves data from the underlying storage engine based on the
 * table schema and any specified filters or projections.
 */
public class ScanNode implements LogicalPlan {

    private final Table table;

    public ScanNode(Table table) {
        this.table = table;
    }

    public Table getTable() {
        return table;
    }
}