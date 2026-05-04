# Java Application Integration Guide

This guide explains how another Java application can connect to MiniDatabase and execute SQL.

## 1. Supported client options

You currently have two practical integration options:

1. JDBC driver (`minidb-jdbc`) - recommended for Java apps.
2. Raw TCP text protocol - useful for custom clients.

## 2. JDBC integration

## 2.1 Driver and URL

- Driver class: `com.minidb.jdbc.MiniDbDriver`
- JDBC URL format: `jdbc:minidb://<host>:<port>/`
- Default credentials if omitted in properties:
  - user: `admin`
  - password: `minidb`

## 2.2 Minimal Java example

```java
import java.sql.*;
import java.util.Properties;

public class MiniDbJdbcExample {
    public static void main(String[] args) throws Exception {
        Class.forName("com.minidb.jdbc.MiniDbDriver");

        String url = "jdbc:minidb://localhost:5432/";
        Properties props = new Properties();
        props.setProperty("user", "admin");
        props.setProperty("password", "minidb");

        try (Connection conn = DriverManager.getConnection(url, props);
             Statement st = conn.createStatement()) {

            st.execute("CREATE DATABASE testdb");
            st.execute("CREATE SCHEMA testdb.public");
            st.execute("CREATE TABLE testdb.public.users (id INT, name VARCHAR(64))");

            st.executeUpdate("INSERT INTO testdb.public.users VALUES (1, 'Alice')");
            st.executeUpdate("INSERT INTO testdb.public.users VALUES (2, 'Bob')");

            try (ResultSet rs = st.executeQuery("SELECT * FROM testdb.public.users ORDER BY id")) {
                while (rs.next()) {
                    System.out.println(rs.getString(1) + ", " + rs.getString(2));
                }
            }
        }
    }
}
```

## 2.3 Prepared statement example

```java
import java.sql.*;
import java.util.Properties;

public class MiniDbPreparedExample {
    public static void main(String[] args) throws Exception {
        Class.forName("com.minidb.jdbc.MiniDbDriver");

        Properties p = new Properties();
        p.setProperty("user", "admin");
        p.setProperty("password", "minidb");

        try (Connection c = DriverManager.getConnection("jdbc:minidb://localhost:5432/", p)) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO testdb.public.users VALUES (?, ?)") ) {
                ps.setInt(1, 3);
                ps.setString(2, "Charlie");
                ps.executeUpdate();
            }
        }
    }
}
```

## 2.4 Transaction usage example

```java
import java.sql.*;
import java.util.Properties;

public class MiniDbTransactionExample {
    public static void main(String[] args) throws Exception {
        Class.forName("com.minidb.jdbc.MiniDbDriver");

        Properties p = new Properties();
        p.setProperty("user", "admin");
        p.setProperty("password", "minidb");

        try (Connection c = DriverManager.getConnection("jdbc:minidb://localhost:5432/", p);
             Statement s = c.createStatement()) {

            c.setAutoCommit(false); // sends BEGIN
            try {
                s.executeUpdate("UPDATE testdb.public.users SET name='Alice-Updated' WHERE id=1");
                c.commit(); // sends COMMIT
            } catch (Exception e) {
                c.rollback(); // sends ROLLBACK
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }
}
```

## 2.5 What happens internally for JDBC

1. Driver parses URL and opens socket to server.
2. Driver reads server greeting and AUTH prompt.
3. Driver sends username/password.
4. On `Statement.execute(...)`, SQL is sent as one line.
5. Driver reads response lines until `END`.
6. Errors (`ERROR:`) are raised as `SQLException`.
7. Result lines are mapped into JDBC `ResultSet` values.

## 3. Raw TCP text protocol integration

Protocol sequence:

1. Server -> `MINIDB 1.0`
2. Server -> `AUTH`
3. Client -> `<username>`
4. Client -> `<password>`
5. Server -> `OK` or `ERROR: Authentication failed`
6. Client sends SQL line by line.
7. Server responds with lines and trailing `END`.
8. Client sends `QUIT` to close.

Minimal raw socket sample:

```java
import java.io.*;
import java.net.Socket;

public class RawMiniDbClient {
    public static void main(String[] args) throws Exception {
        try (Socket s = new Socket("localhost", 5432);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {

            System.out.println(in.readLine()); // MINIDB 1.0
            System.out.println(in.readLine()); // AUTH

            out.println("admin");
            out.println("minidb");
            System.out.println(in.readLine()); // OK

            out.println("SELECT * FROM testdb.public.users");
            for (String line; (line = in.readLine()) != null;) {
                if ("END".equals(line)) break;
                System.out.println(line);
            }

            out.println("QUIT");
        }
    }
}
```

## 4. Running server and client locally

```powershell
Set-Location "D:\projects\MiniDatabase"
mvn -pl minidb-server -am exec:java -Dexec.mainClass="com.minidb.server.DatabaseServer"
```

In another terminal:

```powershell
Set-Location "D:\projects\MiniDatabase"
mvn -pl minidb-client -am exec:java -Dexec.mainClass="com.minidb.client.MiniDbClient"
```

## 5. Data update flow (from external Java app)

For an UPDATE executed via JDBC:

1. Java app calls `Statement.executeUpdate("UPDATE ...")`.
2. JDBC driver sends SQL to server over socket.
3. `TextProtocolHandler` forwards SQL to parser/executor function.
4. AST built by parser.
5. `Engine` dispatches to update path.
6. `TableStorage.update(...)` scans pages, applies predicate and assignment.
7. Modified pages are marked dirty and flushed by buffer pool.
8. Response line (e.g., `N ROWS UPDATED`) returned to client.
9. Driver maps this to update count.

## 6. Integration caveats for application developers

- SQL is currently single-line protocol framing; avoid multiline SQL unless client normalizes it.
- JDBC implementation is baseline and not full enterprise JDBC compliance yet.
- Transport is plaintext; for production, run in trusted network or add TLS proxy.
- Role-based authorization is not yet enforced.

## 7. Recommended production-style wrapper for your app

Until full JDBC compliance hardening is complete:

- Centralize MiniDB access through repository/service classes.
- Add retry and timeout wrappers around SQL operations.
- Validate update counts and enforce idempotency where needed.
- Add your own audit logging around SQL calls.

