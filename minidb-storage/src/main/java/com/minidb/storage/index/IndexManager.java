package com.minidb.storage.index;

import com.minidb.catalog.model.Table;
import com.minidb.storage.row.Row;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IndexManager — maintains B+ Tree indexes per table per column.
 *
 * Usage:
 *   IndexManager mgr = new IndexManager();
 *   mgr.createIndex("mydb.public.users", "id");
 *   mgr.insertEntry("mydb.public.users", "id", 42, rowPointer);
 *   List<Long> ptrs = mgr.lookup("mydb.public.users", "id", 42);
 *
 * Row pointer encoding: (pageId << 16 | slotOffset)
 */
public class IndexManager {

    /** indexKey = "qualifiedTableName::columnName" */
    private final ConcurrentHashMap<String, BPlusTree> indexes = new ConcurrentHashMap<>();

    /** Create a B+ Tree index for the given table + column */
    public void createIndex(String qualifiedTable, String columnName) {
        String key = indexKey(qualifiedTable, columnName);
        indexes.putIfAbsent(key, new BPlusTree());
    }

    /** Drop an index */
    public void dropIndex(String qualifiedTable, String columnName) {
        indexes.remove(indexKey(qualifiedTable, columnName));
    }

    /** Check whether an index exists */
    public boolean hasIndex(String qualifiedTable, String columnName) {
        return indexes.containsKey(indexKey(qualifiedTable, columnName));
    }

    /** Insert one index entry */
    @SuppressWarnings("unchecked")
    public void insertEntry(String qualifiedTable, String columnName,
                            Comparable<?> keyValue, long rowPointer) {
        BPlusTree tree = getTree(qualifiedTable, columnName);
        tree.insert(keyValue, rowPointer);
    }

    /** Delete one index entry */
    public void deleteEntry(String qualifiedTable, String columnName,
                            Comparable<?> keyValue, long rowPointer) {
        BPlusTree tree = getTree(qualifiedTable, columnName);
        tree.delete(keyValue, rowPointer);
    }

    /** Exact lookup — returns row pointers matching the key */
    public List<Long> lookup(String qualifiedTable, String columnName,
                             Comparable<?> keyValue) {
        BPlusTree tree = getTree(qualifiedTable, columnName);
        return tree.search(keyValue);
    }

    /** Range lookup — returns row pointers where from ≤ key ≤ to */
    public List<Long> rangeLookup(String qualifiedTable, String columnName,
                                  Comparable<?> from, Comparable<?> to) {
        BPlusTree tree = getTree(qualifiedTable, columnName);
        return tree.rangeSearch(from, to);
    }

    /**
     * Rebuild an index from scratch by scanning all rows.
     * colIndex is the 0-based column position in the row.
     */
    @SuppressWarnings("unchecked")
    public void buildFromRows(String qualifiedTable, String columnName,
                              List<Row> rows, int colIndex) {
        createIndex(qualifiedTable, columnName);
        BPlusTree tree = getTree(qualifiedTable, columnName);
        for (int i = 0; i < rows.size(); i++) {
            Object val = rows.get(i).getValues().get(colIndex);
            if (val instanceof Comparable) {
                tree.insert((Comparable<Object>) val, encodePointer(0, i));
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    public static long encodePointer(int pageId, int slotOffset) {
        return ((long) pageId << 32) | (slotOffset & 0xFFFFFFFFL);
    }

    public static int decodePageId(long pointer) {
        return (int) (pointer >>> 32);
    }

    public static int decodeSlot(long pointer) {
        return (int) (pointer & 0xFFFFFFFFL);
    }

    private BPlusTree getTree(String qualifiedTable, String columnName) {
        String key = indexKey(qualifiedTable, columnName);
        BPlusTree tree = indexes.get(key);
        if (tree == null)
            throw new IllegalArgumentException("No index on " + qualifiedTable + "." + columnName);
        return tree;
    }

    private String indexKey(String table, String column) {
        return table + "::" + column;
    }
}

