package com.minidb.tx;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private static final long DEFAULT_SEGMENT_SIZE_BYTES = 4L * 1024L * 1024L;

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
    private final Path segmentDir;
    private final Path checkpointMetaPath;
    private FileChannel channel;
    private final AtomicLong lsnCounter;
    private final long segmentSizeBytes;
    private volatile long flushedLsn = 0;
    private volatile long pendingCheckpointLsn = 0;

    // ── Constructor ────────────────────────────────────────────────────────

    public WalManager(Path walPath) throws IOException {
        this(walPath, DEFAULT_SEGMENT_SIZE_BYTES);
    }

    public WalManager(Path walPath, long segmentSizeBytes) throws IOException {
        this.walPath = walPath;
        this.segmentDir = walPath.resolveSibling(walPath.getFileName().toString() + ".segments");
        this.checkpointMetaPath = walPath.resolveSibling(walPath.getFileName().toString() + ".checkpoint");
        this.segmentSizeBytes = segmentSizeBytes;
        Files.createDirectories(this.segmentDir);
        this.channel = openActiveChannel();

        long maxLsn = findMaxLsn();
        this.lsnCounter = new AtomicLong(Math.max(1L, maxLsn + 1L));
        this.flushedLsn = Math.max(0L, maxLsn);
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

        // Prefix: lsn(8) + txId(8) + type(1) + pageId(4) + offset(4) + beforeLen(4) = 29 bytes
        // Then: beforeImage + afterLen(4) + afterImage + crc(4)
        int totalSize = 29 + beforeSafe.length + 4 + afterSafe.length + 4;
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
        writeFully(channel, buf, channel.size());
        rotateIfNeeded(lsn);

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
        if (pendingCheckpointLsn > 0 && upToLsn >= pendingCheckpointLsn) {
            writeCheckpointMetadata(pendingCheckpointLsn);
            pendingCheckpointLsn = 0;
        }
    }

    /**
     * Write a simple text checkpoint marker.
     */
    public synchronized long checkpoint(long txId) throws IOException {
        long lsn = append(txId, RecordType.CHECKPOINT, -1, -1, null, null);
        pendingCheckpointLsn = lsn;
        return lsn;
    }

    /**
     * Remove WAL history older than keepFromLsn.
     *
     * Segment files are deleted only when all records in that segment are older than keepFromLsn.
     * The active WAL file is compacted exactly to records with lsn >= keepFromLsn.
     */
    public synchronized void truncateBeforeLsn(long keepFromLsn) throws IOException {
        if (keepFromLsn <= 0) {
            return;
        }

        for (Path segment : listSegmentFiles()) {
            List<WalRecord> records = parseRecords(segment);
            boolean allOld = !records.isEmpty() && records.stream().allMatch(r -> r.lsn() < keepFromLsn);
            if (allOld) {
                Files.deleteIfExists(segment);
            }
        }

        List<WalRecord> activeRecords = parseRecords(walPath);
        List<WalRecord> kept = new ArrayList<>();
        for (WalRecord record : activeRecords) {
            if (record.lsn() >= keepFromLsn) {
                kept.add(record);
            }
        }
        if (kept.size() != activeRecords.size()) {
            rewriteActiveWal(kept);
        }
    }

    /**
     * Read all WAL records from the beginning (used during crash recovery).
     */
    public synchronized java.util.List<WalRecord> readAll() throws IOException {
        List<WalRecord> records = new ArrayList<>();
        for (Path segment : listSegmentFiles()) {
            records.addAll(parseRecords(segment));
        }
        records.addAll(parseRecords(walPath));
        records.sort(Comparator.comparingLong(WalRecord::lsn));
        return records;
    }

    public synchronized long latestCheckpointLsn() throws IOException {
        long metadataLsn = readCheckpointMetadata();
        if (metadataLsn > 0) {
            return metadataLsn;
        }
        List<WalRecord> records = readAll();
        for (int i = records.size() - 1; i >= 0; i--) {
            WalRecord record = records.get(i);
            if (record.type() == RecordType.CHECKPOINT) {
                return record.lsn();
            }
        }
        return 0L;
    }

    @Override
    public synchronized void close() throws IOException {
        channel.force(true);
        channel.close();
    }

    public long getFlushedLsn() { return flushedLsn; }
    public Path getWalPath()    { return walPath; }
    public Path getCheckpointMetaPath() { return checkpointMetaPath; }

    private FileChannel openActiveChannel() throws IOException {
        return FileChannel.open(
                walPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
        );
    }

    private void rotateIfNeeded(long lastLsnInSegment) throws IOException {
        if (segmentSizeBytes <= 0 || channel.size() < segmentSizeBytes) {
            return;
        }
        channel.force(true);
        channel.close();

        Path rotated = segmentDir.resolve(String.format("segment-%020d-%d.wal", lastLsnInSegment, Instant.now().toEpochMilli()));
        Files.move(walPath, rotated, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        channel = openActiveChannel();
        LOG.info("WAL segment rotated: " + rotated.getFileName());
    }

    private List<Path> listSegmentFiles() throws IOException {
        if (!Files.exists(segmentDir)) {
            return List.of();
        }
        List<Path> segments = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(segmentDir, "*.wal")) {
            for (Path p : stream) {
                segments.add(p);
            }
        }
        segments.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return segments;
    }

    private long findMaxLsn() throws IOException {
        long max = 0L;
        for (WalRecord record : readAll()) {
            max = Math.max(max, record.lsn());
        }
        return max;
    }

    private List<WalRecord> parseRecords(Path file) throws IOException {
        if (!Files.exists(file) || Files.size(file) == 0) {
            return List.of();
        }

        List<WalRecord> records = new ArrayList<>();
        try (FileChannel readChannel = FileChannel.open(file, StandardOpenOption.READ)) {
            long position = 0;
            long fileSize = readChannel.size();

            while (position < fileSize) {
                if (fileSize - position < 37) {
                    break;
                }

                ByteBuffer prefix = ByteBuffer.allocate(29);
                if (!readFully(readChannel, prefix, position)) {
                    break;
                }
                prefix.flip();

                long lsn = prefix.getLong();
                long txId = prefix.getLong();
                byte typeCode = prefix.get();
                int pageId = prefix.getInt();
                int offset = prefix.getInt();
                int beforeLen = prefix.getInt();

                if (beforeLen < 0) {
                    throw new IOException("Corrupted WAL: negative before-image length at position " + position + " in " + file);
                }

                ByteBuffer beforeBuf = ByteBuffer.allocate(beforeLen);
                if (!readFully(readChannel, beforeBuf, position + 29L)) {
                    break;
                }
                byte[] before = beforeBuf.array();

                ByteBuffer afterLenBuf = ByteBuffer.allocate(4);
                if (!readFully(readChannel, afterLenBuf, position + 29L + beforeLen)) {
                    break;
                }
                afterLenBuf.flip();
                int afterLen = afterLenBuf.getInt();
                if (afterLen < 0) {
                    throw new IOException("Corrupted WAL: negative after-image length at position " + position + " in " + file);
                }

                ByteBuffer afterBuf = ByteBuffer.allocate(afterLen);
                if (!readFully(readChannel, afterBuf, position + 33L + beforeLen)) {
                    break;
                }
                byte[] after = afterBuf.array();

                ByteBuffer crcBuf = ByteBuffer.allocate(4);
                if (!readFully(readChannel, crcBuf, position + 33L + beforeLen + afterLen)) {
                    break;
                }
                crcBuf.flip();
                int expectedCrc = crcBuf.getInt();

                int crcInputSize = 29 + beforeLen + 4 + afterLen;
                ByteBuffer crcInput = ByteBuffer.allocate(crcInputSize);
                crcInput.putLong(lsn);
                crcInput.putLong(txId);
                crcInput.put(typeCode);
                crcInput.putInt(pageId);
                crcInput.putInt(offset);
                crcInput.putInt(beforeLen);
                crcInput.put(before);
                crcInput.putInt(afterLen);
                crcInput.put(after);

                java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
                crc32.update(crcInput.array(), 0, crcInput.position());
                int actualCrc = (int) crc32.getValue();
                if (expectedCrc != actualCrc) {
                    throw new IOException("Corrupted WAL: CRC mismatch at position " + position + " in " + file);
                }

                long recordSize = 29L + beforeLen + 4L + afterLen + 4L;
                position += recordSize;
                records.add(new WalRecord(lsn, txId, RecordType.of(typeCode), pageId, offset, before, after));
            }
        }
        return records;
    }

    private static boolean readFully(FileChannel ch, ByteBuffer dst, long position) throws IOException {
        while (dst.hasRemaining()) {
            int read = ch.read(dst, position);
            if (read < 0) {
                return false;
            }
            if (read == 0) {
                return false;
            }
            position += read;
        }
        return true;
    }

    private static void writeFully(FileChannel ch, ByteBuffer src, long position) throws IOException {
        while (src.hasRemaining()) {
            int written = ch.write(src, position);
            if (written == 0) {
                throw new EOFException("Unable to make forward progress while writing WAL");
            }
            position += written;
        }
    }

    private void writeCheckpointMetadata(long lsn) throws IOException {
        if (lsn <= 0) {
            return;
        }
        Path tmp = checkpointMetaPath.resolveSibling(checkpointMetaPath.getFileName().toString() + ".tmp");
        Files.writeString(tmp, Long.toString(lsn), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tmp, checkpointMetaPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tmp, checkpointMetaPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private long readCheckpointMetadata() {
        if (!Files.exists(checkpointMetaPath)) {
            return 0L;
        }
        try {
            String raw = Files.readString(checkpointMetaPath).trim();
            if (raw.isEmpty()) {
                return 0L;
            }
            long value = Long.parseLong(raw);
            return value > 0 ? value : 0L;
        } catch (Exception e) {
            LOG.warning("Failed to parse checkpoint metadata at " + checkpointMetaPath + ": " + e.getMessage());
            return 0L;
        }
    }

    private void rewriteActiveWal(List<WalRecord> records) throws IOException {
        channel.force(true);
        channel.close();

        channel = FileChannel.open(
                walPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        long position = 0L;
        for (WalRecord record : records) {
            ByteBuffer encoded = encodeRecord(record);
            writeFully(channel, encoded, position);
            position += encoded.limit();
        }
        channel.force(true);
    }

    private static ByteBuffer encodeRecord(WalRecord record) {
        byte[] beforeSafe = record.before() == null ? new byte[0] : record.before();
        byte[] afterSafe = record.after() == null ? new byte[0] : record.after();

        int totalSize = 29 + beforeSafe.length + 4 + afterSafe.length + 4;
        ByteBuffer buf = ByteBuffer.allocate(totalSize);
        buf.putLong(record.lsn());
        buf.putLong(record.txId());
        buf.put(record.type().code);
        buf.putInt(record.pageId());
        buf.putInt(record.offset());
        buf.putInt(beforeSafe.length);
        buf.put(beforeSafe);
        buf.putInt(afterSafe.length);
        buf.put(afterSafe);

        java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
        crc32.update(buf.array(), 0, buf.position());
        buf.putInt((int) crc32.getValue());
        buf.flip();
        return buf;
    }
}

