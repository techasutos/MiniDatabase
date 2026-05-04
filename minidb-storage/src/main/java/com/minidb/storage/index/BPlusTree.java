package com.minidb.storage.index;

import java.util.*;

/**
 * In-memory B+ Tree supporting integer keys → list of page offsets (row pointers).
 *
 * Order (degree) t means:
 *  - Internal nodes: t-1 .. 2t-1 keys, t .. 2t children
 *  - Leaf nodes:     t-1 .. 2t-1 key/value pairs
 *
 * Supports:
 *  - insert(key, rowPointer)
 *  - search(key) → list of row pointers
 *  - rangeSearch(fromKey, toKey) → sorted list of row pointers
 *  - delete(key, rowPointer)
 *
 * Row pointers are encoded as (pageId << 16 | slotId).
 */
public class BPlusTree {

    private static final int ORDER = 4; // t = 4

    // ── Node types ─────────────────────────────────────────────────────────

    private abstract static class Node {
        List<Comparable<Object>> keys = new ArrayList<>();
        boolean isLeaf;
        Node parent;
    }

    private static class InternalNode extends Node {
        List<Node> children = new ArrayList<>();

        InternalNode() { isLeaf = false; }
    }

    @SuppressWarnings("unchecked")
    private static class LeafNode extends Node {
        /** keys → list of row pointers (same key can have multiple rows) */
        List<List<Long>> values = new ArrayList<>();
        LeafNode next;  // linked list for range scans

        LeafNode() { isLeaf = true; }
    }

    // ── Tree state ─────────────────────────────────────────────────────────

    private Node root;
    private int size;

    public BPlusTree() {
        LeafNode leaf = new LeafNode();
        root = leaf;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /** Insert a key/rowPointer pair. Duplicate keys are allowed. */
    @SuppressWarnings("unchecked")
    public void insert(Comparable<?> key, long rowPointer) {
        LeafNode leaf = findLeaf((Comparable<Object>) key);
        insertIntoLeaf(leaf, (Comparable<Object>) key, rowPointer);

        if (leaf.keys.size() >= 2 * ORDER) {
            splitLeaf(leaf);
        }
        size++;
    }

    /** Return all row pointers for the given key. */
    @SuppressWarnings("unchecked")
    public List<Long> search(Comparable<?> key) {
        LeafNode leaf = findLeaf((Comparable<Object>) key);
        int idx = Collections.binarySearch(leaf.keys, (Comparable<Object>) key,
                Comparator.naturalOrder());
        if (idx < 0) return List.of();
        return Collections.unmodifiableList(leaf.values.get(idx));
    }

    /** Return all row pointers where fromKey ≤ key ≤ toKey (inclusive). */
    @SuppressWarnings("unchecked")
    public List<Long> rangeSearch(Comparable<?> from, Comparable<?> to) {
        List<Long> result = new ArrayList<>();
        LeafNode leaf = findLeaf((Comparable<Object>) from);

        while (leaf != null) {
            for (int i = 0; i < leaf.keys.size(); i++) {
                Comparable<Object> k = leaf.keys.get(i);
                if (k.compareTo((Comparable<Object>) to) > 0) return result;
                if (k.compareTo((Comparable<Object>) from) >= 0) {
                    result.addAll(leaf.values.get(i));
                }
            }
            leaf = leaf.next;
        }
        return result;
    }

    /** Remove a specific rowPointer for a key. */
    @SuppressWarnings("unchecked")
    public void delete(Comparable<?> key, long rowPointer) {
        LeafNode leaf = findLeaf((Comparable<Object>) key);
        int idx = Collections.binarySearch(leaf.keys, (Comparable<Object>) key,
                Comparator.naturalOrder());
        if (idx < 0) return;
        leaf.values.get(idx).remove(rowPointer);
        if (leaf.values.get(idx).isEmpty()) {
            leaf.keys.remove(idx);
            leaf.values.remove(idx);
            size = Math.max(0, size - 1);
        }
    }

    public int size() { return size; }

    // ── Internal ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private LeafNode findLeaf(Comparable<Object> key) {
        Node current = root;
        while (!current.isLeaf) {
            InternalNode internal = (InternalNode) current;
            int i = 0;
            while (i < internal.keys.size() && key.compareTo(internal.keys.get(i)) >= 0) i++;
            current = internal.children.get(i);
        }
        return (LeafNode) current;
    }

    @SuppressWarnings("unchecked")
    private void insertIntoLeaf(LeafNode leaf, Comparable<Object> key, long rowPointer) {
        int i = Collections.binarySearch(leaf.keys, key, Comparator.naturalOrder());
        if (i >= 0) {
            // Key exists — append rowPointer
            leaf.values.get(i).add(rowPointer);
        } else {
            int insertPos = -(i + 1);
            leaf.keys.add(insertPos, key);
            List<Long> ptrs = new ArrayList<>();
            ptrs.add(rowPointer);
            leaf.values.add(insertPos, ptrs);
        }
    }

    private void splitLeaf(LeafNode leaf) {
        int mid = ORDER;
        LeafNode newLeaf = new LeafNode();

        newLeaf.keys   = new ArrayList<>(leaf.keys.subList(mid, leaf.keys.size()));
        newLeaf.values = new ArrayList<>(leaf.values.subList(mid, leaf.values.size()));

        leaf.keys   = new ArrayList<>(leaf.keys.subList(0, mid));
        leaf.values = new ArrayList<>(leaf.values.subList(0, mid));

        newLeaf.next = leaf.next;
        leaf.next    = newLeaf;

        Comparable<Object> promotedKey = newLeaf.keys.get(0);
        insertIntoParent(leaf, promotedKey, newLeaf);
    }

    private void insertIntoParent(Node left, Comparable<Object> key, Node right) {
        if (left == root) {
            InternalNode newRoot = new InternalNode();
            newRoot.keys.add(key);
            newRoot.children.add(left);
            newRoot.children.add(right);
            left.parent  = newRoot;
            right.parent = newRoot;
            root = newRoot;
            return;
        }

        InternalNode parent = (InternalNode) left.parent;
        int idx = parent.children.indexOf(left);
        parent.keys.add(idx, key);
        parent.children.add(idx + 1, right);
        right.parent = parent;

        if (parent.keys.size() >= 2 * ORDER) {
            splitInternal(parent);
        }
    }

    private void splitInternal(InternalNode node) {
        int mid = ORDER - 1;
        Comparable<Object> pushedUpKey = node.keys.get(mid);

        InternalNode newNode = new InternalNode();
        newNode.keys     = new ArrayList<>(node.keys.subList(mid + 1, node.keys.size()));
        newNode.children = new ArrayList<>(node.children.subList(mid + 1, node.children.size()));

        node.keys     = new ArrayList<>(node.keys.subList(0, mid));
        node.children = new ArrayList<>(node.children.subList(0, mid + 1));

        for (Node child : newNode.children) child.parent = newNode;

        insertIntoParent(node, pushedUpKey, newNode);
    }
}

