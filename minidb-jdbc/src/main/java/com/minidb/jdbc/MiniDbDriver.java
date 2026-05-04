package com.minidb.jdbc;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * MiniDB JDBC Driver.
 *
 * Registration (auto via SPI):
 *   META-INF/services/java.sql.Driver → com.minidb.jdbc.MiniDbDriver
 *
 * URL format:
 *   jdbc:minidb://host:port[/]
 *   e.g. jdbc:minidb://localhost:5432/
 *
 * Default credentials: user=admin, password=minidb (override via Properties).
 */
public class MiniDbDriver implements Driver {

    private static final Logger LOG = Logger.getLogger(MiniDbDriver.class.getName());

    static {
        try {
            DriverManager.registerDriver(new MiniDbDriver());
            LOG.info("MiniDB JDBC Driver registered");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register MiniDB driver", e);
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) return null;

        String host = "localhost";
        int    port = 5432;

        // Parse jdbc:minidb://host:port
        try {
            String stripped = url.replace("jdbc:minidb://", "");
            if (stripped.contains("/")) stripped = stripped.substring(0, stripped.indexOf('/'));
            if (stripped.contains(":")) {
                String[] hp = stripped.split(":");
                host = hp[0];
                port = Integer.parseInt(hp[1]);
            } else if (!stripped.isEmpty()) {
                host = stripped;
            }
        } catch (Exception e) {
            throw new SQLException("Invalid MiniDB URL: " + url, e);
        }

        String user     = info != null ? info.getProperty("user",     "admin")   : "admin";
        String password = info != null ? info.getProperty("password", "minidb")  : "minidb";

        return new MiniDbConnection(host, port, user, password);
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith("jdbc:minidb://");
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[]{
            new DriverPropertyInfo("user", "admin"),
            new DriverPropertyInfo("password", "minidb")
        };
    }

    @Override public int     getMajorVersion() { return 1; }
    @Override public int     getMinorVersion() { return 0; }
    @Override public boolean jdbcCompliant()   { return false; }
    @Override public Logger  getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
}

