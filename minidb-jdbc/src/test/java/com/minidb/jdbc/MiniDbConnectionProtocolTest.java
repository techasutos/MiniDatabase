package com.minidb.jdbc;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniDbConnectionProtocolTest {

    @Test
    void discoversCapabilitiesWhenServerSupportsCommand() throws Exception {
        FakeServer fakeServer = new FakeServer(true);
        fakeServer.start();
        try {
            MiniDbConnection conn = new MiniDbConnection("127.0.0.1", fakeServer.port(), "admin", "minidb");
            try {
                Map<String, String> caps = conn.getServerCapabilitiesSnapshot();
                assertEquals("minidb-text/1", caps.get("PROTOCOL"));
                assertTrue(caps.containsKey("FEATURES"));
            } finally {
                conn.close();
            }
        } finally {
            fakeServer.stop();
        }
    }

    @Test
    void keepsConnectionUsableWhenCapabilitiesCommandIsUnsupported() throws Exception {
        FakeServer fakeServer = new FakeServer(false);
        fakeServer.start();
        try {
            MiniDbConnection conn = new MiniDbConnection("127.0.0.1", fakeServer.port(), "admin", "minidb");
            try {
                assertTrue(conn.getServerCapabilitiesSnapshot().isEmpty());
                assertFalse(conn.isClosed());
            } finally {
                conn.close();
            }
        } finally {
            fakeServer.stop();
        }
    }

    private static final class FakeServer {
        private final boolean supportsCapabilities;
        private final ServerSocket serverSocket;
        private final Thread thread;
        private final CountDownLatch started = new CountDownLatch(1);

        private FakeServer(boolean supportsCapabilities) throws Exception {
            this.supportsCapabilities = supportsCapabilities;
            this.serverSocket = new ServerSocket(0);
            this.thread = new Thread(this::serve, "minidb-jdbc-fake-server");
        }

        private void start() throws Exception {
            thread.start();
            if (!started.await(3, TimeUnit.SECONDS)) {
                throw new SQLException("Fake server did not start in time");
            }
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private void stop() throws Exception {
            try {
                serverSocket.close();
            } finally {
                thread.join(3000);
            }
        }

        private void serve() {
            started.countDown();
            try (Socket client = serverSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                 PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(client.getOutputStream())), true)) {

                out.println("MINIDB 1.0");
                out.println("AUTH");

                String user = in.readLine();
                String pass = in.readLine();
                if (!"admin".equals(user) || !"minidb".equals(pass)) {
                    out.println("ERROR: Authentication failed");
                    return;
                }
                out.println("OK");

                String line;
                while ((line = in.readLine()) != null) {
                    if ("QUIT".equalsIgnoreCase(line)) {
                        break;
                    }
                    if ("CAPABILITIES".equalsIgnoreCase(line)) {
                        if (supportsCapabilities) {
                            out.println("PROTOCOL=minidb-text/1");
                            out.println("AUTH=plain");
                            out.println("FEATURES=sql_text");
                        } else {
                            out.println("ERROR: unsupported command");
                        }
                        out.println("END");
                        continue;
                    }
                    out.println("OK");
                    out.println("END");
                }
            } catch (Exception ignored) {
                // Test helper server can exit on socket close.
            }
        }
    }
}

