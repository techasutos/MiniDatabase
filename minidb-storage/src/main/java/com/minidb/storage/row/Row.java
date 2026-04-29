package com.minidb.storage.row;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Row {

    private final List<Object> values;

    public Row(List<Object> values) {
        this.values = values;
    }

    public List<Object> getValues() {
        return values;
    }

    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        buffer.putInt(values.size());

        for (Object val : values) {
            if (val instanceof Integer i) {
                buffer.put((byte) 1);
                buffer.putInt(i);
            } else if (val instanceof String s) {
                buffer.put((byte) 2);
                byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
                buffer.putInt(bytes.length);
                buffer.put(bytes);
            }
        }

        buffer.flip();
        byte[] data = new byte[buffer.limit()];
        buffer.get(data);
        return data;
    }

    public static Row deserialize(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        int size = buffer.getInt();
        List<Object> values = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            byte type = buffer.get();

            if (type == 1) {
                values.add(buffer.getInt());
            } else if (type == 2) {
                int len = buffer.getInt();
                byte[] str = new byte[len];
                buffer.get(str);
                values.add(new String(str, StandardCharsets.UTF_8));
            }
        }

        return new Row(values);
    }
}