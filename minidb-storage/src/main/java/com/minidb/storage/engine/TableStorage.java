package com.minidb.storage.engine;

import com.minidb.storage.row.Row;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class TableStorage {

    private final Path file;

    public TableStorage(Path file) {
        this.file = file;
    }

    public synchronized void insert(Row row) {
        try (OutputStream os = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            byte[] data = row.serialize();

            DataOutputStream dos = new DataOutputStream(os);
            dos.writeInt(data.length);
            dos.write(data);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized List<Row> scan() {
        List<Row> rows = new ArrayList<>();

        try (InputStream is = Files.newInputStream(file)) {

            DataInputStream dis = new DataInputStream(is);

            while (dis.available() > 0) {
                int len = dis.readInt();
                byte[] data = new byte[len];
                dis.readFully(data);

                rows.add(Row.deserialize(data));
            }

        } catch (IOException ignored) {}

        return rows;
    }
}