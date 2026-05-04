package com.minidb.tx;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Write-Ahead Log (WAL) Manager.
 *
 * WAL guarantees durability: before any page is modified, a log record
 * describing the change is first written and flushed to disk.
 *
 * Log record format (binary):
 * ┌──────────────────────────────────────────────────────┐
 * │ LSN (8 bytes) │ TxId (8 bytes) │ Type (1 byte)      │
 * │ PageId (4 bytes) │ Offset (4 bytes) │ Length (4 bytes)│
 * │ Before-Image (variable) │ After-Image (variable)     │
 * │ CRC32 (4 bytes)                                      │
 * └──────────────────────────────────────────────────────┘
 *
 * Supports REDO and UNDO recovery modes.
 */
public class WalManager implements Closeable {

    private static final Logger LOG = Logger.getLogger(WalManager.class.getName());

    public enum RecordType {
        BEGIN   ((byte) 1),
        INSERT  ((byte) 2),
        UPDATE  ((byte) 3),
        DELETE  ((byte) 4),
        COMMIT  ((byte) 5),
        ABORT   ((byte) 6),
        CHECKPOINT((byte) 7);

        public final byte code;
        RecordType(byte code) { this.code = code; }

        public static RecordType of(byte code) {
            for (RecordType t : values()) if (t.code == code) return t;
            throw new IllegalArgumentException("Unknown WAL record type: " + code);
        }
    }

    public record WalRecord(
            long lsn,
            long txId,
            RecordType type,
            int pageId,
            int offset,
            byte[] before,
            byte[] after
    ) {}

    // ── State ──────────────────────────────────────────────────────────────

    private final Path walPath;
    private final FileChannel channel;
    private final AtomicLong lsnCounter;
    private volatile long flushedLsn = 0;

    // ── Constructor ────────────────────────────────────────────────────────

    public WalManager(Path walPath) throws IOException {
        this.walPath = walPath;
        this.channel = FileChannel.open(
                walPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
        );
        // Resume LSN from file size
        long size = channel.size();
        this.lsnCounter = new AtomicLong(size == 0 ? 1L : size);
        this.flushedLsn = lsnCounter.get();
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Append a WAL record. Returns the assigned LSN.
     */
    public synchronized long append(long txId,
                                    RecordType type,
                                    int pageId,
                                    int offset,
                                    byte[] before,
                                    byte[] after) throws IOException {

        long lsn = lsnCounter.getAndIncrement();

        byte[] beforeSafe = before == null ? new byte[0] : before;
        byte[] afterSafe  = after  == null ? new byte[0] : after;

        // Header: lsn(8) + txId(8) + type(1) + pageId(4) + offset(4)
        //       + beforeLen(4) + afterLen(4) = 33 bytes
        int totalSize = 33 + beforeSafe.length + afterSafe.length + 4; // +4 for CRC
        ByteBuffer buf = ByteBuffer.allocate(totalSize);

        buf.putLong(lsn);
        buf.putLong(txId);
        buf.put(type.code);
        buf.putInt(pageId);
        buf.putInt(offset);
        buf.putInt(beforeSafe.length);
        buf.put(beforeSafe);
        buf.putInt(afterSafe.length);
        buf.put(afterSafe);

        // CRC32 over all preceding bytes
        java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
        crc32.update(buf.array(), 0, buf.position());
        buf.putInt((int) crc32.getValue());

        buf.flip();
        channel.write(buf, channel.size());

        return lsn;
    }

    /**
     * Force-flush WAL to disk up to (and including) the given LSN.
     * Called before a transaction COMMIT to guarantee durability.
     */
    public synchronized void flush(long upToLsn) throws IOException {
        if (upToLsn > flushedLsn) {
            channel.force(true);
            flushedLsn = upToLsn;
        }
    }

    /**
     * Write a simple text checkpoint marker.
     */
    public synchronized long checkpoint(long txId) throws IOException {
        return append(txId, RecordType.CHECKPOINT, -1, -1, null, null);
    }

    /**
     * Read all WAL records from the beginning (used during crash recovery).
     */
    public java.util.List<WalRecord> readAll() throws IOException {
        java.util.List<WalRecord> records = new java.util.ArrayList<>();
        long position = 0;
        long fileSize = channel.size();

        while (position < fileSize) {
            // Need at least 33+4 = 37 bytes for a minimal record
            if (fileSize - position < 37) break;

            ByteBuffer header = ByteBuffer.allocate(33);
            channel.read(header, position);
            header.flip();

            long lsn     = header.getLong();
            long txId    = header.getLong();
            byte typeCode= header.get();
            int  pageId  = header.getInt();
            int  offset  = header.getInt();
            int  beforeLen = header.getInt();

            ByteBuffer beforeBuf = ByteBuffer.allocate(beforeLen);
            channel.read(beforeBuf, position + 33);
            byte[] before = beforeBuf.array();

            ByteBuffer afterLenBuf = ByteBuffer.allocate(4);
            channel.read(afterLenBuf, position + 33 + beforeLen);
            afterLenBuf.flip();
            int afterLen = afterLenBuf.getInt();

            ByteBuffer afterBuf = ByteBuffer.allocate(afterLen);
            channel.read(afterBuf, position + 37 + beforeLen);
            byte[] after = afterBuf.array();

            // skip CRC for now (production would validate)
            long recordSize = 33L + beforeLen + 4 + afterLen + 4;
            position += recordSize;

            records.add(new WalRecord(lsn, txId, RecordType.of(typeCode),
                    pageId, offset, before, after));
        }

        return records;
    }

    @Override
    public synchronized void close() throws IOException {
        channel.force(true);
        channel.close();
    }

    public long getFlushedLsn() { return flushedLsn; }
    public Path getWalPath()    { return walPath; }
}

