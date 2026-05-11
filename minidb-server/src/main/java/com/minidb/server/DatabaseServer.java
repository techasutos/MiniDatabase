package com.minidb.server;

import com.minidb.catalog.CatalogManager;
import com.minidb.catalog.CatalogStore;
import com.minidb.executor.Engine;
import com.minidb.sql.SQLParserService;
import com.minidb.sql.ast.Statement;
import com.minidb.transport.auth.InMemoryAuthService;
import com.minidb.transport.protocol.TextProtocolHandler;
import com.minidb.transport.session.SessionRegistry;
import com.minidb.transport.tcp.TcpTransportServer;

import java.nio.file.*;
import java.util.logging.Logger;

/**
 * MiniDB Database Server bootstrap.
 *
 * Wires together:
 *  - CatalogManager  (metadata)
 *  - Engine          (SQL execution)
 *  - AuthService     (authentication)
 *  - TextProtocolHandler (request/response framing)
 *  - TcpTransportServer  (network listener)
 *
 * Usage: java -cp ... com.minidb.server.DatabaseServer [port] [dataDir]
 */
public class DatabaseServer {

    private static final Logger LOG = Logger.getLogger(DatabaseServer.class.getName());
    private static final int DEFAULT_PORT = 5544;

    public static void main(String[] args) throws Exception {

        String envPort = System.getenv("MINIDB_PORT");
        int defaultPort = (envPort == null || envPort.isBlank()) ? DEFAULT_PORT : Integer.parseInt(envPort.trim());
        int    port    = args.length > 0 ? Integer.parseInt(args[0]) : defaultPort;
        String dataDirStr = args.length > 1 ? args[1] : "data";

        Path dataDir = Paths.get(dataDirStr);
        Files.createDirectories(dataDir);

        LOG.info("MiniDB starting — port=" + port + "  dataDir=" + dataDir.toAbsolutePath());

        // ── Component wiring ──────────────────────────────────────────────
        CatalogStore   catalogStore = new CatalogStore(dataDir.resolve("catalog.meta"));
        CatalogManager catalog      = new CatalogManager(catalogStore);
        Engine         engine       = new Engine(dataDir, catalog);
        engine.recover();
        SQLParserService parser     = new SQLParserService();
        SessionRegistry sessionRegistry = new SessionRegistry();

        InMemoryAuthService auth = new InMemoryAuthService();
        // Override default credentials via env vars for production deployments
        String adminUser = System.getenv("MINIDB_USER");
        String adminPass = System.getenv("MINIDB_PASSWORD");
        if (adminUser != null && adminPass != null) {
            auth.addUser(adminUser, adminPass);
        }

        // SQL executor: parse then execute, return result string
        java.util.function.Function<String, String> sqlExecutor = sql -> {
            try {
                Statement stmt   = parser.parse(sql);
                String    result = engine.execute(stmt);
                return result == null ? "" : result;
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        };

        java.util.function.Supplier<java.util.List<String>> databaseLister = catalog::listDatabaseNames;
        java.util.function.Function<String, java.util.List<String>> schemaLister = catalog::listSchemaNames;
        java.util.function.Function<String, java.util.List<String>> tableLister = schemaRef -> {
            String[] parts = schemaRef.split("\\.", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Use db.schema format");
            }
            return catalog.listTableNames(parts[0], parts[1]);
        };
        java.util.function.Function<String, java.util.List<String>> columnLister = tableRef -> {
            String[] parts = tableRef.split("\\.", 3);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Use db.schema.table format");
            }
            return catalog.listColumnDefinitions(parts[0], parts[1], parts[2]);
        };

        TextProtocolHandler protocol  = new TextProtocolHandler(auth, sqlExecutor, databaseLister, schemaLister, tableLister, columnLister, sessionRegistry);
        TcpTransportServer  transport = new TcpTransportServer(port, 50, protocol);

        // ── Shutdown hook ─────────────────────────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down MiniDB...");
            try { transport.stop(); } catch (Exception e) { LOG.warning("Shutdown error: " + e.getMessage()); }
        }, "minidb-shutdown"));

        transport.start();

        // Block main thread
        Thread.currentThread().join();
    }
}