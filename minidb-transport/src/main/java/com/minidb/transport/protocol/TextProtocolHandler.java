package com.minidb.transport.protocol;

import com.minidb.transport.auth.AuthService;
import com.minidb.transport.session.SessionRegistry;

import java.io.*;
import java.net.Socket;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.List;
import java.util.logging.Logger;

/**
 * MiniDB text protocol handler.
 *
 * Handshake:
 *  S→C: "MINIDB 1.0\n"
 *  S→C: "AUTH\n"
 *  C→S: "<username>\n"
 *  C→S: "<password>\n"
 *  S→C: "OK\n" | "ERROR: Authentication failed\n"
 *
 * Query loop:
 *  C→S: "<sql>\n"
 *  S→C: "<result lines>\n"
 *  S→C: "END\n"       ← marks end of result set
 *
 * Quit:
 *  C→S: "QUIT\n"  → server closes
 */
public class TextProtocolHandler implements ProtocolHandler {

    private static final Logger LOG = Logger.getLogger(TextProtocolHandler.class.getName());
    private static final String GREETING = "MINIDB 1.0";

    private final AuthService              authService;
    private final Function<String, String> sqlExecutor;  // sql → result string
    private final Supplier<List<String>> databases;
    private final Function<String, List<String>> schemas;
    private final Function<String, List<String>> tables;
    private final Function<String, List<String>> columns;
    private final SessionRegistry sessionRegistry;

    public TextProtocolHandler(AuthService authService,
                               Function<String, String> sqlExecutor,
                               Supplier<List<String>> databases,
                               Function<String, List<String>> schemas,
                               Function<String, List<String>> tables,
                               Function<String, List<String>> columns,
                               SessionRegistry sessionRegistry) {
        this.authService = authService;
        this.sqlExecutor = sqlExecutor;
        this.databases = databases;
        this.schemas = schemas;
        this.tables = tables;
        this.columns = columns;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void handle(Socket socket) {
        String remote = socket.getRemoteSocketAddress().toString();
        LOG.info("Connection from " + remote);
        long sessionId = sessionRegistry.open(remote);

        try (
            BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter    out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true)
        ) {
            // ── Greeting ──────────────────────────────────────────────────────
            out.println(GREETING);

            // ── Auth ──────────────────────────────────────────────────────────
            out.println("AUTH");
            String username = in.readLine();
            String password = in.readLine();

            if (username == null || password == null || !authService.authenticate(username, password)) {
                out.println("ERROR: Authentication failed");
                LOG.warning("Auth failed for user: " + username + " from " + remote);
                return;
            }
            sessionRegistry.markAuthenticated(sessionId, username);
            out.println("OK");
            LOG.info("Authenticated: " + username + " from " + remote);

            // ── Query Loop ────────────────────────────────────────────────────
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if ("QUIT".equalsIgnoreCase(line)) break;

                sessionRegistry.markExecuting(sessionId, line);
                try {
                    boolean handled;
                    try {
                        handled = handleProtocolCommand(line, out);
                    } catch (Exception e) {
                        out.println("ERROR: " + sanitize(e.getMessage()));
                        out.println("END");
                        continue;
                    }

                    if (handled) {
                        continue;
                    }

                    try {
                        String result = sqlExecutor.apply(line);
                        // Write each line of result, then "END"
                        if (result != null && !result.isEmpty()) {
                            for (String resultLine : result.split("\n", -1)) {
                                out.println(resultLine);
                            }
                        }
                    } catch (Exception e) {
                        out.println("ERROR: " + sanitize(e.getMessage()));
                    }
                    out.println("END");
                } finally {
                    sessionRegistry.markIdle(sessionId);
                }
            }

        } catch (IOException e) {
            LOG.fine("Connection closed: " + remote + " (" + e.getMessage() + ")");
        } finally {
            sessionRegistry.close(sessionId);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private String sanitize(String msg) {
        return msg == null ? "Unknown error" : msg.replace("\n", " ").replace("\r", "");
    }

    private boolean handleProtocolCommand(String line, PrintWriter out) {
        if ("PING".equalsIgnoreCase(line)) {
            out.println("PONG");
            out.println("END");
            return true;
        }
        if ("CAPABILITIES".equalsIgnoreCase(line) || "SHOW CAPABILITIES".equalsIgnoreCase(line)) {
            out.println("PROTOCOL=minidb-text/1");
            out.println("AUTH=plain");
            out.println("FRAMING=line+END");
            out.println("FEATURES=sql_text,transactions,checkpoint,recovery,jdbc_basic,metadata,sessions,columns");
            out.println("END");
            return true;
        }
        if (line.equalsIgnoreCase("SHOW DATABASES")) {
            writeLines(databases.get(), "DATABASE ", out);
            return true;
        }
        if (line.toUpperCase().startsWith("SHOW SCHEMAS")) {
            String dbName = parseTrailingName(line, "SHOW SCHEMAS IN");
            writeLines(schemas.apply(dbName), "SCHEMA ", out);
            return true;
        }
        if (line.toUpperCase().startsWith("SHOW TABLES")) {
            String schemaRef = parseTrailingName(line, "SHOW TABLES IN");
            String[] parts = schemaRef.split("\\.", 2);
            if (parts.length != 2) {
                out.println("ERROR: Use SHOW TABLES IN db.schema");
                out.println("END");
                return true;
            }
            writeLines(tables.apply(schemaRef), "TABLE ", out);
            return true;
        }
        if (line.toUpperCase().startsWith("SHOW COLUMNS")) {
            String tableRef = parseTrailingName(line, "SHOW COLUMNS IN");
            String[] parts = tableRef.split("\\.", 3);
            if (parts.length != 3) {
                out.println("ERROR: Use SHOW COLUMNS IN db.schema.table");
                out.println("END");
                return true;
            }
            writeLines(columns.apply(tableRef), "COLUMN ", out);
            return true;
        }
        if (line.equalsIgnoreCase("SHOW SESSIONS")) {
            for (SessionRegistry.SessionInfo info : sessionRegistry.snapshot()) {
                out.println(formatSession(info));
            }
            out.println("END");
            return true;
        }
        return false;
    }

    private static void writeLines(List<String> values, String prefix, PrintWriter out) {
        for (String value : values) {
            out.println(prefix + value);
        }
        out.println("END");
    }

    private static String parseTrailingName(String line, String prefix) {
        String remainder = line.substring(prefix.length()).trim();
        if (remainder.isEmpty()) {
            throw new IllegalArgumentException("Missing identifier after " + prefix);
        }
        return remainder;
    }

    private static String formatSession(SessionRegistry.SessionInfo info) {
        return "SESSION "
                + "sessionId=" + info.sessionId() + ';'
                + "user=" + safe(info.username()) + ';'
                + "state=" + info.state() + ';'
                + "remote=" + safe(info.remoteAddress()) + ';'
                + "connectedAt=" + SessionRegistry.formatIso(info.connectedAtEpochMs()) + ';'
                + "lastActivity=" + SessionRegistry.formatIso(info.lastActivityEpochMs()) + ';'
                + "lastCommand=" + safe(info.lastCommand());
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace("\n", " ").replace("\r", " ");
    }
}

