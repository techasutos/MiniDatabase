package com.minidb.catalog;

import com.minidb.catalog.model.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class CatalogStore {

    private final Path file;

    public CatalogStore(Path file) {
        this.file = file;
    }

    public void save(Map<String, Database> dbs) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(
                Files.newOutputStream(file))) {
            oos.writeObject(dbs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Database> load() {
        if (!Files.exists(file)) return new HashMap<>();

        try (ObjectInputStream ois = new ObjectInputStream(
                Files.newInputStream(file))) {
            return (Map<String, Database>) ois.readObject();
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}