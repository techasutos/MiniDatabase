package com.minidb.storage.page;

import com.minidb.catalog.model.Table;
import com.minidb.storage.row.Row;
import com.minidb.storage.row.RowSerializer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a page that stores rows of a table.
 * Each page has a header that tracks the number of rows and the next page in the chain.
 * The rest of the page is used to store serialized rows.
 *
 */
public class TablePage {

    private final Page page;
    private final Table table;

    public TablePage(Page page, Table table) {
        this.page = page;
        this.table = table;
    }

    // Header layout:
    // [0-3]: int rowCount
    // [4-7]: int nextPageId (-1 if none)
    public static final int HEADER_SIZE = 8;

    public int getRowCount() {
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        return buffer.getInt(0);
    }

    public int getNextPageId() {
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        return buffer.getInt(4);
    }

    public void setNextPageId(int nextPageId) {
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.putInt(4, nextPageId);
        page.markDirty();
    }

    public boolean hasSpace() {
        int rowCount = getRowCount();
        int maxRows = (Page.PAGE_SIZE - HEADER_SIZE) / table.getRowSize();
        return rowCount < maxRows;
    }

    public void insertRow(Row row) {
        if (!hasSpace()) {
            throw new IllegalStateException("Page full");
        }
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        int rowCount = buffer.getInt(0);
        int offset = HEADER_SIZE + rowCount * table.getRowSize();
        byte[] serialized = RowSerializer.serialize(row, table);
        System.arraycopy(serialized, 0, page.getData(), offset, serialized.length);
        buffer.putInt(0, rowCount + 1);
        page.markDirty();
    }

    public List<Row> getRows() {
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        int rowCount = buffer.getInt(0);
        List<Row> rows = new ArrayList<>();
        int offset = HEADER_SIZE;
        for (int i = 0; i < rowCount; i++) {
            byte[] rowBytes = new byte[table.getRowSize()];
            System.arraycopy(page.getData(), offset, rowBytes, 0, rowBytes.length);
            rows.add(RowSerializer.deserialize(rowBytes, table));
            offset += rowBytes.length;
        }

        return rows;
    }

    public Row getRow(int slotIndex) {
        int rowCount = getRowCount();
        if (slotIndex < 0 || slotIndex >= rowCount) {
            return null;
        }

        int offset = HEADER_SIZE + slotIndex * table.getRowSize();
        byte[] rowBytes = new byte[table.getRowSize()];
        System.arraycopy(page.getData(), offset, rowBytes, 0, rowBytes.length);
        return RowSerializer.deserialize(rowBytes, table);
    }

    /**
     * Overwrites all rows in this page with the provided list.
     * Updates the row count and marks the page as dirty.
     */
    public void overwriteRows(List<Row> rows) {
        ByteBuffer buffer = ByteBuffer.wrap(page.getData());
        buffer.putInt(0, rows.size());
        int offset = HEADER_SIZE;
        for (Row row : rows) {
            byte[] serialized = RowSerializer.serialize(row, table);
            System.arraycopy(serialized, 0, page.getData(), offset, serialized.length);
            offset += serialized.length;
        }
        // Zero out any remaining space (optional, for safety)
        int end = offset;
        int pageSize = page.getData().length;
        if (end < pageSize) {
            java.util.Arrays.fill(page.getData(), end, pageSize, (byte)0);
        }
        page.markDirty();
    }
}