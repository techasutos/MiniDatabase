import { useEffect, useMemo, useState, type MouseEvent as ReactMouseEvent } from "react";
import { createPortal } from "react-dom";
import { useQuery } from "@tanstack/react-query";
import { ChevronRight, Database, FilePlus2, Layers3, MoreHorizontal, PencilLine, PlusCircle, RefreshCcw, Table2, Trash2, WandSparkles } from "lucide-react";
import { getColumns, getDatabases, getSchemas, getTables, runQuery } from "../lib/api";
import { setEditorDraft } from "../lib/editor-draft";
import { useToast } from "../lib/toast";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";
import { Dialog } from "../components/ui/dialog";
import { Table, TBody, TD, TH, THead, TR } from "../components/ui/table";
import { QueryCreationPanel } from "./query-creation-panel";

type SchemaPanelProps = {
  onOpenSql?: () => void;
};

type ObjectActionConfig = {
  kind: "database" | "schema" | "table";
  operation: "create" | "update" | "delete";
  database: string;
  schema: string;
  table: string;
  resetKey: string;
};

type ContextMenuState = {
  x: number;
  y: number;
  scope: "database" | "schema" | "table";
  database: string;
  schema: string;
  table: string;
};

export function SchemaPanel({ onOpenSql }: SchemaPanelProps) {
  const { pushToast } = useToast();
  const databasesQuery = useQuery({ queryKey: ["catalog", "databases"], queryFn: getDatabases, refetchInterval: 10000 });
  const [selectedDatabase, setSelectedDatabase] = useState("");
  const [selectedSchema, setSelectedSchema] = useState("");
  const [selectedTable, setSelectedTable] = useState("");
  const [expandedDatabases, setExpandedDatabases] = useState<Set<string>>(new Set());
  const [expandedSchemas, setExpandedSchemas] = useState<Set<string>>(new Set());
  const [objectDialogOpen, setObjectDialogOpen] = useState(false);
  const [actionConfig, setActionConfig] = useState<ObjectActionConfig>({
    kind: "database",
    operation: "create",
    database: "testdb",
    schema: "public",
    table: "new_table",
    resetKey: "initial"
  });
  const [contextMenu, setContextMenu] = useState<ContextMenuState | null>(null);

  const databaseItems = databasesQuery.data?.items ?? [];

  const schemaKey = (database: string, schema: string) => `${database}::${schema}`;

  const expandDatabase = (database: string) => {
    setExpandedDatabases((prev) => new Set(prev).add(database));
  };

  const collapseDatabase = (database: string) => {
    setExpandedDatabases((prev) => {
      const next = new Set(prev);
      next.delete(database);
      return next;
    });
  };

  const expandSchema = (database: string, schema: string) => {
    setExpandedSchemas((prev) => new Set(prev).add(schemaKey(database, schema)));
  };

  const collapseSchema = (database: string, schema: string) => {
    setExpandedSchemas((prev) => {
      const next = new Set(prev);
      next.delete(schemaKey(database, schema));
      return next;
    });
  };

  useEffect(() => {
    if (databaseItems.length === 0) {
      setSelectedDatabase("");
      setSelectedSchema("");
      setSelectedTable("");
      setExpandedDatabases(new Set());
      setExpandedSchemas(new Set());
      return;
    }
    if (!selectedDatabase || !databaseItems.some((db) => db.name === selectedDatabase)) {
      const firstDatabase = databaseItems[0].name;
      setSelectedDatabase(firstDatabase);
      expandDatabase(firstDatabase);
    }
  }, [databaseItems, selectedDatabase]);

  const schemasQuery = useQuery({
    queryKey: ["catalog", "schemas", selectedDatabase],
    queryFn: () => getSchemas(selectedDatabase),
    enabled: Boolean(selectedDatabase),
    refetchInterval: 10000
  });

  const schemaItems = schemasQuery.data?.items ?? [];

  useEffect(() => {
    if (schemaItems.length === 0) {
      setSelectedSchema("");
      setSelectedTable("");
      return;
    }
    if (!selectedSchema || !schemaItems.some((schema) => schema.name === selectedSchema)) {
      const firstSchema = schemaItems[0].name;
      setSelectedSchema(firstSchema);
      expandSchema(selectedDatabase, firstSchema);
    }
  }, [schemaItems, selectedSchema]);

  const tablesQuery = useQuery({
    queryKey: ["catalog", "tables", selectedDatabase, selectedSchema],
    queryFn: () => getTables(selectedDatabase, selectedSchema),
    enabled: Boolean(selectedDatabase && selectedSchema),
    refetchInterval: 10000
  });

  const tableItems = tablesQuery.data?.items ?? [];

  useEffect(() => {
    if (tableItems.length === 0) {
      setSelectedTable("");
      return;
    }
    if (!selectedTable || !tableItems.some((t) => t.name === selectedTable)) {
      setSelectedTable(tableItems[0].name);
    }
  }, [tableItems, selectedTable]);

  const selectDatabase = (database: string) => {
    setSelectedDatabase(database);
    setSelectedSchema("");
    setSelectedTable("");
    expandDatabase(database);
  };

  const selectSchema = (schema: string) => {
    setSelectedSchema(schema);
    setSelectedTable("");
    expandSchema(selectedDatabase, schema);
  };

  const selectTable = (table: string) => {
    setSelectedTable(table);
  };

  const tablePreviewSql = selectedDatabase && selectedSchema && selectedTable
    ? `SELECT * FROM ${selectedDatabase}.${selectedSchema}.${selectedTable} LIMIT 25`
    : "";

  const columnsQuery = useQuery({
    queryKey: ["catalog", "columns", selectedDatabase, selectedSchema, selectedTable],
    queryFn: () => getColumns(selectedDatabase, selectedSchema, selectedTable),
    enabled: Boolean(selectedDatabase && selectedSchema && selectedTable),
    refetchInterval: 10000
  });

  const columns = columnsQuery.data?.items ?? [];

  const emitSqlDraft = (sql: string) => {
    setEditorDraft(sql);
    onOpenSql?.();
  };

  const openObjectDialog = (
    kind: ObjectActionConfig["kind"],
    operation: ObjectActionConfig["operation"],
    database: string,
    schema: string,
    table: string
  ) => {
    setActionConfig({
      kind,
      operation,
      database,
      schema,
      table,
      resetKey: `${Date.now()}-${kind}-${operation}-${database}-${schema}-${table}`
    });
    setObjectDialogOpen(true);
    setContextMenu(null);
  };

  const openContextMenu = (
    event: ReactMouseEvent,
    scope: ContextMenuState["scope"],
    database: string,
    schema: string,
    table: string
  ) => {
    event.preventDefault();
    setContextMenu({
      x: event.clientX,
      y: event.clientY,
      scope,
      database,
      schema,
      table
    });
  };

  const openDropdownMenu = (
    event: ReactMouseEvent,
    scope: ContextMenuState["scope"],
    database: string,
    schema: string,
    table: string
  ) => {
    event.preventDefault();
    event.stopPropagation();
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    setContextMenu({
      x: rect.left,
      y: rect.bottom + 6,
      scope,
      database,
      schema,
      table
    });
  };

  useEffect(() => {
    if (!contextMenu) {
      return;
    }
    const onGlobalClick = () => setContextMenu(null);
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setContextMenu(null);
      }
    };
    window.addEventListener("click", onGlobalClick);
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("click", onGlobalClick);
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [contextMenu]);

  const dmlTemplate = (
    kind: "select" | "insert" | "update" | "delete",
    database = selectedDatabase,
    schema = selectedSchema,
    table = selectedTable,
    templateColumns = columns
  ) => {
    const qn = `${database}.${schema}.${table}`;
    if (kind === "select") return `SELECT * FROM ${qn} LIMIT 100;`;
    if (kind === "insert") {
      const colNames = templateColumns.map((c) => c.name);
      const values = templateColumns.map((c) => `/* ${c.name}:${c.type} */`);
      return colNames.length > 0
        ? `INSERT INTO ${qn} (${colNames.join(", ")}) VALUES (${values.join(", ")});`
        : `INSERT INTO ${qn} VALUES (/* values */);`;
    }
    if (kind === "update") {
      const assignments = templateColumns.map((c) => `${c.name} = /* ${c.type} */`).join(", ");
      return assignments.length > 0
        ? `UPDATE ${qn} SET ${assignments} WHERE /* condition */;`
        : `UPDATE ${qn} SET /* column = value */ WHERE /* condition */;`;
    }
    return `DELETE FROM ${qn} WHERE /* condition */;`;
  };

  const ddlTemplate = (
    kind: "create" | "drop" | "alter",
    database = selectedDatabase,
    schema = selectedSchema,
    table = selectedTable
  ) => {
    const qn = `${database}.${schema}.${table}`;
    if (kind === "create") return `CREATE TABLE ${qn} (id INT, name STRING);`;
    if (kind === "alter") return `ALTER TABLE ${qn} ADD COLUMN new_column STRING;`;
    return `DROP TABLE ${qn};`;
  };

  const previewQuery = useQuery({
    queryKey: ["catalog", "preview", selectedDatabase, selectedSchema, selectedTable],
    queryFn: () => runQuery(tablePreviewSql),
    enabled: Boolean(tablePreviewSql),
    refetchInterval: 10000
  });

  const previewRows = previewQuery.data?.rows ?? [];
  const previewColumns = previewRows[0]?.length ?? 0;

  const tree = useMemo(() => {
    return databaseItems.map((db) => ({
      name: db.name,
      selected: db.name === selectedDatabase,
      expanded: expandedDatabases.has(db.name),
      schemas: db.name === selectedDatabase && expandedDatabases.has(db.name)
        ? schemaItems.map((schema) => ({
            name: schema.name,
            selected: schema.name === selectedSchema,
            expanded: expandedSchemas.has(schemaKey(db.name, schema.name)),
            tables: schema.name === selectedSchema && expandedSchemas.has(schemaKey(db.name, schema.name)) ? tableItems : []
          }))
        : []
    }));
  }, [databaseItems, schemaItems, tableItems, selectedDatabase, selectedSchema, expandedDatabases, expandedSchemas]);

  const emitTemplateFromMenu = (sql: string, label: string) => {
    emitSqlDraft(sql);
    setContextMenu(null);
    pushToast({
      type: "success",
      title: "SQL template added",
      description: `${label} template opened in SQL editor.`
    });
  };

  const schemaSidebarSlot = typeof document !== "undefined"
    ? document.getElementById("schema-catalog-sidebar-slot")
    : null;

  const catalogCard = (
    <Card className={schemaSidebarSlot ? "" : "lg:sticky lg:top-4 lg:self-start"}>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><Database className="h-5 w-5" /> Catalog</CardTitle>
        <CardDescription>{databaseItems.length} database(s) discovered</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-2">
        {tree.map((db) => (
          <div key={db.name} className="grid gap-1">
            <div
              role="button"
              tabIndex={0}
              className={[
                "flex items-center justify-between gap-2 rounded-md border px-3 py-2 text-left transition hover:bg-accent",
                db.selected ? "border-primary bg-primary/5" : ""
              ].join(" ")}
              onClick={() => selectDatabase(db.name)}
              onKeyDown={(event) => {
                if (event.key === "Enter" || event.key === " ") {
                  event.preventDefault();
                  selectDatabase(db.name);
                }
              }}
              onContextMenu={(e) => openContextMenu(e, "database", db.name, "public", "new_table")}
              aria-haspopup="menu"
              aria-label={`Open ${db.name} database`}
            >
              <span className="flex min-w-0 items-center gap-2">
                <button
                  type="button"
                  className="rounded p-0.5 text-muted-foreground hover:bg-background"
                  onClick={(event) => {
                    event.stopPropagation();
                    if (db.expanded) {
                      collapseDatabase(db.name);
                    } else {
                      expandDatabase(db.name);
                    }
                  }}
                  aria-label={`${db.expanded ? "Collapse" : "Expand"} database ${db.name}`}
                  title={db.expanded ? "Collapse database" : "Expand database"}
                >
                  <ChevronRight className={[
                    "h-4 w-4 text-muted-foreground transition-transform",
                    db.expanded ? "rotate-90" : ""
                  ].join(" ")} />
                </button>
                <Database className="h-4 w-4 text-muted-foreground" />
                <span className="truncate font-medium">{db.name}</span>
              </span>
              <span className="flex items-center gap-1">
                <button
                  type="button"
                  className="rounded p-1 text-muted-foreground hover:bg-background"
                  onClick={(e) => openDropdownMenu(e, "database", db.name, "public", "new_table")}
                  onContextMenu={(e) => openContextMenu(e, "database", db.name, "public", "new_table")}
                  aria-label={`Open actions for database ${db.name}`}
                  aria-haspopup="menu"
                  title="Open management menu"
                >
                  <MoreHorizontal className="h-4 w-4" />
                </button>
              </span>
            </div>

            {db.expanded && db.selected ? (
              <div className="ml-4 grid gap-1 border-l pl-3">
                {db.schemas.length > 0 ? db.schemas.map((schema) => (
                  <div key={schema.name} className="grid gap-1">
                    <div
                      role="button"
                      tabIndex={0}
                      className={[
                        "flex items-center justify-between gap-2 rounded-md border px-3 py-2 text-left transition hover:bg-accent",
                        schema.selected ? "border-primary bg-primary/5" : "border-transparent"
                      ].join(" ")}
                      onClick={() => selectSchema(schema.name)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          selectSchema(schema.name);
                        }
                      }}
                      onContextMenu={(e) => openContextMenu(e, "schema", db.name, schema.name, "new_table")}
                      aria-haspopup="menu"
                      aria-label={`Open ${db.name}.${schema.name} schema`}
                    >
                      <span className="flex min-w-0 items-center gap-2">
                        <button
                          type="button"
                          className="rounded p-0.5 text-muted-foreground hover:bg-background"
                          onClick={(event) => {
                            event.stopPropagation();
                            if (schema.expanded) {
                              collapseSchema(db.name, schema.name);
                            } else {
                              expandSchema(db.name, schema.name);
                            }
                          }}
                          aria-label={`${schema.expanded ? "Collapse" : "Expand"} schema ${db.name}.${schema.name}`}
                          title={schema.expanded ? "Collapse schema" : "Expand schema"}
                        >
                          <ChevronRight className={[
                            "h-4 w-4 text-muted-foreground transition-transform",
                            schema.expanded ? "rotate-90" : ""
                          ].join(" ")} />
                        </button>
                        <Layers3 className="h-4 w-4 text-muted-foreground" />
                        <span className="truncate">{schema.name}</span>
                      </span>
                      <span className="flex items-center gap-1">
                        <button
                          type="button"
                          className="rounded p-1 text-muted-foreground hover:bg-background"
                          onClick={(e) => openDropdownMenu(e, "schema", db.name, schema.name, "new_table")}
                          onContextMenu={(e) => openContextMenu(e, "schema", db.name, schema.name, "new_table")}
                          aria-label={`Open actions for schema ${db.name}.${schema.name}`}
                          aria-haspopup="menu"
                          title="Open management menu"
                        >
                          <MoreHorizontal className="h-4 w-4" />
                        </button>
                      </span>
                    </div>

                    {schema.selected && schema.expanded ? (
                      <div className="ml-4 grid gap-1 border-l pl-3">
                        {schema.tables.map((table) => (
                          <div
                            key={table.name}
                            role="button"
                            tabIndex={0}
                            className={[
                              "flex items-center justify-between gap-2 rounded-md border px-3 py-2 text-left transition hover:bg-accent",
                              selectedTable === table.name ? "border-primary bg-primary/5" : "border-transparent"
                            ].join(" ")}
                            onClick={() => {
                              selectTable(table.name);
                              emitSqlDraft(`SELECT * FROM ${db.name}.${schema.name}.${table.name} LIMIT 100;`);
                            }}
                            onKeyDown={(event) => {
                              if (event.key === "Enter" || event.key === " ") {
                                event.preventDefault();
                                selectTable(table.name);
                                emitSqlDraft(`SELECT * FROM ${db.name}.${schema.name}.${table.name} LIMIT 100;`);
                              }
                            }}
                            onContextMenu={(e) => openContextMenu(e, "table", db.name, schema.name, table.name)}
                            aria-haspopup="menu"
                            aria-label={`Open ${db.name}.${schema.name}.${table.name} table`}
                          >
                            <span className="flex min-w-0 items-center gap-2">
                              <Table2 className="h-4 w-4 text-muted-foreground" />
                              <span className="truncate font-medium">{table.name}</span>
                            </span>
                            <button
                              type="button"
                              className="rounded p-1 text-muted-foreground hover:bg-background"
                              onClick={(e) => openDropdownMenu(e, "table", db.name, schema.name, table.name)}
                              onContextMenu={(e) => openContextMenu(e, "table", db.name, schema.name, table.name)}
                              aria-label={`Open actions for table ${db.name}.${schema.name}.${table.name}`}
                              aria-haspopup="menu"
                              title="Open management menu"
                            >
                              <MoreHorizontal className="h-4 w-4" />
                            </button>
                          </div>
                        ))}
                        {schema.tables.length === 0 ? <p className="px-3 py-2 text-sm text-muted-foreground">No tables found for selected schema.</p> : null}
                      </div>
                    ) : null}
                  </div>
                )) : <p className="px-3 py-2 text-sm text-muted-foreground">No schemas found for selected database.</p>}
              </div>
            ) : null}
          </div>
        ))}
        {databaseItems.length === 0 ? <p className="text-sm text-muted-foreground">No databases found.</p> : null}
      </CardContent>
    </Card>
  );

  return (
    <div className="grid gap-4">
      <Card>
        <CardHeader className="flex-row items-center justify-between gap-4 space-y-0">
          <div>
            <CardTitle>Schema explorer</CardTitle>
            <CardDescription>Use the left-side drill-down tree to browse databases, schemas, and tables. Right-click any node or use the action button beside it.</CardDescription>
          </div>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => setObjectDialogOpen(true)}>
              <PlusCircle className="mr-2 h-4 w-4" /> Object actions
            </Button>
            <Button variant="outline" onClick={() => databasesQuery.refetch()}>
              <RefreshCcw className="mr-2 h-4 w-4" /> Refresh
            </Button>
          </div>
        </CardHeader>
        <CardContent className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          <span>Selected path:</span>
          <Badge className="border-muted-foreground/30">{selectedDatabase || "No database"}</Badge>
          <Badge className="border-muted-foreground/30">{selectedSchema || "No schema"}</Badge>
          <Badge className="border-muted-foreground/30">{selectedTable || "No table"}</Badge>
        </CardContent>
      </Card>

      <div className={schemaSidebarSlot ? "grid gap-4" : "grid gap-4 lg:grid-cols-[320px,1fr]"}>
        {schemaSidebarSlot ? null : catalogCard}

        <div className="grid gap-4">
          <Card>
            <CardHeader className="flex-row items-center justify-between gap-4 space-y-0">
              <div>
                <CardTitle>Table data preview</CardTitle>
                <CardDescription>
                  {tablePreviewSql || "Select a table from the left drill-down menu to preview data"}
                </CardDescription>
              </div>
              <Button variant="outline" onClick={() => previewQuery.refetch()} disabled={!tablePreviewSql}>
                <RefreshCcw className="mr-2 h-4 w-4" /> Refresh data
              </Button>
            </CardHeader>
            <CardContent>
              {previewRows.length > 0 ? (
                <div className="overflow-x-auto rounded-md border">
                  <Table>
                    <THead>
                      <TR>
                        {Array.from({ length: previewColumns }).map((_, i) => (
                          <TH key={i}>col_{i + 1}</TH>
                        ))}
                      </TR>
                    </THead>
                    <TBody>
                      {previewRows.map((row, rowIndex) => (
                        <TR key={rowIndex}>
                          {row.map((value, cellIndex) => (
                            <TD key={cellIndex}>{value}</TD>
                          ))}
                        </TR>
                      ))}
                    </TBody>
                  </Table>
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">
                  {previewQuery.isLoading ? "Loading table preview..." : "No rows returned for current preview."}
                </p>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Generate SQL from selected table</CardTitle>
              <CardDescription>
                Click a table in the left sidebar, then generate DDL/DML templates. Draft opens in SQL editor with multiline support.
              </CardDescription>
            </CardHeader>
            <CardContent className="grid gap-2 md:grid-cols-3">
              <Button variant="secondary" disabled={!selectedTable} onClick={() => emitSqlDraft(dmlTemplate("select"))}>SELECT template</Button>
              <Button variant="secondary" disabled={!selectedTable} onClick={() => emitSqlDraft(dmlTemplate("insert"))}>INSERT template</Button>
              <Button variant="secondary" disabled={!selectedTable} onClick={() => emitSqlDraft(dmlTemplate("update"))}>
                <PencilLine className="mr-2 h-4 w-4" /> UPDATE template
              </Button>
              <Button variant="secondary" disabled={!selectedTable} onClick={() => emitSqlDraft(dmlTemplate("delete"))}>
                <Trash2 className="mr-2 h-4 w-4" /> DELETE template
              </Button>
              <Button variant="outline" disabled={!selectedTable} onClick={() => emitSqlDraft(ddlTemplate("alter"))}>
                <WandSparkles className="mr-2 h-4 w-4" /> ALTER TABLE template
              </Button>
              <Button variant="outline" disabled={!selectedTable} onClick={() => emitSqlDraft(ddlTemplate("create"))}>
                <FilePlus2 className="mr-2 h-4 w-4" /> CREATE TABLE template
              </Button>
              <Button variant="outline" disabled={!selectedTable} onClick={() => emitSqlDraft(ddlTemplate("drop"))}>DROP TABLE template</Button>
            </CardContent>
          </Card>
        </div>
      </div>

      {schemaSidebarSlot ? createPortal(catalogCard, schemaSidebarSlot) : null}

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Sequences</CardTitle>
            <CardDescription>MiniDB does not expose sequence metadata yet; this bucket is reserved for future catalog support.</CardDescription>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            No sequence objects are currently reported by the backend.
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Other objects</CardTitle>
            <CardDescription>Reserved for views, indexes, procedures, and future catalog object kinds.</CardDescription>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            Additional object types will appear here as the server catalog expands.
          </CardContent>
        </Card>
      </div>

      <Dialog
        open={objectDialogOpen}
        onOpenChange={setObjectDialogOpen}
        title="Create / Update / Delete objects"
        description="Run object-level actions from a focused dialog, then refresh explorer or open generated SQL in editor."
      >
        <QueryCreationPanel
          onOpenSql={onOpenSql}
          initialKind={actionConfig.kind}
          initialOperation={actionConfig.operation}
          initialDatabase={actionConfig.database}
          initialSchema={actionConfig.schema}
          initialTable={actionConfig.table}
          resetKey={actionConfig.resetKey}
        />
      </Dialog>

      {contextMenu ? (
        <div
          className="fixed z-50 min-w-[260px] rounded-md border bg-background p-1 shadow-xl"
          style={{ left: contextMenu.x, top: contextMenu.y }}
          onClick={(event) => event.stopPropagation()}
        >
          <div className="px-2 py-1 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Object management</div>
          <button
            type="button"
            className="w-full rounded px-2 py-1 text-left text-sm hover:bg-accent"
            onClick={() => {
              if (contextMenu.scope === "database") {
                openObjectDialog("database", "create", contextMenu.database, "public", "new_table");
              } else if (contextMenu.scope === "schema") {
                openObjectDialog("schema", "create", contextMenu.database, contextMenu.schema, "new_table");
              } else {
                openObjectDialog("table", "create", contextMenu.database, contextMenu.schema, contextMenu.table);
              }
            }}
          >
            Create {contextMenu.scope}
          </button>
          <button
            type="button"
            className="w-full rounded px-2 py-1 text-left text-sm hover:bg-accent"
            onClick={() => {
              openObjectDialog(
                contextMenu.scope,
                "update",
                contextMenu.database,
                contextMenu.schema,
                contextMenu.scope === "table" ? contextMenu.table : "new_table"
              );
            }}
          >
            Update {contextMenu.scope}
          </button>
          <button
            type="button"
            className="w-full rounded px-2 py-1 text-left text-sm text-red-600 hover:bg-accent"
            onClick={() => {
              openObjectDialog(
                contextMenu.scope,
                "delete",
                contextMenu.database,
                contextMenu.schema,
                contextMenu.scope === "table" ? contextMenu.table : "new_table"
              );
            }}
          >
            Delete {contextMenu.scope}
          </button>

          {contextMenu.scope === "table" ? (
            <>
              <div className="my-1 border-t" />
              <div className="px-2 py-1 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Table templates</div>
              {(() => {
                const isSelected =
                  contextMenu.database === selectedDatabase
                  && contextMenu.schema === selectedSchema
                  && contextMenu.table === selectedTable;
                const templateColumns = isSelected ? columns : [];
                return (
                  <>
              <button
                type="button"
                className="w-full rounded px-2 py-1 text-left text-sm hover:bg-accent"
                onClick={() => emitTemplateFromMenu(`SELECT * FROM ${contextMenu.database}.${contextMenu.schema}.${contextMenu.table} LIMIT 100;`, "SELECT")}
              >
                DML: SELECT
              </button>
              <button
                type="button"
                className="w-full rounded px-2 py-1 text-left text-sm hover:bg-accent"
                onClick={() => emitTemplateFromMenu(dmlTemplate("insert", contextMenu.database, contextMenu.schema, contextMenu.table, templateColumns), "INSERT")}
              >
                DML: INSERT
              </button>
              <button
                type="button"
                className="w-full rounded px-2 py-1 text-left text-sm hover:bg-accent"
                onClick={() => emitTemplateFromMenu(dmlTemplate("update", contextMenu.database, contextMenu.schema, contextMenu.table, templateColumns), "UPDATE")}
              >
                DML: UPDATE
              </button>
              <button
                type="button"
                className="w-full rounded px-2 py-1 text-left text-sm hover:bg-accent"
                onClick={() => emitTemplateFromMenu(dmlTemplate("delete", contextMenu.database, contextMenu.schema, contextMenu.table, templateColumns), "DELETE")}
              >
                DML: DELETE
              </button>
              <button
                type="button"
                className="w-full rounded px-2 py-1 text-left text-sm hover:bg-accent"
                onClick={() => emitTemplateFromMenu(ddlTemplate("create", contextMenu.database, contextMenu.schema, contextMenu.table), "CREATE TABLE")}
              >
                DDL: CREATE TABLE
              </button>
              <button
                type="button"
                className="w-full rounded px-2 py-1 text-left text-sm hover:bg-accent"
                onClick={() => emitTemplateFromMenu(ddlTemplate("alter", contextMenu.database, contextMenu.schema, contextMenu.table), "ALTER TABLE")}
              >
                DDL: ALTER TABLE
              </button>
              <button
                type="button"
                className="w-full rounded px-2 py-1 text-left text-sm text-red-600 hover:bg-accent"
                onClick={() => emitTemplateFromMenu(ddlTemplate("drop", contextMenu.database, contextMenu.schema, contextMenu.table), "DROP TABLE")}
              >
                DDL: DROP TABLE
              </button>
                  </>
                );
              })()}
            </>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

