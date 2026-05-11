package com.minidb.executor.planner.physical;

import com.minidb.storage.engine.TableStorage;
import com.minidb.storage.index.IndexManager;
import com.minidb.storage.row.Row;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Physical scan node that uses a single-column equality index.
 */
public class IndexScanNode implements PlanNode {

    private final TableStorage storage;
    private final IndexManager indexManager;
    private final String qualifiedTable;
    private final String columnName;
    private final Comparable<?> keyValue;

    public IndexScanNode(TableStorage storage,
                         IndexManager indexManager,
                         String qualifiedTable,
                         String columnName,
                         Comparable<?> keyValue) {
        this.storage = storage;
        this.indexManager = indexManager;
        this.qualifiedTable = qualifiedTable;
        this.columnName = columnName;
        this.keyValue = keyValue;
    }

    @Override
    public List<Row> execute() throws Exception {
        List<Row> rows = new ArrayList<>();
        forEachRow(rows::add);
        return rows;
    }

    @Override
    public void forEachRow(Consumer<Row> consumer) throws Exception {
        for (Long pointer : indexManager.lookup(qualifiedTable, columnName, keyValue)) {
            Row row = storage.readRow(pointer);
            if (row != null) {
                consumer.accept(row);
            }
        }
    }
}


