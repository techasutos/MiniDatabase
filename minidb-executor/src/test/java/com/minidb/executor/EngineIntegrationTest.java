package com.minidb.executor;

import com.minidb.catalog.CatalogManager;
import com.minidb.sql.SQLParserService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EngineIntegrationTest {

    @Test
    void executesDdlDmlAndSelectEndToEnd() throws Exception {
        Path dataDir = Files.createTempDirectory("minidb-engine-it-");

        CatalogManager catalog = new CatalogManager();
        Engine engine = new Engine(dataDir, catalog);
        SQLParserService parser = new SQLParserService();

        assertEquals("DATABASE CREATED", engine.execute(parser.parse("CREATE DATABASE testdb")));
        assertEquals("SCHEMA CREATED", engine.execute(parser.parse("CREATE SCHEMA testdb.analytics")));
        assertEquals("TABLE CREATED", engine.execute(parser.parse("CREATE TABLE testdb.analytics.users (id INT, name STRING)")));

        assertEquals("OK", engine.execute(parser.parse("INSERT INTO testdb.analytics.users VALUES (1, 'Alice')")));
        assertEquals("OK", engine.execute(parser.parse("INSERT INTO testdb.analytics.users VALUES (2, 'Bob')")));

        String result = engine.execute(parser.parse("SELECT * FROM testdb.analytics.users WHERE id >= 1"));

        assertTrue(result.contains("[1, Alice]"));
        assertTrue(result.contains("[2, Bob]"));
    }
}

