package com.minidb.storage.disk;

import java.io.*;

public class DiskManager {

    private final String dir;

    public DiskManager(String dir) {
        this.dir = dir;
    }

    public synchronized void write(String file, byte[] data) {
        try (FileOutputStream fos = new FileOutputStream(dir + "/" + file, true)) {
            fos.write(data);
            fos.write('\n');
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized String readAll(String file) {
        try {
            return new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get(dir + "/" + file)));
        } catch (Exception e) {
            return "";
        }
    }
}