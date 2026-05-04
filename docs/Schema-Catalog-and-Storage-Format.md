# Schema, Catalog, and Storage Format Guide

This guide explains how MiniDatabase currently models metadata and how table data is physically stored.

It covers:
- schema/catalog structures
- metadata persistence
- row and page layout
- table chaining and allocation
- current limitations and roadmap

---

## 1. Overview

MiniDatabase separates metadata from table data.

### Metadata side
Managed by the catalog layer:
- databases
- schemas
- tables
- columns
- root page references
- row-size-related layout information

### Data side
Managed by the storage layer:
- fixed-size pages in `minidb.data`
- table pages linked by next-page pointers
- serialized row payloads inside pages

This separation allows:
- DDL and metadata persistence to evolve separately
- storage engine to use catalog metadata for row encoding/decoding

---

## 2. Catalog model

Main metadata classes currently include:
- `Database`
- `Schema`
- `Table`
- `Column`
- `DataType`

### `Database`
Represents a logical database namespace containing schemas.

### `Schema`
Represents a namespace containing tables.

### `Table`
Current table metadata includes:
- table ID
- table name
- immutable column list
- column-name to index map
- computed row size
- root page ID

### `Column`
Current column metadata includes:
- column name
- logical type
- storage size
- constraint flags
- default value

### `DataType`
Current code supports logical types such as:
- `INT`
- `BIGINT`
- `DOUBLE`
- `BOOLEAN`
- `STRING`
- `DATE`
- `TIMESTAMP`
- SQL type parsing for `VARCHAR(n)` style storage sizing

---

## 3. Catalog persistence

Catalog metadata is persisted separately from table row bytes.

Current metadata file:
- `data/catalog.meta`

### Practical meaning
- DDL operations update catalog state
- table definitions survive restart
- table row data does not live in catalog metadata

### Stability note
The current metadata format should be treated as internal implementation format, not as a stable public versioned format.

---

## 4. How names resolve to physical storage

A fully qualified name such as:

`db.schema.table`

is resolved by the catalog into a `Table` object.

That `Table` object provides the storage layer with:
- column order
- storage sizes
- root page ID
- row width

Without catalog resolution, the storage layer would not know how to interpret bytes in `minidb.data`.

---

## 5. Table identity and root page

Each table currently has a `rootPageId`.

This is the entry point into the table’s page chain.

Conceptually:
```text
Table metadata -> rootPageId -> first table page -> next table page -> ...
```

This root pointer is what connects logical metadata to physical data layout.

---

## 6. Physical data file

Main row/page data file:
- `data/minidb.data`

Storage I/O happens through:
- `FileDiskManager`
- `BufferPoolManager`
- `Page`

Pages are fixed size.
All reads and writes occur in page-sized chunks.

---

## 7. Row format

MiniDatabase currently uses a fixed-width row format driven by table schema.

### Why fixed-width is used now
It simplifies:
- offset calculation
- page scan logic
- row rewrite logic during update/delete
- implementation complexity during early engine development

### Trade-off
This is easier to implement but less space-efficient than a slot-directory + variable-width tuple design.

---

## 8. Column storage behavior

`RowSerializer` converts logical row values into bytes using the table schema.

### Current storage behavior by type
- `INT` -> 4 bytes
- `BIGINT` -> 8 bytes
- `DOUBLE` -> 8 bytes
- `BOOLEAN` -> 1 byte
- `STRING` / `VARCHAR(n)` -> fixed-width UTF-8 byte region padded or truncated
- `DATE` -> numeric long-based representation in current serializer path
- `TIMESTAMP` -> numeric long-based representation in current serializer path

This gives the storage engine deterministic row size.

---

## 9. Table page format

Rows are stored inside `TablePage`.

Current header layout:
- bytes `[0..3]` = row count
- bytes `[4..7]` = next page ID

Header size:
- 8 bytes

After the header, rows are stored sequentially.

### Conceptual layout
```text
[ rowCount | nextPageId | row1 | row2 | row3 | ... ]
```

---

## 10. Multi-page table layout

A table can span multiple pages.
Pages are linked using the `nextPageId` in each page header.

Example:
```text
rootPageId -> page 5 -> page 8 -> page 9 -> -1
```

### Current behaviors enabled by this design
- scans traverse the chain from root to tail
- inserts append into the first page with available space or allocate a new tail page
- updates and deletes scan page-by-page

---

## 11. Page allocation and free pages

`TableStorage` currently maintains:
- monotonic page allocation counter for new pages
- in-memory `freePages` set for reusable empty pages

### Important limitation
The free-page list is not yet persisted across restart.
So free-page reuse is currently runtime-only.

---

## 12. Insert path and storage format

Insert flow in storage terms:

1. Engine resolves `Table`
2. SQL values are evaluated and ordered according to schema
3. `RowSerializer` produces fixed-width row bytes
4. `TableStorage` traverses page chain
5. If needed, a new page is allocated and linked
6. `TablePage.insertRow(...)` writes row bytes into payload region
7. Row count is incremented
8. Dirty page is flushed through buffer pool to disk

---

## 13. Read path and storage format

Read flow in storage terms:

1. `TableStorage.scan()` starts at `rootPageId`
2. Each `TablePage` reads `rowCount` and `nextPageId`
3. Row-sized byte slices are extracted from page payload
4. `RowSerializer.deserialize(...)` uses table schema to reconstruct row values
5. Logical rows are returned to executor/planner

This is why schema metadata is required for physical reads.

---

## 14. Update and delete under current format

### Update
- page rows are read into logical `Row` objects
- matching rows are modified
- page content is rewritten using overwrite logic

### Delete
- matching rows are removed from in-memory row list
- page is rewritten from remaining rows
- empty non-root pages may be unlinked and added to free-page set

This is simple and correct for the current engine stage, but not yet optimized like slot-based tuple storage in mature engines.

---

## 15. Constraints in metadata vs runtime enforcement

The metadata model can represent:
- `PRIMARY KEY`
- `NOT NULL`
- `UNIQUE`
- `DEFAULT`

Current reality:
- metadata support exists
- runtime enforcement may still be partial depending on execution path

This is normal at this stage of database-engine development.

---

## 16. Relation to indexing

Current indexing is managed through:
- `IndexManager`
- `BPlusTree`

Schema matters because:
- indexes target specific columns
- column values become index keys

Current limitations:
- index structures are in memory only
- indexes are not yet persisted as part of storage format
- planner does not yet automatically switch to index scans

---

## 17. Current format limitations

### Catalog limitations
- no versioned catalog migrations
- no persistent auth/role metadata yet
- no durable index metadata catalog yet

### Page/row limitations
- fixed-width rows
- no slot directory
- no MVCC tuple headers
- no page checksums yet
- no page-format version field yet

### Allocation limitations
- free-page tracking not durable yet
- page allocation metadata not centralized in a durable map structure

---

## 18. Recommended roadmap

### Next storage-format milestones
1. Persist free-page metadata
2. Add page checksums
3. Add storage-format version markers
4. Introduce tuple slot directory
5. Support more flexible variable-width storage layout
6. Add WAL-aware page metadata such as page LSN
7. Persist index structures and index catalog entries

---

## 19. Mental model

### Metadata layer
```text
catalog.meta
  -> databases
  -> schemas
  -> tables
  -> columns
  -> root page references
```

### Data layer
```text
minidb.data
  -> fixed-size pages
  -> chained table pages
  -> sequential fixed-width row payloads
```

The catalog explains what the storage bytes mean.
The storage file holds the actual row data.

---

## 20. Related documents

- `docs/Architecture-and-Execution-Guide.md`
- `docs/End-to-End-Data-Flow.md`
- `docs/Recovery-Design-and-WAL-Roadmap.md`
- `docs/Flow-and-Visualization.md`

