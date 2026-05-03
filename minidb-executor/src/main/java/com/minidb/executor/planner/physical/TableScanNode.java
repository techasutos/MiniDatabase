package com.minidb.executor.planner.physical;

import com.minidb.storage.engine.TableStorage;
import com.minidb.storage.row.Row;

import java.util.List;
/**
 * TableScanNode is a physical plan node that performs a full table scan.
 * It retrieves all rows from the specified table storage.
 * This node is typically used when there are no indexes available or when the query requires all rows to be processed.
 * It implements the PlanNode interface, which defines the contract for executing the plan and returning results.
 * The execute() method calls the scan() method of the TableStorage to fetch all rows and returns them as a list.
 * This node is a fundamental building block in the physical execution plan and can be combined with other
 * nodes like FilterNode, ProjectionNode, etc., to form more complex query execution plans.
 * Note: In a real implementation, you would likely want to add support for pagination
 * (limit/offset) and possibly push down filters to optimize the scan.
 *
 */
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