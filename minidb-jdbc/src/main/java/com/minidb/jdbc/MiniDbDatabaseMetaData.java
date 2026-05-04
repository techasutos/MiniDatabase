package com.minidb.jdbc;

import java.sql.*;

/**
 * Minimal DatabaseMetaData implementation for MiniDB.
 */
public class MiniDbDatabaseMetaData implements DatabaseMetaData {

    private final MiniDbConnection connection;

    MiniDbDatabaseMetaData(MiniDbConnection connection) {
        this.connection = connection;
    }

    @Override public String getDatabaseProductName()    { return "MiniDB"; }
    @Override public String getDatabaseProductVersion() { return "1.0"; }
    @Override public String getDriverName()             { return "MiniDB JDBC Driver"; }
    @Override public String getDriverVersion()          { return "1.0"; }
    @Override public int    getDriverMajorVersion()     { return 1; }
    @Override public int    getDriverMinorVersion()     { return 0; }
    @Override public String getURL()                    { return "jdbc:minidb://"; }
    @Override public String getUserName() throws SQLException { return null; }
    @Override public boolean isReadOnly() throws SQLException { return false; }
    @Override public Connection getConnection() { return connection; }
    @Override public boolean supportsTransactions() { return true; }
    @Override public boolean supportsBatchUpdates() { return false; }
    @Override public boolean supportsSavepoints() { return false; }
    @Override public boolean supportsStoredProcedures() { return false; }
    @Override public boolean allTablesAreSelectable() { return true; }
    @Override public boolean nullsAreSortedHigh() { return false; }
    @Override public boolean nullsAreSortedLow()  { return true; }
    @Override public boolean nullsAreSortedAtStart() { return false; }
    @Override public boolean nullsAreSortedAtEnd()   { return true; }
    @Override public boolean usesLocalFiles()    { return false; }
    @Override public boolean usesLocalFilePerTable() { return false; }
    @Override public boolean supportsMixedCaseIdentifiers() { return true; }
    @Override public boolean storesUpperCaseIdentifiers() { return false; }
    @Override public boolean storesLowerCaseIdentifiers() { return false; }
    @Override public boolean storesMixedCaseIdentifiers() { return true; }
    @Override public boolean supportsMixedCaseQuotedIdentifiers() { return true; }
    @Override public boolean storesUpperCaseQuotedIdentifiers() { return false; }
    @Override public boolean storesLowerCaseQuotedIdentifiers() { return false; }
    @Override public boolean storesMixedCaseQuotedIdentifiers() { return true; }
    @Override public String getIdentifierQuoteString() { return "`"; }
    @Override public String getSQLKeywords() { return ""; }
    @Override public String getNumericFunctions() { return "COUNT,SUM,AVG,MIN,MAX"; }
    @Override public String getStringFunctions()  { return ""; }
    @Override public String getSystemFunctions()  { return ""; }
    @Override public String getTimeDateFunctions(){ return ""; }
    @Override public String getSearchStringEscape(){ return "\\"; }
    @Override public String getExtraNameCharacters(){ return ""; }
    @Override public boolean supportsAlterTableWithAddColumn() { return false; }
    @Override public boolean supportsAlterTableWithDropColumn() { return false; }
    @Override public boolean supportsColumnAliasing() { return true; }
    @Override public boolean nullPlusNonNullIsNull() { return true; }
    @Override public boolean supportsConvert() { return false; }
    @Override public boolean supportsConvert(int fromType, int toType) { return false; }
    @Override public boolean supportsTableCorrelationNames() { return false; }
    @Override public boolean supportsDifferentTableCorrelationNames() { return false; }
    @Override public boolean supportsExpressionsInOrderBy() { return true; }
    @Override public boolean supportsOrderByUnrelated() { return true; }
    @Override public boolean supportsGroupBy() { return true; }
    @Override public boolean supportsGroupByUnrelated() { return true; }
    @Override public boolean supportsGroupByBeyondSelect() { return true; }
    @Override public boolean supportsLikeEscapeClause() { return false; }
    @Override public boolean supportsMultipleResultSets() { return false; }
    @Override public boolean supportsMultipleTransactions() { return false; }
    @Override public boolean supportsGetGeneratedKeys() { return false; }
    @Override public boolean supportsNonNullableColumns() { return true; }
    @Override public boolean supportsMinimumSQLGrammar() { return true; }
    @Override public boolean supportsCoreSQLGrammar() { return false; }
    @Override public boolean supportsExtendedSQLGrammar() { return false; }
    @Override public boolean supportsANSI92EntryLevelSQL() { return false; }
    @Override public boolean supportsANSI92IntermediateSQL() { return false; }
    @Override public boolean supportsANSI92FullSQL() { return false; }
    @Override public boolean supportsIntegrityEnhancementFacility() { return false; }
    @Override public boolean supportsOuterJoins() { return false; }
    @Override public boolean supportsFullOuterJoins() { return false; }
    @Override public boolean supportsLimitedOuterJoins() { return false; }
    @Override public String getSchemaTerm()   { return "schema"; }
    @Override public String getProcedureTerm(){ return "procedure"; }
    @Override public String getCatalogTerm()  { return "database"; }
    @Override public boolean isCatalogAtStart() { return true; }
    @Override public String getCatalogSeparator() { return "."; }
    @Override public boolean supportsSchemasInDataManipulation() { return true; }
    @Override public boolean supportsSchemasInProcedureCalls() { return false; }
    @Override public boolean supportsSchemasInTableDefinitions() { return true; }
    @Override public boolean supportsSchemasInIndexDefinitions() { return false; }
    @Override public boolean supportsSchemasInPrivilegeDefinitions() { return false; }
    @Override public boolean supportsCatalogsInDataManipulation() { return true; }
    @Override public boolean supportsCatalogsInProcedureCalls() { return false; }
    @Override public boolean supportsCatalogsInTableDefinitions() { return true; }
    @Override public boolean supportsCatalogsInIndexDefinitions() { return false; }
    @Override public boolean supportsCatalogsInPrivilegeDefinitions() { return false; }
    @Override public boolean supportsPositionedDelete() { return false; }
    @Override public boolean supportsPositionedUpdate() { return false; }
    @Override public boolean supportsSelectForUpdate() { return false; }
    @Override public boolean supportsStoredFunctionsUsingCallSyntax() { return false; }
    @Override public boolean autoCommitFailureClosesAllResultSets() { return false; }
    @Override public boolean supportsMultipleOpenResults() { return false; }
    @Override public int getMaxBinaryLiteralLength() { return 0; }
    @Override public int getMaxCharLiteralLength() { return 0; }
    @Override public int getMaxColumnNameLength() { return 128; }
    @Override public int getMaxColumnsInGroupBy() { return 0; }
    @Override public int getMaxColumnsInIndex() { return 1; }
    @Override public int getMaxColumnsInOrderBy() { return 0; }
    @Override public int getMaxColumnsInSelect() { return 0; }
    @Override public int getMaxColumnsInTable() { return 0; }
    @Override public int getMaxConnections() { return 50; }
    @Override public int getMaxCursorNameLength() { return 0; }
    @Override public int getMaxIndexLength() { return 0; }
    @Override public int getMaxSchemaNameLength() { return 128; }
    @Override public int getMaxProcedureNameLength() { return 0; }
    @Override public int getMaxCatalogNameLength() { return 128; }
    @Override public int getMaxRowSize() { return 0; }
    @Override public boolean doesMaxRowSizeIncludeBlobs() { return false; }
    @Override public int getMaxStatementLength() { return 0; }
    @Override public int getMaxStatements() { return 0; }
    @Override public int getMaxTableNameLength() { return 128; }
    @Override public int getMaxTablesInSelect() { return 1; }
    @Override public int getMaxUserNameLength() { return 64; }
    @Override public int getDefaultTransactionIsolation() { return Connection.TRANSACTION_READ_COMMITTED; }
    @Override public boolean supportsTransactionIsolationLevel(int level) { return level == Connection.TRANSACTION_READ_COMMITTED; }
    @Override public boolean supportsDataDefinitionAndDataManipulationTransactions() { return true; }
    @Override public boolean supportsDataManipulationTransactionsOnly() { return false; }
    @Override public boolean dataDefinitionCausesTransactionCommit() { return false; }
    @Override public boolean dataDefinitionIgnoredInTransactions() { return false; }
    @Override public boolean supportsSubqueriesInComparisons() { return false; }
    @Override public boolean supportsSubqueriesInExists() { return false; }
    @Override public boolean supportsSubqueriesInIns() { return false; }
    @Override public boolean supportsSubqueriesInQuantifieds() { return false; }
    @Override public boolean supportsCorrelatedSubqueries() { return false; }
    @Override public boolean supportsUnion() { return false; }
    @Override public boolean supportsUnionAll() { return false; }
    @Override public boolean supportsOpenCursorsAcrossCommit() { return false; }
    @Override public boolean supportsOpenCursorsAcrossRollback() { return false; }
    @Override public boolean supportsOpenStatementsAcrossCommit() { return true; }
    @Override public boolean supportsOpenStatementsAcrossRollback() { return true; }
    @Override public boolean supportsResultSetType(int type) { return type == ResultSet.TYPE_FORWARD_ONLY; }
    @Override public boolean supportsResultSetConcurrency(int type, int concurrency) { return concurrency == ResultSet.CONCUR_READ_ONLY; }
    @Override public boolean ownUpdatesAreVisible(int type) { return false; }
    @Override public boolean ownDeletesAreVisible(int type) { return false; }
    @Override public boolean ownInsertsAreVisible(int type) { return false; }
    @Override public boolean othersUpdatesAreVisible(int type) { return false; }
    @Override public boolean othersDeletesAreVisible(int type) { return false; }
    @Override public boolean othersInsertsAreVisible(int type) { return false; }
    @Override public boolean updatesAreDetected(int type) { return false; }
    @Override public boolean deletesAreDetected(int type) { return false; }
    @Override public boolean insertsAreDetected(int type) { return false; }
    @Override public boolean locatorsUpdateCopy() { return false; }
    @Override public boolean supportsResultSetHoldability(int holdability) { return false; }
    @Override public int getResultSetHoldability() { return ResultSet.CLOSE_CURSORS_AT_COMMIT; }
    @Override public int getDatabaseMajorVersion() { return 1; }
    @Override public int getDatabaseMinorVersion() { return 0; }
    @Override public int getJDBCMajorVersion() { return 4; }
    @Override public int getJDBCMinorVersion() { return 3; }
    @Override public int getSQLStateType() { return DatabaseMetaData.sqlStateSQL; }
    @Override public boolean generatedKeyAlwaysReturned() { return false; }
    @Override public boolean supportsNamedParameters() { return false; }
    @Override public ResultSet getProcedures(String c, String s, String p) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getProcedureColumns(String c, String s, String p, String col) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getTables(String c, String s, String t, String[] types) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getSchemas() throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getCatalogs() throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getTableTypes() throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getColumns(String c, String s, String t, String col) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getColumnPrivileges(String c, String s, String t, String col) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getTablePrivileges(String c, String s, String t) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getBestRowIdentifier(String c, String s, String t, int scope, boolean nullable) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getVersionColumns(String c, String s, String t) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getPrimaryKeys(String c, String s, String t) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getImportedKeys(String c, String s, String t) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getExportedKeys(String c, String s, String t) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getCrossReference(String pc, String ps, String pt, String fc, String fs, String ft) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getTypeInfo() throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getIndexInfo(String c, String s, String t, boolean unique, boolean approximate) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getUDTs(String c, String s, String t, int[] types) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getSuperTypes(String c, String s, String t) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getSuperTables(String c, String s, String t) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getAttributes(String c, String s, String t, String a) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getClientInfoProperties() throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getFunctions(String c, String s, String fn) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getFunctionColumns(String c, String s, String fn, String col) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public ResultSet getPseudoColumns(String c, String s, String t, String col) throws SQLException { return MiniDbResultSet.empty(); }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    @Override public boolean supportsStatementPooling() { return false; }
    @Override public java.sql.RowIdLifetime getRowIdLifetime() { return java.sql.RowIdLifetime.ROWID_UNSUPPORTED; }
    @Override public boolean allProceduresAreCallable() { return false; }
}
