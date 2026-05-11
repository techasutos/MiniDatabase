package com.minidb.catalog;

import com.minidb.catalog.model.Column;
import com.minidb.catalog.model.DataType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogPersistenceTest {

	@Test
	void persistsDatabasesSchemasAndTables() throws Exception {
		Path metadataFile = Files.createTempDirectory("minidb-catalog-it-").resolve("catalog.meta");

		CatalogManager writer = new CatalogManager(new CatalogStore(metadataFile));
		writer.createDatabase("testdb");
		writer.createSchema("testdb", "analytics");
		writer.createTable(
				"testdb",
				"analytics",
				"users",
				List.of(new Column("id", DataType.INT), new Column("name", DataType.STRING))
		);

		CatalogManager reader = new CatalogManager(new CatalogStore(metadataFile));

		assertNotNull(reader.getDatabase("testdb"));
		assertNotNull(reader.getDatabase("testdb").getSchema("analytics"));
		assertNotNull(reader.getDatabase("testdb").getSchema("analytics").getTable("users"));
		assertEquals(List.of("testdb"), reader.listDatabaseNames());
		assertEquals(List.of("analytics", "public"), reader.listSchemaNames("testdb"));
		assertEquals(List.of("users"), reader.listTableNames("testdb", "analytics"));
		assertEquals(List.of("id:INT", "name:STRING"), reader.listColumnDefinitions("testdb", "analytics", "users"));
	}
}

