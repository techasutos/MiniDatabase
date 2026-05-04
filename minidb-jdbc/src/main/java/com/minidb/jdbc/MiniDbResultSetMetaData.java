package com.minidb.jdbc;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * ResultSet metadata backed by a column name list.
 */
public class MiniDbResultSetMetaData implements ResultSetMetaData {

    private final List<String> columns;

    MiniDbResultSetMetaData(List<String> columns) {
        this.columns = columns;
    }

    @Override public int    getColumnCount()                        { return columns.size(); }
    @Override public String getColumnName(int col)   throws SQLException { return col(col); }
    @Override public String getColumnLabel(int col)  throws SQLException { return col(col); }
    @Override public int    getColumnType(int col)                  { return java.sql.Types.VARCHAR; }
    @Override public String getColumnTypeName(int col)              { return "VARCHAR"; }
    @Override public String getColumnClassName(int col)             { return String.class.getName(); }
    @Override public int    getColumnDisplaySize(int col)           { return 255; }
    @Override public int    getPrecision(int col)                   { return 255; }
    @Override public int    getScale(int col)                       { return 0; }
    @Override public String getTableName(int col)                   { return ""; }
    @Override public String getSchemaName(int col)                  { return ""; }
    @Override public String getCatalogName(int col)                 { return ""; }
    @Override public boolean isAutoIncrement(int col)               { return false; }
    @Override public boolean isCaseSensitive(int col)               { return false; }
    @Override public boolean isSearchable(int col)                  { return true; }
    @Override public boolean isCurrency(int col)                    { return false; }
    @Override public int     isNullable(int col)                    { return columnNullableUnknown; }
    @Override public boolean isSigned(int col)                      { return false; }
    @Override public boolean isReadOnly(int col)                    { return true; }
    @Override public boolean isWritable(int col)                    { return false; }
    @Override public boolean isDefinitelyWritable(int col)          { return false; }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new java.sql.SQLFeatureNotSupportedException(); }
    @Override public boolean isWrapperFor(Class<?> iface)           { return false; }

    private String col(int col) throws SQLException {
        if (col < 1 || col > columns.size()) throw new SQLException("Column " + col + " out of range");
        return columns.get(col - 1);
    }
}

