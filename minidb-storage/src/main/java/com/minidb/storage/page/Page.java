package com.minidb.storage.page;

public class Page {
    public static final int SIZE = 4096;

    private final byte[] data = new byte[SIZE];

    public byte[] data() {
        return data;
    }
}