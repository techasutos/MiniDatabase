package com.minidb.client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 * MiniDB interactive CLI client.
 *
 * Usage:
 *   java -cp ... com.minidb.client.MiniDbClient [host] [port] [user] [password]
 *
 * Defaults: host=localhost, port=5544, user=admin, password=minidb
 *
 * Commands:
 *   Any SQL statement terminated by pressing Enter.
 *   Type 'QUIT' or 'EXIT' to disconnect.
 *   Type 'HELP' for built-in help.
 */
public class MiniDbClient {

    private static final Logger LOG = Logger.getLogger(MiniDbClient.class.getName());

    public static void main(String[] args) throws Exception {
        String host     = args.length > 0 ? args[0] : "localhost";
        int    port     = args.length > 1 ? Integer.parseInt(args[1]) : 5544;
        String user     = args.length > 2 ? args[2] : "admin";
        String password = args.length > 3 ? args[3] : "minidb";

        System.out.printf("Connecting to %s:%d as %s ...%n", host, port, user);

        try (Socket socket = new Socket(host, port);
             BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter    out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
             Scanner        console = new Scanner(System.in)) {

            // ── Handshake ─────────────────────────────────────────────────
            String greeting = in.readLine();
            System.out.println("Server: " + greeting);

            String authPrompt = in.readLine(); // "AUTH"
            if (!"AUTH".equals(authPrompt)) {
                System.err.println("Unexpected server message: " + authPrompt);
                return;
            }

            out.println(user);
            out.println(password);

            String authResult = in.readLine();
            if (!"OK".equals(authResult)) {
                System.err.println("Authentication failed: " + authResult);
                return;
            }

            System.out.println("Connected to MiniDB. Type SQL or HELP / QUIT.");
            System.out.println();

            // ── REPL ──────────────────────────────────────────────────────
            while (true) {
                System.out.print("minidb> ");
                System.out.flush();

                if (!console.hasNextLine()) break;
                String line = console.nextLine().trim();

                if (line.isEmpty()) continue;
                if ("QUIT".equalsIgnoreCase(line) || "EXIT".equalsIgnoreCase(line)) {
                    out.println("QUIT");
                    System.out.println("Bye.");
                    break;
                }
                if ("HELP".equalsIgnoreCase(line)) {
                    printHelp();
                    continue;
                }
                if ("\\capabilities".equalsIgnoreCase(line)) {
                    line = "CAPABILITIES";
                }

                // Send SQL
                out.println(line);

                // Read response until END
                StringBuilder response = new StringBuilder();
                String serverLine;
                while ((serverLine = in.readLine()) != null) {
                    if ("END".equals(serverLine)) break;
                    response.append(serverLine).append("\n");
                }

                System.out.print(response);
                System.out.flush();
            }

        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }

    private static void printHelp() {
        System.out.println("""
                ┌─────────────────────────────────────────────────────────────┐
                │  MiniDB CLI Help                                            │
                ├─────────────────────────────────────────────────────────────┤
                │  DDL:                                                       │
                │    CREATE DATABASE mydb                                     │
                │    CREATE SCHEMA mydb.public                                │
                │    CREATE TABLE mydb.public.users (id INT, name STRING)     │
                │    DROP TABLE mydb.public.users                             │
                │                                                             │
                │  DML:                                                       │
                │    INSERT INTO mydb.public.users VALUES (1, 'Alice')        │
                │    SELECT * FROM mydb.public.users WHERE id = 1             │
                │    SELECT * FROM mydb.public.users ORDER BY id DESC LIMIT 5 │
                │    SELECT COUNT(*), AVG(id) FROM mydb.public.users          │
                │    UPDATE mydb.public.users SET name = 'Bob' WHERE id = 1   │
                │    DELETE FROM mydb.public.users WHERE id = 2               │
                │                                                             │
                │  Transactions:                                              │
                │    BEGIN  /  COMMIT  /  ROLLBACK                           │
                │                                                             │
                │  Client:                                                    │
                │    HELP  — this message                                     │
                │    \\capabilities — show server protocol features           │
                │    QUIT  — disconnect                                       │
                └─────────────────────────────────────────────────────────────┘
                """);
    }
}

