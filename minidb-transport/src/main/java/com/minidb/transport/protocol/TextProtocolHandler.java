package com.minidb.transport.protocol;

import com.minidb.transport.auth.AuthService;

import java.io.*;
import java.net.Socket;
import java.util.function.Function;
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

    private final AuthService              authService;
    private final Function<String, String> sqlExecutor;  // sql → result string

    public TextProtocolHandler(AuthService authService,
                               Function<String, String> sqlExecutor) {
        this.authService = authService;
        this.sqlExecutor = sqlExecutor;
    }

    @Override
    public void handle(Socket socket) {
        String remote = socket.getRemoteSocketAddress().toString();
        LOG.info("Connection from " + remote);

        try (
            BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter    out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true)
        ) {
            // ── Greeting ──────────────────────────────────────────────────────
            out.println("MINIDB 1.0");

            // ── Auth ──────────────────────────────────────────────────────────
            out.println("AUTH");
            String username = in.readLine();
            String password = in.readLine();

            if (username == null || password == null || !authService.authenticate(username, password)) {
                out.println("ERROR: Authentication failed");
                LOG.warning("Auth failed for user: " + username + " from " + remote);
                return;
            }
            out.println("OK");
            LOG.info("Authenticated: " + username + " from " + remote);

            // ── Query Loop ────────────────────────────────────────────────────
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if ("QUIT".equalsIgnoreCase(line)) break;

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
            }

        } catch (IOException e) {
            LOG.fine("Connection closed: " + remote + " (" + e.getMessage() + ")");
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private String sanitize(String msg) {
        return msg == null ? "Unknown error" : msg.replace("\n", " ").replace("\r", "");
    }
}

