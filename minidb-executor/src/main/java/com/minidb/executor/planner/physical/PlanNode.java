package com.minidb.executor.planner.physical;

import com.minidb.storage.row.Row;
import java.util.List;

public interface PlanNode {
    List<Row> execute() throws Exception;
}