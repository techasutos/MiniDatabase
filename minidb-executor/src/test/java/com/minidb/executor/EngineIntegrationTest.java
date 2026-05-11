package com.minidb.executor;

import com.minidb.catalog.CatalogManager;
import com.minidb.sql.SQLParserService;
import com.minidb.tx.WalManager;
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
        assertEquals("TABLE CREATED", engine.execute(parser.parse("CREATE TABLE testdb.analytics.orders (user_id INT, item STRING)")));

        assertEquals("OK", engine.execute(parser.parse("INSERT INTO testdb.analytics.users VALUES (1, 'Alice')")));
        assertEquals("OK", engine.execute(parser.parse("INSERT INTO testdb.analytics.users VALUES (2, 'Bob')")));
        assertEquals("OK", engine.execute(parser.parse("INSERT INTO testdb.analytics.orders VALUES (1, 'Book')")));
        assertEquals("OK", engine.execute(parser.parse("INSERT INTO testdb.analytics.orders VALUES (2, 'Pen')")));

        String result = engine.execute(parser.parse("SELECT * FROM testdb.analytics.users WHERE id >= 1"));

        assertTrue(result.contains("[1, Alice]"));
        assertTrue(result.contains("[2, Bob]"));

        String joinResult = engine.execute(parser.parse(
                "SELECT * FROM testdb.analytics.users INNER JOIN testdb.analytics.orders ON testdb.analytics.users.id = testdb.analytics.orders.user_id"
        ));

        assertTrue(joinResult.contains("[1, Alice, 1, Book]"));
        assertTrue(joinResult.contains("[2, Bob, 2, Pen]"));
    }

    @Test
    void recoversCommittedAndUndoesUncommittedChangesFromWal() throws Exception {
        Path dataDir = Files.createTempDirectory("minidb-wal-recovery-it-");

        CatalogManager catalog = new CatalogManager();
        SQLParserService parser = new SQLParserService();

        Engine writer = new Engine(dataDir, catalog);
        writer.execute(parser.parse("CREATE DATABASE testdb"));
        writer.execute(parser.parse("CREATE SCHEMA testdb.analytics"));
        writer.execute(parser.parse("CREATE TABLE testdb.analytics.users (id INT, name STRING)"));

        writer.execute(parser.parse("BEGIN"));
        writer.execute(parser.parse("INSERT INTO testdb.analytics.users VALUES (1, 'Committed')"));
        writer.execute(parser.parse("COMMIT"));

        writer.execute(parser.parse("BEGIN"));
        writer.execute(parser.parse("INSERT INTO testdb.analytics.users VALUES (2, 'Uncommitted')"));
        assertTrue(writer.getTransactionManager().getWalRecordsForRecovery().size() >= 4);
        // Simulate crash: no COMMIT/ROLLBACK for tx 2
        writer.close();

        Engine recovered = new Engine(dataDir, catalog);
        recovered.recover();

        String result = recovered.execute(parser.parse("SELECT * FROM testdb.analytics.users"));
        assertTrue(result.contains("[1, Committed]"));
        assertFalse(result.contains("Uncommitted"));
    }

    @Test
    void checkpointTruncationKeepsDatabaseStateAndPrunesOldWalHistory() throws Exception {
        Path dataDir = Files.createTempDirectory("minidb-checkpoint-it-");

        CatalogManager catalog = new CatalogManager();
        SQLParserService parser = new SQLParserService();
        Engine writer = new Engine(dataDir, catalog);

        writer.execute(parser.parse("CREATE DATABASE testdb"));
        writer.execute(parser.parse("CREATE SCHEMA testdb.analytics"));
        writer.execute(parser.parse("CREATE TABLE testdb.analytics.users (id INT, name STRING)"));

        writer.execute(parser.parse("BEGIN"));
        writer.execute(parser.parse("INSERT INTO testdb.analytics.users VALUES (1, 'A')"));
        writer.execute(parser.parse("COMMIT"));

        writer.execute(parser.parse("BEGIN"));
        writer.execute(parser.parse("INSERT INTO testdb.analytics.users VALUES (2, 'B')"));
        writer.execute(parser.parse("COMMIT"));

        int before = writer.getTransactionManager().getWalRecordsForRecovery().size();
        long checkpointLsn = writer.checkpointAndTruncateWal();

        var afterRecords = writer.getTransactionManager().getWalRecordsForRecovery();
        assertFalse(afterRecords.isEmpty());
        assertTrue(afterRecords.stream().allMatch(r -> r.lsn() >= checkpointLsn));
        assertTrue(afterRecords.stream().anyMatch(r -> r.type() == WalManager.RecordType.CHECKPOINT));
        assertTrue(afterRecords.size() <= before + 1);

        writer.close();

        Engine recovered = new Engine(dataDir, catalog);
        recovered.recover();
        String result = recovered.execute(parser.parse("SELECT * FROM testdb.analytics.users"));
        assertTrue(result.contains("[1, A]"));
        assertTrue(result.contains("[2, B]"));
    }

    @Test
    void recoveryIgnoresStaleCheckpointMetadataAndFallsBackToWalCheckpoint() throws Exception {
        Path dataDir = Files.createTempDirectory("minidb-checkpoint-meta-fallback-it-");

        CatalogManager catalog = new CatalogManager();
        SQLParserService parser = new SQLParserService();
        Engine writer = new Engine(dataDir, catalog);

        writer.execute(parser.parse("CREATE DATABASE testdb"));
        writer.execute(parser.parse("CREATE SCHEMA testdb.analytics"));
        writer.execute(parser.parse("CREATE TABLE testdb.analytics.users (id INT, name STRING)"));

        writer.execute(parser.parse("BEGIN"));
        writer.execute(parser.parse("INSERT INTO testdb.analytics.users VALUES (1, 'A')"));
        writer.execute(parser.parse("COMMIT"));

        writer.execute(parser.parse("BEGIN"));
        writer.execute(parser.parse("INSERT INTO testdb.analytics.users VALUES (2, 'B')"));
        writer.execute(parser.parse("COMMIT"));

        writer.checkpointAndTruncateWal();
        Files.writeString(dataDir.resolve("minidb.wal.checkpoint"), "999999999");
        writer.close();

        Engine recovered = new Engine(dataDir, catalog);
        recovered.recover();

        String result = recovered.execute(parser.parse("SELECT * FROM testdb.analytics.users"));
        assertTrue(result.contains("[1, A]"));
        assertTrue(result.contains("[2, B]"));
    }
}

