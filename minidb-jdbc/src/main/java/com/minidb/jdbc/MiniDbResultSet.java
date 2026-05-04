package com.minidb.jdbc;

import java.sql.*;
import java.util.*;

/**
 * MiniDB JDBC ResultSet.
 * Parses server text responses of the form: [val1, val2, val3]
 */
public class MiniDbResultSet implements ResultSet {

    private final List<List<String>> rows;
    private final List<String>       columnNames;
    private int     cursor  = -1;
    private boolean closed  = false;
    private boolean wasNull = false;

    private MiniDbResultSet(List<String> columnNames, List<List<String>> rows) {
        this.columnNames = columnNames;
        this.rows        = rows;
    }

    static MiniDbResultSet fromLines(List<String> lines) {
        List<String>       colNames = new ArrayList<>();
        List<List<String>> data     = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            if (!line.trim().startsWith("[")) {
                if (data.isEmpty()) { colNames.addAll(Arrays.asList(line.split(",\\s*"))); }
                continue;
            }
            data.add(parseLine(line));
        }
        if (colNames.isEmpty() && !data.isEmpty())
            for (int i = 0; i < data.get(0).size(); i++) colNames.add("col" + (i + 1));
        return new MiniDbResultSet(colNames, data);
    }

    static MiniDbResultSet empty() { return new MiniDbResultSet(List.of(), List.of()); }

    @Override public boolean next()          { cursor++; return cursor < rows.size(); }
    @Override public boolean isBeforeFirst() { return cursor < 0; }
    @Override public boolean isAfterLast()   { return cursor >= rows.size(); }
    @Override public boolean isFirst()       { return cursor == 0; }
    @Override public boolean isLast()        { return cursor == rows.size() - 1; }
    @Override public int     getRow()        { return cursor + 1; }
    @Override public boolean absolute(int r) { cursor = r - 1; return cursor >= 0 && cursor < rows.size(); }
    @Override public boolean relative(int r) { cursor += r;    return cursor >= 0 && cursor < rows.size(); }
    @Override public boolean previous()      { cursor--; return cursor >= 0; }
    @Override public void    beforeFirst()   { cursor = -1; }
    @Override public void    afterLast()     { cursor = rows.size(); }
    @Override public boolean first()         { cursor = 0; return !rows.isEmpty(); }
    @Override public boolean last()          { cursor = Math.max(0, rows.size()-1); return !rows.isEmpty(); }
    @Override public Statement getStatement() { return null; }
    @Override public void setFetchDirection(int direction) {}
    @Override public int  getFetchDirection() { return FETCH_FORWARD; }
    @Override public void setFetchSize(int rows) {}
    @Override public int  getFetchSize() { return 0; }

    private String raw(int idx) throws SQLException {
        if (closed) throw new SQLException("ResultSet is closed");
        if (cursor < 0 || cursor >= rows.size()) throw new SQLException("No current row");
        List<String> row = rows.get(cursor);
        if (idx < 0 || idx >= row.size()) throw new SQLException("Column index " + (idx+1) + " out of range");
        String v = row.get(idx);
        wasNull = (v == null || "null".equalsIgnoreCase(v));
        return wasNull ? null : v;
    }

    @Override public boolean wasNull() { return wasNull; }
    @Override public String  getString(int c)  throws SQLException { return raw(c-1); }
    @Override public boolean getBoolean(int c) throws SQLException { String v=raw(c-1); return v!=null&&("true".equalsIgnoreCase(v)||"1".equals(v)); }
    @Override public byte    getByte(int c)    throws SQLException { String v=raw(c-1); return v==null?0:Byte.parseByte(v.trim()); }
    @Override public short   getShort(int c)   throws SQLException { String v=raw(c-1); return v==null?0:Short.parseShort(v.trim()); }
    @Override public int     getInt(int c)     throws SQLException { String v=raw(c-1); return v==null?0:Integer.parseInt(v.trim()); }
    @Override public long    getLong(int c)    throws SQLException { String v=raw(c-1); return v==null?0L:Long.parseLong(v.trim()); }
    @Override public float   getFloat(int c)   throws SQLException { String v=raw(c-1); return v==null?0f:Float.parseFloat(v.trim()); }
    @Override public double  getDouble(int c)  throws SQLException { String v=raw(c-1); return v==null?0d:Double.parseDouble(v.trim()); }
    @Override public java.math.BigDecimal getBigDecimal(int c, int s) throws SQLException { String v=raw(c-1); return v==null?null:new java.math.BigDecimal(v.trim()).setScale(s); }
    @Override public java.math.BigDecimal getBigDecimal(int c)        throws SQLException { String v=raw(c-1); return v==null?null:new java.math.BigDecimal(v.trim()); }
    @Override public Object  getObject(int c)  throws SQLException { return getString(c); }
    @Override public byte[]  getBytes(int c)   throws SQLException { String v=getString(c); return v==null?null:v.getBytes(); }
    @Override public java.sql.Date      getDate(int c)           throws SQLException { return null; }
    @Override public java.sql.Time      getTime(int c)           throws SQLException { return null; }
    @Override public java.sql.Timestamp getTimestamp(int c)      throws SQLException { return null; }
    @Override public java.sql.Date      getDate(int c, java.util.Calendar cal)      throws SQLException { return null; }
    @Override public java.sql.Time      getTime(int c, java.util.Calendar cal)      throws SQLException { return null; }
    @Override public java.sql.Timestamp getTimestamp(int c, java.util.Calendar cal) throws SQLException { return null; }

    @Override public String  getString(String n)  throws SQLException { return getString(findColumn(n)); }
    @Override public boolean getBoolean(String n) throws SQLException { return getBoolean(findColumn(n)); }
    @Override public byte    getByte(String n)    throws SQLException { return getByte(findColumn(n)); }
    @Override public short   getShort(String n)   throws SQLException { return getShort(findColumn(n)); }
    @Override public int     getInt(String n)     throws SQLException { return getInt(findColumn(n)); }
    @Override public long    getLong(String n)    throws SQLException { return getLong(findColumn(n)); }
    @Override public float   getFloat(String n)   throws SQLException { return getFloat(findColumn(n)); }
    @Override public double  getDouble(String n)  throws SQLException { return getDouble(findColumn(n)); }
    @Override public java.math.BigDecimal getBigDecimal(String n, int s) throws SQLException { return getBigDecimal(findColumn(n),s); }
    @Override public java.math.BigDecimal getBigDecimal(String n)        throws SQLException { return getBigDecimal(findColumn(n)); }
    @Override public Object  getObject(String n)  throws SQLException { return getObject(findColumn(n)); }
    @Override public byte[]  getBytes(String n)   throws SQLException { return getBytes(findColumn(n)); }
    @Override public java.sql.Date      getDate(String n)           throws SQLException { return null; }
    @Override public java.sql.Time      getTime(String n)           throws SQLException { return null; }
    @Override public java.sql.Timestamp getTimestamp(String n)      throws SQLException { return null; }
    @Override public java.sql.Date      getDate(String n, java.util.Calendar cal)      throws SQLException { return null; }
    @Override public java.sql.Time      getTime(String n, java.util.Calendar cal)      throws SQLException { return null; }
    @Override public java.sql.Timestamp getTimestamp(String n, java.util.Calendar cal) throws SQLException { return null; }

    @Override public ResultSetMetaData getMetaData() { return new MiniDbResultSetMetaData(columnNames); }
    @Override public int findColumn(String label) throws SQLException {
        for (int i=0;i<columnNames.size();i++) if (columnNames.get(i).equalsIgnoreCase(label)) return i+1;
        throw new SQLException("Column not found: " + label);
    }
    @Override public void close()       { closed = true; }
    @Override public boolean isClosed() { return closed; }
    @Override public int getType()        { return TYPE_FORWARD_ONLY; }
    @Override public int getConcurrency() { return CONCUR_READ_ONLY; }
    @Override public int getHoldability() { return CLOSE_CURSORS_AT_COMMIT; }
    @Override public SQLWarning getWarnings() { return null; }
    @Override public void clearWarnings() {}
    @Override public String getCursorName() { return ""; }
    @Override public boolean rowUpdated()  { return false; }
    @Override public boolean rowInserted() { return false; }
    @Override public boolean rowDeleted()  { return false; }

    // All update / stream / specialized getters throw or return null
    private static final SQLFeatureNotSupportedException U = new SQLFeatureNotSupportedException("Read-only ResultSet");
    @Override public java.io.InputStream getAsciiStream(int c)    { return null; }
    @Override public java.io.InputStream getAsciiStream(String c) { return null; }
    @Override public java.io.InputStream getUnicodeStream(int c)  { return null; }
    @Override public java.io.InputStream getUnicodeStream(String c){ return null; }
    @Override public java.io.InputStream getBinaryStream(int c)   { return null; }
    @Override public java.io.InputStream getBinaryStream(String c){ return null; }
    @Override public java.io.Reader getCharacterStream(int c)   { return null; }
    @Override public java.io.Reader getCharacterStream(String c){ return null; }
    @Override public java.io.Reader getNCharacterStream(int c)   { return null; }
    @Override public java.io.Reader getNCharacterStream(String c){ return null; }
    @Override public String getNString(int c)    throws SQLException { return getString(c); }
    @Override public String getNString(String c) throws SQLException { return getString(c); }
    @Override public java.sql.Ref    getRef(int c)    throws SQLException { throw U; }
    @Override public java.sql.Ref    getRef(String c) throws SQLException { throw U; }
    @Override public java.sql.Blob   getBlob(int c)    throws SQLException { throw U; }
    @Override public java.sql.Blob   getBlob(String c) throws SQLException { throw U; }
    @Override public java.sql.Clob   getClob(int c)    throws SQLException { throw U; }
    @Override public java.sql.Clob   getClob(String c) throws SQLException { throw U; }
    @Override public java.sql.Array  getArray(int c)    throws SQLException { throw U; }
    @Override public java.sql.Array  getArray(String c) throws SQLException { throw U; }
    @Override public java.net.URL    getURL(int c)    throws SQLException { throw U; }
    @Override public java.net.URL    getURL(String c) throws SQLException { throw U; }
    @Override public java.sql.RowId  getRowId(int c)    throws SQLException { throw U; }
    @Override public java.sql.RowId  getRowId(String c) throws SQLException { throw U; }
    @Override public java.sql.NClob  getNClob(int c)    throws SQLException { throw U; }
    @Override public java.sql.NClob  getNClob(String c) throws SQLException { throw U; }
    @Override public java.sql.SQLXML getSQLXML(int c)    throws SQLException { throw U; }
    @Override public java.sql.SQLXML getSQLXML(String c) throws SQLException { throw U; }
    @Override public <T> T getObject(int c, Class<T> t) throws SQLException { return t.cast(getString(c)); }
    @Override public <T> T getObject(String c, Class<T> t) throws SQLException { return t.cast(getString(c)); }
    @Override public Object getObject(int c, java.util.Map<String,Class<?>> m) throws SQLException { return getObject(c); }
    @Override public Object getObject(String c, java.util.Map<String,Class<?>> m) throws SQLException { return getObject(c); }
    @Override public void updateNull(int c) throws SQLException { throw U; }
    @Override public void updateNull(String c) throws SQLException { throw U; }
    @Override public void updateBoolean(int c, boolean v) throws SQLException { throw U; }
    @Override public void updateBoolean(String c, boolean v) throws SQLException { throw U; }
    @Override public void updateByte(int c, byte v) throws SQLException { throw U; }
    @Override public void updateByte(String c, byte v) throws SQLException { throw U; }
    @Override public void updateShort(int c, short v) throws SQLException { throw U; }
    @Override public void updateShort(String c, short v) throws SQLException { throw U; }
    @Override public void updateInt(int c, int v) throws SQLException { throw U; }
    @Override public void updateInt(String c, int v) throws SQLException { throw U; }
    @Override public void updateLong(int c, long v) throws SQLException { throw U; }
    @Override public void updateLong(String c, long v) throws SQLException { throw U; }
    @Override public void updateFloat(int c, float v) throws SQLException { throw U; }
    @Override public void updateFloat(String c, float v) throws SQLException { throw U; }
    @Override public void updateDouble(int c, double v) throws SQLException { throw U; }
    @Override public void updateDouble(String c, double v) throws SQLException { throw U; }
    @Override public void updateBigDecimal(int c, java.math.BigDecimal v) throws SQLException { throw U; }
    @Override public void updateBigDecimal(String c, java.math.BigDecimal v) throws SQLException { throw U; }
    @Override public void updateString(int c, String v) throws SQLException { throw U; }
    @Override public void updateString(String c, String v) throws SQLException { throw U; }
    @Override public void updateBytes(int c, byte[] v) throws SQLException { throw U; }
    @Override public void updateBytes(String c, byte[] v) throws SQLException { throw U; }
    @Override public void updateDate(int c, java.sql.Date v) throws SQLException { throw U; }
    @Override public void updateDate(String c, java.sql.Date v) throws SQLException { throw U; }
    @Override public void updateTime(int c, java.sql.Time v) throws SQLException { throw U; }
    @Override public void updateTime(String c, java.sql.Time v) throws SQLException { throw U; }
    @Override public void updateTimestamp(int c, java.sql.Timestamp v) throws SQLException { throw U; }
    @Override public void updateTimestamp(String c, java.sql.Timestamp v) throws SQLException { throw U; }
    @Override public void updateObject(int c, Object x) throws SQLException { throw U; }
    @Override public void updateObject(String c, Object x) throws SQLException { throw U; }
    @Override public void updateObject(int c, Object x, int s) throws SQLException { throw U; }
    @Override public void updateObject(String c, Object x, int s) throws SQLException { throw U; }
    @Override public void updateAsciiStream(int c, java.io.InputStream x, int l)    throws SQLException { throw U; }
    @Override public void updateAsciiStream(String c, java.io.InputStream x, int l)  throws SQLException { throw U; }
    @Override public void updateAsciiStream(int c, java.io.InputStream x, long l)   throws SQLException { throw U; }
    @Override public void updateAsciiStream(String c, java.io.InputStream x, long l) throws SQLException { throw U; }
    @Override public void updateAsciiStream(int c, java.io.InputStream x)   throws SQLException { throw U; }
    @Override public void updateAsciiStream(String c, java.io.InputStream x) throws SQLException { throw U; }
    @Override public void updateBinaryStream(int c, java.io.InputStream x, int l)    throws SQLException { throw U; }
    @Override public void updateBinaryStream(String c, java.io.InputStream x, int l)  throws SQLException { throw U; }
    @Override public void updateBinaryStream(int c, java.io.InputStream x, long l)   throws SQLException { throw U; }
    @Override public void updateBinaryStream(String c, java.io.InputStream x, long l) throws SQLException { throw U; }
    @Override public void updateBinaryStream(int c, java.io.InputStream x)   throws SQLException { throw U; }
    @Override public void updateBinaryStream(String c, java.io.InputStream x) throws SQLException { throw U; }
    @Override public void updateCharacterStream(int c, java.io.Reader x, int l)    throws SQLException { throw U; }
    @Override public void updateCharacterStream(String c, java.io.Reader x, int l)  throws SQLException { throw U; }
    @Override public void updateCharacterStream(int c, java.io.Reader x, long l)   throws SQLException { throw U; }
    @Override public void updateCharacterStream(String c, java.io.Reader x, long l) throws SQLException { throw U; }
    @Override public void updateCharacterStream(int c, java.io.Reader x)   throws SQLException { throw U; }
    @Override public void updateCharacterStream(String c, java.io.Reader x) throws SQLException { throw U; }
    @Override public void updateNCharacterStream(int c, java.io.Reader x, long l)    throws SQLException { throw U; }
    @Override public void updateNCharacterStream(String c, java.io.Reader x, long l)  throws SQLException { throw U; }
    @Override public void updateNCharacterStream(int c, java.io.Reader x)   throws SQLException { throw U; }
    @Override public void updateNCharacterStream(String c, java.io.Reader x) throws SQLException { throw U; }
    @Override public void updateNString(int c, String x) throws SQLException { throw U; }
    @Override public void updateNString(String c, String x) throws SQLException { throw U; }
    @Override public void updateNClob(int c, java.sql.NClob x) throws SQLException { throw U; }
    @Override public void updateNClob(String c, java.sql.NClob x) throws SQLException { throw U; }
    @Override public void updateNClob(int c, java.io.Reader r, long l) throws SQLException { throw U; }
    @Override public void updateNClob(String c, java.io.Reader r, long l) throws SQLException { throw U; }
    @Override public void updateNClob(int c, java.io.Reader r) throws SQLException { throw U; }
    @Override public void updateNClob(String c, java.io.Reader r) throws SQLException { throw U; }
    @Override public void updateRef(int c, java.sql.Ref x) throws SQLException { throw U; }
    @Override public void updateRef(String c, java.sql.Ref x) throws SQLException { throw U; }
    @Override public void updateBlob(int c, java.sql.Blob x) throws SQLException { throw U; }
    @Override public void updateBlob(String c, java.sql.Blob x) throws SQLException { throw U; }
    @Override public void updateBlob(int c, java.io.InputStream x, long l) throws SQLException { throw U; }
    @Override public void updateBlob(String c, java.io.InputStream x, long l) throws SQLException { throw U; }
    @Override public void updateBlob(int c, java.io.InputStream x) throws SQLException { throw U; }
    @Override public void updateBlob(String c, java.io.InputStream x) throws SQLException { throw U; }
    @Override public void updateClob(int c, java.sql.Clob x) throws SQLException { throw U; }
    @Override public void updateClob(String c, java.sql.Clob x) throws SQLException { throw U; }
    @Override public void updateClob(int c, java.io.Reader r, long l) throws SQLException { throw U; }
    @Override public void updateClob(String c, java.io.Reader r, long l) throws SQLException { throw U; }
    @Override public void updateClob(int c, java.io.Reader r) throws SQLException { throw U; }
    @Override public void updateClob(String c, java.io.Reader r) throws SQLException { throw U; }
    @Override public void updateArray(int c, java.sql.Array x) throws SQLException { throw U; }
    @Override public void updateArray(String c, java.sql.Array x) throws SQLException { throw U; }
    @Override public void updateRowId(int c, java.sql.RowId x) throws SQLException { throw U; }
    @Override public void updateRowId(String c, java.sql.RowId x) throws SQLException { throw U; }
    @Override public void updateSQLXML(int c, java.sql.SQLXML x) throws SQLException { throw U; }
    @Override public void updateSQLXML(String c, java.sql.SQLXML x) throws SQLException { throw U; }
    @Override public void insertRow() throws SQLException { throw U; }
    @Override public void updateRow() throws SQLException { throw U; }
    @Override public void deleteRow() throws SQLException { throw U; }
    @Override public void refreshRow() {}
    @Override public void cancelRowUpdates() throws SQLException { throw U; }
    @Override public void moveToInsertRow() throws SQLException { throw U; }
    @Override public void moveToCurrentRow() {}
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw U; }
    @Override public boolean isWrapperFor(Class<?> iface) { return false; }

    private static List<String> parseLine(String line) {
        String t = line.trim();
        if (t.startsWith("[") && t.endsWith("]")) t = t.substring(1, t.length()-1);
        return Arrays.asList(t.split(",\\s*", -1));
    }
}
