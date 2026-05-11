package com.minidb.transport.protocol;

import com.minidb.catalog.CatalogManager;
import com.minidb.catalog.model.Column;
import com.minidb.catalog.model.DataType;
import com.minidb.transport.auth.InMemoryAuthService;
import com.minidb.transport.session.SessionRegistry;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TextProtocolHandlerMetadataTest {

    @Test
    void servesMetadataAndSessionsThroughShowCommands() throws Exception {
        CatalogManager catalog = new CatalogManager();
        catalog.createDatabase("testdb");
        catalog.createSchema("testdb", "analytics");
        catalog.createTable(
                "testdb",
                "analytics",
                "users",
                List.of(new Column("id", DataType.INT), new Column("name", DataType.STRING))
        );

        SessionRegistry registry = new SessionRegistry();
        Supplier<List<String>> databases = catalog::listDatabaseNames;
        Function<String, List<String>> schemas = catalog::listSchemaNames;
        Function<String, List<String>> tables = schemaRef -> {
            String[] parts = schemaRef.split("\\.", 2);
            return catalog.listTableNames(parts[0], parts[1]);
        };
        Function<String, List<String>> columns = tableRef -> {
            String[] parts = tableRef.split("\\.", 3);
            return catalog.listColumnDefinitions(parts[0], parts[1], parts[2]);
        };

        TextProtocolHandler handler = new TextProtocolHandler(
                new InMemoryAuthService(),
                sql -> "OK",
                databases,
                schemas,
                tables,
                columns,
                registry
        );

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            CountDownLatch started = new CountDownLatch(1);
            Thread serverThread = new Thread(() -> {
                started.countDown();
                try (Socket socket = serverSocket.accept()) {
                    handler.handle(socket);
                } catch (Exception ignored) {
                }
            });
            serverThread.start();
            assertTrue(started.await(2, TimeUnit.SECONDS));

            try (Socket client = new Socket("127.0.0.1", serverSocket.getLocalPort());
                 BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                 PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(client.getOutputStream())), true)) {

                assertTrue(in.readLine().startsWith("MINIDB"));
                assertTrue("AUTH".equals(in.readLine()));
                out.println("admin");
                out.println("minidb");
                assertTrue("OK".equals(in.readLine()));

                out.println("SHOW DATABASES");
                List<String> databasesResponse = readUntilEnd(in);
                assertTrue(databasesResponse.contains("DATABASE testdb"));

                out.println("SHOW SCHEMAS IN testdb");
                List<String> schemasResponse = readUntilEnd(in);
                assertTrue(schemasResponse.contains("SCHEMA analytics"));
                assertTrue(schemasResponse.contains("SCHEMA public"));

                out.println("SHOW TABLES IN testdb.analytics");
                List<String> tablesResponse = readUntilEnd(in);
                assertTrue(tablesResponse.contains("TABLE users"));

                out.println("SHOW COLUMNS IN testdb.analytics.users");
                List<String> columnsResponse = readUntilEnd(in);
                assertTrue(columnsResponse.contains("COLUMN id:INT"));
                assertTrue(columnsResponse.contains("COLUMN name:STRING"));

                out.println("SHOW SESSIONS");
                List<String> sessionsResponse = readUntilEnd(in);
                assertTrue(sessionsResponse.stream().anyMatch(line -> line.startsWith("SESSION sessionId=")));
                assertTrue(sessionsResponse.stream().anyMatch(line -> line.contains("user=admin")));

                out.println("QUIT");
            }

            serverThread.join(2000);
        }
    }

    private static List<String> readUntilEnd(BufferedReader in) throws Exception {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        while (true) {
            String line = in.readLine();
            if (line == null) {
                break;
            }
            if ("END".equals(line)) {
                break;
            }
            lines.add(line);
        }
        return lines;
    }
}

