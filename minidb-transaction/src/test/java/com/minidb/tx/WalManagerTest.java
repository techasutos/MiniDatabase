package com.minidb.tx;

import org.junit.jupiter.api.Test;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalManagerTest {

    @Test
    void latestCheckpointLsnUsesPersistedMetadataAcrossRestart() throws Exception {
        Path dir = Files.createTempDirectory("minidb-wal-checkpoint-meta-");
        Path walPath = dir.resolve("minidb.wal");

        WalManager wal = new WalManager(walPath, 1024);
        long checkpointLsn = wal.checkpoint(0L);
        wal.flush(checkpointLsn);
        Path checkpointMetaPath = wal.getCheckpointMetaPath();
        wal.close();

        assertTrue(Files.exists(checkpointMetaPath));

        WalManager reopened = new WalManager(walPath, 1024);
        try {
            assertEquals(checkpointLsn, reopened.latestCheckpointLsn());
        } finally {
            reopened.close();
        }
    }

    @Test
    void latestCheckpointLsnFallsBackToWalScanWhenMetadataIsCorrupt() throws Exception {
        Path dir = Files.createTempDirectory("minidb-wal-checkpoint-fallback-");
        Path walPath = dir.resolve("minidb.wal");

        WalManager wal = new WalManager(walPath, 1024);
        long checkpointLsn = wal.checkpoint(0L);
        wal.flush(checkpointLsn);
        Files.writeString(wal.getCheckpointMetaPath(), "not-a-number");
        wal.close();

        WalManager reopened = new WalManager(walPath, 1024);
        try {
            assertEquals(checkpointLsn, reopened.latestCheckpointLsn());
        } finally {
            reopened.close();
        }
    }

    @Test
    void readAllThrowsWhenRecordCrcIsCorrupted() throws Exception {
        Path dir = Files.createTempDirectory("minidb-wal-crc-test-");
        Path walPath = dir.resolve("minidb.wal");
        WalManager wal = new WalManager(walPath, 1024);

        long lsn = wal.append(1L, WalManager.RecordType.UPDATE, 1, 0, new byte[]{1, 2}, new byte[]{3, 4});
        wal.flush(lsn);
        wal.close();

        try (RandomAccessFile raf = new RandomAccessFile(walPath.toFile(), "rw")) {
            // Flip one byte in the before-image payload (record starts after 29-byte header).
            raf.seek(29L);
            int original = raf.read();
            raf.seek(29L);
            raf.write(original ^ 0x01);
        }

        assertThrows(java.io.IOException.class, () -> new WalManager(walPath, 1024));
    }

    @Test
    void truncateBeforeLsnKeepsCheckpointAndNewerRecords() throws Exception {
        Path dir = Files.createTempDirectory("minidb-wal-test-");
        WalManager wal = new WalManager(dir.resolve("minidb.wal"), 512);

        wal.append(1L, WalManager.RecordType.BEGIN, -1, -1, null, null);
        wal.append(1L, WalManager.RecordType.UPDATE, 1, 0, new byte[]{1}, new byte[]{2});
        long commitLsn = wal.append(1L, WalManager.RecordType.COMMIT, -1, -1, null, null);
        wal.flush(commitLsn);

        long checkpointLsn = wal.checkpoint(0L);
        wal.flush(checkpointLsn);

        wal.append(2L, WalManager.RecordType.BEGIN, -1, -1, null, null);
        long afterCheckpointLsn = wal.append(2L, WalManager.RecordType.ABORT, -1, -1, null, null);
        wal.flush(afterCheckpointLsn);

        wal.truncateBeforeLsn(checkpointLsn);

        var records = wal.readAll();
        assertFalse(records.isEmpty());
        assertTrue(records.stream().allMatch(r -> r.lsn() >= checkpointLsn));
        assertTrue(records.stream().anyMatch(r -> r.type() == WalManager.RecordType.CHECKPOINT));
        assertTrue(records.stream().anyMatch(r -> r.lsn() == afterCheckpointLsn));

        wal.close();
    }
}

