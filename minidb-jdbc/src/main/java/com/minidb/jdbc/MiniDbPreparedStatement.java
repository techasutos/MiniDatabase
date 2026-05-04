package com.minidb.jdbc;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MiniDB PreparedStatement with basic '?' parameter substitution.
 */
public class MiniDbPreparedStatement extends MiniDbStatement implements PreparedStatement {

    private final String template;
    private final List<String> params = new ArrayList<>();

    MiniDbPreparedStatement(MiniDbConnection connection, String sql) {
        super(connection);
        this.template = sql;
        // pre-count '?' placeholders
        for (char c : sql.toCharArray()) if (c == '?') params.add(null);
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        return executeQuery(build());
    }

    @Override
    public int executeUpdate() throws SQLException {
        return executeUpdate(build());
    }

    @Override
    public boolean execute() throws SQLException {
        return execute(build());
    }

    // ── Parameter setters ──────────────────────────────────────────────────

    @Override public void setNull(int i, int t) throws SQLException    { set(i, "NULL"); }
    @Override public void setBoolean(int i, boolean v) throws SQLException { set(i, String.valueOf(v)); }
    @Override public void setByte(int i, byte v) throws SQLException   { set(i, String.valueOf(v)); }
    @Override public void setShort(int i, short v) throws SQLException { set(i, String.valueOf(v)); }
    @Override public void setInt(int i, int v) throws SQLException     { set(i, String.valueOf(v)); }
    @Override public void setLong(int i, long v) throws SQLException   { set(i, String.valueOf(v)); }
    @Override public void setFloat(int i, float v) throws SQLException { set(i, String.valueOf(v)); }
    @Override public void setDouble(int i, double v) throws SQLException { set(i, String.valueOf(v)); }
    @Override public void setBigDecimal(int i, java.math.BigDecimal v) throws SQLException { set(i, v == null ? "NULL" : v.toPlainString()); }
    @Override public void setString(int i, String v) throws SQLException {
        if (v == null) { set(i, "NULL"); return; }
        set(i, "'" + v.replace("'", "\\'") + "'");
    }
    @Override public void setBytes(int i, byte[] v) throws SQLException { set(i, v == null ? "NULL" : new String(v)); }
    @Override public void setDate(int i, java.sql.Date v) throws SQLException { set(i, v == null ? "NULL" : "'" + v + "'"); }
    @Override public void setTime(int i, java.sql.Time v) throws SQLException { set(i, v == null ? "NULL" : "'" + v + "'"); }
    @Override public void setTimestamp(int i, java.sql.Timestamp v) throws SQLException { set(i, v == null ? "NULL" : "'" + v + "'"); }
    @Override public void setObject(int i, Object v) throws SQLException {
        if (v == null)             { setNull(i, Types.NULL); return; }
        if (v instanceof String)   { setString(i, (String) v); return; }
        if (v instanceof Integer)  { setInt(i, (Integer) v); return; }
        if (v instanceof Long)     { setLong(i, (Long) v); return; }
        if (v instanceof Double)   { setDouble(i, (Double) v); return; }
        if (v instanceof Boolean)  { setBoolean(i, (Boolean) v); return; }
        set(i, v.toString());
    }
    @Override public void setObject(int i, Object v, int t) throws SQLException { setObject(i, v); }
    @Override public void setObject(int i, Object v, int t, int s) throws SQLException { setObject(i, v); }
    @Override public void clearParameters() { params.replaceAll(p -> null); }

    // ── Build SQL ──────────────────────────────────────────────────────────

    private String build() throws SQLException {
        StringBuilder sb = new StringBuilder();
        int paramIdx = 0;
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '?') {
                if (paramIdx >= params.size()) throw new SQLException("Not enough parameters set");
                String val = params.get(paramIdx++);
                if (val == null) throw new SQLException("Parameter " + paramIdx + " not set");
                sb.append(val);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void set(int paramIndex, String value) throws SQLException {
        if (paramIndex < 1 || paramIndex > params.size())
            throw new SQLException("Parameter index " + paramIndex + " out of range");
        params.set(paramIndex - 1, value);
    }

    // ── Unsupported stubs ──────────────────────────────────────────────────

    private static final SQLFeatureNotSupportedException U = new SQLFeatureNotSupportedException();
    @Override public void setAsciiStream(int i, java.io.InputStream x, int l) throws SQLException { throw U; }
    @Override public void setUnicodeStream(int i, java.io.InputStream x, int l) throws SQLException { throw U; }
    @Override public void setBinaryStream(int i, java.io.InputStream x, int l) throws SQLException { throw U; }
    @Override public void setCharacterStream(int i, java.io.Reader r, int l) throws SQLException { throw U; }
    @Override public void setRef(int i, Ref x) throws SQLException { throw U; }
    @Override public void setBlob(int i, Blob x) throws SQLException { throw U; }
    @Override public void setClob(int i, Clob x) throws SQLException { throw U; }
    @Override public void setArray(int i, Array x) throws SQLException { throw U; }
    @Override public void setDate(int i, java.sql.Date d, java.util.Calendar c) throws SQLException { setDate(i,d); }
    @Override public void setTime(int i, java.sql.Time t, java.util.Calendar c) throws SQLException { setTime(i,t); }
    @Override public void setTimestamp(int i, java.sql.Timestamp t, java.util.Calendar c) throws SQLException { setTimestamp(i,t); }
    @Override public void setNull(int i, int t, String tn) throws SQLException { setNull(i, t); }
    @Override public void setURL(int i, java.net.URL x) throws SQLException { throw U; }
    @Override public ParameterMetaData getParameterMetaData() throws SQLException { throw U; }
    @Override public ResultSetMetaData getMetaData() throws SQLException { return null; }
    @Override public void setRowId(int i, RowId x) throws SQLException { throw U; }
    @Override public void setNString(int i, String v) throws SQLException { setString(i, v); }
    @Override public void setNCharacterStream(int i, java.io.Reader r, long l) throws SQLException { throw U; }
    @Override public void setNClob(int i, NClob x) throws SQLException { throw U; }
    @Override public void setClob(int i, java.io.Reader r, long l) throws SQLException { throw U; }
    @Override public void setBlob(int i, java.io.InputStream is, long l) throws SQLException { throw U; }
    @Override public void setNClob(int i, java.io.Reader r, long l) throws SQLException { throw U; }
    @Override public void setSQLXML(int i, SQLXML x) throws SQLException { throw U; }
    @Override public void setAsciiStream(int i, java.io.InputStream x, long l) throws SQLException { throw U; }
    @Override public void setBinaryStream(int i, java.io.InputStream x, long l) throws SQLException { throw U; }
    @Override public void setCharacterStream(int i, java.io.Reader r, long l) throws SQLException { throw U; }
    @Override public void setAsciiStream(int i, java.io.InputStream x) throws SQLException { throw U; }
    @Override public void setBinaryStream(int i, java.io.InputStream x) throws SQLException { throw U; }
    @Override public void setCharacterStream(int i, java.io.Reader r) throws SQLException { throw U; }
    @Override public void setNCharacterStream(int i, java.io.Reader r) throws SQLException { throw U; }
    @Override public void setClob(int i, java.io.Reader r) throws SQLException { throw U; }
    @Override public void setBlob(int i, java.io.InputStream is) throws SQLException { throw U; }
    @Override public void setNClob(int i, java.io.Reader r) throws SQLException { throw U; }
    @Override public int[] executeBatch() throws SQLException { throw U; }
    @Override public void addBatch() throws SQLException { throw U; }
}

