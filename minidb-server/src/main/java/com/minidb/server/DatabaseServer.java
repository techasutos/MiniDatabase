package com.minidb.server;

import com.minidb.catalog.CatalogManager;
import com.minidb.executor.Engine;
import com.minidb.sql.SQLParserService;
import com.minidb.sql.ast.Statement;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;

public class DatabaseServer {

    public static void main(String[] args) throws Exception {

        int port = 5432;

        System.out.println("MiniDB starting on port " + port);

        SQLParserService parser = new SQLParserService();
        CatalogManager catalog = new CatalogManager();
        Engine engine = new Engine(Paths.get("data"), catalog);

        ServerSocket serverSocket = new ServerSocket(port);

        while (true) {

            Socket client = serverSocket.accept();

            new Thread(() -> handleClient(client, parser, engine)).start();
        }
    }

    private static void handleClient(Socket socket,
                                     SQLParserService parser,
                                     Engine engine) {

        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));

                PrintWriter out = new PrintWriter(
                        socket.getOutputStream(), true)
        ) {

            out.println("Connected to MiniDB");

            String line;

            while ((line = in.readLine()) != null) {

                try {
                    Statement stmt = parser.parse(line);
                    String result = engine.execute(stmt);
                    out.println(result);

                } catch (Exception e) {
                    out.println("ERROR: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}