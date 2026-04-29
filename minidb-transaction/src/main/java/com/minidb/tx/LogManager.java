package com.minidb.tx;

import java.io.*;

public class LogManager {

    private final String file;

    public LogManager(String file) {
        this.file = file;
    }

    public synchronized void log(String record) {
        try (FileWriter fw = new FileWriter(file, true)) {
            fw.write(record + "\n");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}