package com.minidb.jdbc;

import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * MiniDB JDBC Connection.
 *
 * URL format: jdbc:minidb://host:port
 *
 * Maintains a persistent socket to the server and exposes
 * standard JDBC methods for statement execution and transaction control.
 */
public class MiniDbConnection implements Connection {

    static final Logger LOG = Logger.getLogger(MiniDbConnection.class.getName());

    private final Socket       socket;
    private final BufferedReader in;
    private final PrintWriter    out;

    private boolean autoCommit  = true;
    private boolean closed      = false;
    private boolean readOnly    = false;
    private String  catalog     = null;
    private int     holdability = ResultSet.CLOSE_CURSORS_AT_COMMIT;

    MiniDbConnection(String host, int port, String user, String password) throws SQLException {
        try {
            socket = new Socket(host, port);
            in     = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out    = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

            // Handshake
            String greeting = in.readLine(); // MINIDB 1.0
            String authPrompt = in.readLine(); // AUTH
            if (!"AUTH".equals(authPrompt))
                throw new SQLException("Protocol error: expected AUTH, got: " + authPrompt);

            out.println(user);
            out.println(password);

            String authResult = in.readLine();
            if (!"OK".equals(authResult))
                throw new SQLException("Authentication failed: " + authResult);

            LOG.fine("Connected to MiniDB at " + host + ":" + port);

        } catch (IOException e) {
            throw new SQLException("Cannot connect to MiniDB: " + e.getMessage(), e);
        }
    }

    // ── Statement Factory ──────────────────────────────────────────────────

    @Override
    public Statement createStatement() throws SQLException {
        checkOpen();
        return new MiniDbStatement(this);
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        checkOpen();
        return new MiniDbPreparedStatement(this, sql);
    }

    // ── Transaction Control ────────────────────────────────────────────────

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        checkOpen();
        if (this.autoCommit == autoCommit) return;
        this.autoCommit = autoCommit;
        if (!autoCommit) execute("BEGIN");
    }

    @Override
    public boolean getAutoCommit() { return autoCommit; }

    @Override
    public void commit() throws SQLException {
        checkOpen();
        if (autoCommit) throw new SQLException("Cannot commit in auto-commit mode");
        execute("COMMIT");
    }

    @Override
    public void rollback() throws SQLException {
        checkOpen();
        if (autoCommit) throw new SQLException("Cannot rollback in auto-commit mode");
        execute("ROLLBACK");
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints not supported");
    }

    // ── Connection State ───────────────────────────────────────────────────

    @Override
    public void close() throws SQLException {
        if (!closed) {
            try {
                out.println("QUIT");
                socket.close();
            } catch (IOException ignored) {}
            closed = true;
        }
    }

    @Override
    public boolean isClosed() { return closed; }

    @Override
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }

    @Override
    public boolean isReadOnly() { return readOnly; }

    @Override
    public void setCatalog(String catalog) { this.catalog = catalog; }

    @Override
    public String getCatalog() { return catalog; }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        // accept silently — only READ_COMMITTED behaviour for now
    }

    @Override
    public int getTransactionIsolation() { return Connection.TRANSACTION_READ_COMMITTED; }

    @Override
    public SQLWarning getWarnings() { return null; }

    @Override
    public void clearWarnings() {}

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return new MiniDbDatabaseMetaData(this);
    }

    @Override
    public boolean isValid(int timeout) {
        return !closed && socket.isConnected();
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    /**
     * Send a SQL string to the server and collect the response lines
     * (everything before the "END" marker).
     */
    synchronized List<String> execute(String sql) throws SQLException {
        checkOpen();
        try {
            out.println(sql);
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = in.readLine()) != null) {
                if ("END".equals(line)) break;
                lines.add(line);
            }
            if (!lines.isEmpty() && lines.get(0).startsWith("ERROR:")) {
                throw new SQLException(lines.get(0).substring(6).trim());
            }
            return lines;
        } catch (IOException e) {
            closed = true;
            throw new SQLException("Lost connection to server: " + e.getMessage(), e);
        }
    }

    private void checkOpen() throws SQLException {
        if (closed) throw new SQLException("Connection is closed");
    }

    // ── Unsupported / stubs ────────────────────────────────────────────────

    @Override public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException { return createStatement(); }
    @Override public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { return prepareStatement(sql); }
    @Override public CallableStatement prepareCall(String sql) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public String nativeSQL(String sql) { return sql; }
    @Override public Map<String, Class<?>> getTypeMap() { return Collections.emptyMap(); }
    @Override public void setTypeMap(Map<String, Class<?>> map) {}
    @Override public void setHoldability(int holdability) { this.holdability = holdability; }
    @Override public int getHoldability() { return holdability; }
    @Override public Savepoint setSavepoint() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Savepoint setSavepoint(String name) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void releaseSavepoint(Savepoint savepoint) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Statement createStatement(int rst, int rsc, int rsh) throws SQLException { return createStatement(); }
    @Override public PreparedStatement prepareStatement(String sql, int rst, int rsc, int rsh) throws SQLException { return prepareStatement(sql); }
    @Override public PreparedStatement prepareStatement(String sql, int[] ci) throws SQLException { return prepareStatement(sql); }
    @Override public PreparedStatement prepareStatement(String sql, String[] cn) throws SQLException { return prepareStatement(sql); }
    @Override public PreparedStatement prepareStatement(String sql, int ag) throws SQLException { return prepareStatement(sql); }
    @Override public CallableStatement prepareCall(String sql, int rst, int rsc, int rsh) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public java.sql.Clob createClob() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public java.sql.Blob createBlob() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public java.sql.NClob createNClob() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public java.sql.SQLXML createSQLXML() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void setClientInfo(String name, String value) {}
    @Override public void setClientInfo(Properties properties) {}
    @Override public String getClientInfo(String name) { return null; }
    @Override public Properties getClientInfo() { return new Properties(); }
    @Override public java.sql.Array createArrayOf(String typeName, Object[] elements) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public java.sql.Struct createStruct(String typeName, Object[] attributes) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void setSchema(String schema) {}
    @Override public String getSchema() { return null; }
    @Override public void abort(java.util.concurrent.Executor executor) {}
    @Override public void setNetworkTimeout(java.util.concurrent.Executor executor, int ms) {}
    @Override public int getNetworkTimeout() { return 0; }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean isWrapperFor(Class<?> iface) { return false; }
}

