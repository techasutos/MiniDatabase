import { useEffect, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { PlusCircle, RefreshCcw } from "lucide-react";
import { runQuery } from "../lib/api";
import { setEditorDraft } from "../lib/editor-draft";
import { useToast } from "../lib/toast";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";
import { Dialog } from "../components/ui/dialog";
import { Input } from "../components/ui/input";

type QueryCreationPanelProps = {
  onOpenSql?: () => void;
  initialKind?: "database" | "schema" | "table";
  initialOperation?: "create" | "update" | "delete";
  initialDatabase?: string;
  initialSchema?: string;
  initialTable?: string;
  resetKey?: string;
};

export function QueryCreationPanel({
  onOpenSql,
  initialKind = "database",
  initialOperation = "create",
  initialDatabase = "testdb",
  initialSchema = "analytics",
  initialTable = "users",
  resetKey
}: QueryCreationPanelProps) {
  const queryClient = useQueryClient();
  const { pushToast } = useToast();
  const [kind, setKind] = useState<"database" | "schema" | "table">(initialKind);
  const [operation, setOperation] = useState<"create" | "update" | "delete">(initialOperation);
  const [database, setDatabase] = useState(initialDatabase);
  const [schema, setSchema] = useState(initialSchema);
  const [table, setTable] = useState(initialTable);
  const [columns, setColumns] = useState("id INT, name STRING");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [confirmInput, setConfirmInput] = useState("");
  const [pendingSql, setPendingSql] = useState("");
  const [pendingPhrase, setPendingPhrase] = useState("");

  useEffect(() => {
    if (!resetKey) {
      return;
    }
    setKind(initialKind);
    setOperation(initialOperation);
    setDatabase(initialDatabase);
    setSchema(initialSchema);
    setTable(initialTable);
    setConfirmOpen(false);
    setConfirmInput("");
    setPendingSql("");
    setPendingPhrase("");
  }, [resetKey, initialKind, initialOperation, initialDatabase, initialSchema, initialTable]);
  const mutation = useMutation({
    mutationFn: runQuery,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["catalog", "databases"] });
      await queryClient.invalidateQueries({ queryKey: ["catalog", "schemas"] });
      await queryClient.invalidateQueries({ queryKey: ["catalog", "tables"] });
      pushToast({
        type: "success",
        title: "Object action completed",
        description: `Action ${operation.toUpperCase()} on ${kind.toUpperCase()} succeeded.`
      });
    },
    onError: (error) => {
      pushToast({
        type: "error",
        title: "Object action failed",
        description: (error as Error).message
      });
    }
  });

  const sql = buildSql(kind, operation, database, schema, table, columns);
  const qualifiedName = kind === "database"
    ? database
    : kind === "schema"
      ? `${database}.${schema}`
      : `${database}.${schema}.${table}`;

  const requestRunAction = () => {
    if (operation === "create") {
      mutation.mutate(sql);
      return;
    }

    const phrase = `${operation.toUpperCase()} ${qualifiedName}`;
    setPendingSql(sql);
    setPendingPhrase(phrase);
    setConfirmInput("");
    setConfirmOpen(true);
  };

  const confirmAndRun = () => {
    mutation.mutate(pendingSql);
    setConfirmOpen(false);
    setConfirmInput("");
    setPendingSql("");
    setPendingPhrase("");
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <PlusCircle className="h-5 w-5" /> Quick object action
        </CardTitle>
        <CardDescription>Create/update/delete databases, schemas, and tables directly from the UI, then refresh the explorer.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        <label className="grid gap-2 text-sm">
          Object type
          <select className="h-10 rounded-md border border-input bg-background px-3" value={kind} onChange={(e) => setKind(e.target.value as typeof kind)}>
            <option value="database">Database</option>
            <option value="schema">Schema</option>
            <option value="table">Table</option>
          </select>
        </label>

        <label className="grid gap-2 text-sm">
          Action
          <select
            className="h-10 rounded-md border border-input bg-background px-3"
            value={operation}
            onChange={(e) => setOperation(e.target.value as typeof operation)}
          >
            <option value="create">Create</option>
            <option value="update">Update</option>
            <option value="delete">Delete</option>
          </select>
        </label>

        <div className="grid gap-3 md:grid-cols-2">
          <label className="grid gap-2 text-sm">Database<Input value={database} onChange={(e) => setDatabase(e.target.value)} /></label>
          <label className="grid gap-2 text-sm">Schema<Input value={schema} onChange={(e) => setSchema(e.target.value)} /></label>
          <label className="grid gap-2 text-sm md:col-span-2">Table<Input value={table} onChange={(e) => setTable(e.target.value)} /></label>
          <label className="grid gap-2 text-sm md:col-span-2">Columns<Input value={columns} onChange={(e) => setColumns(e.target.value)} /></label>
        </div>

        <pre className="overflow-auto rounded-md bg-muted p-3 text-xs">{sql}</pre>

        <div className="flex gap-2">
          <Button onClick={requestRunAction} disabled={mutation.isPending}>
            <PlusCircle className="mr-2 h-4 w-4" /> {mutation.isPending ? "Running" : "Run action"}
          </Button>
          <Button
            variant="secondary"
            onClick={() => {
              setEditorDraft(sql);
              onOpenSql?.();
            }}
          >
            Open in SQL editor
          </Button>
          <Button variant="outline" onClick={() => queryClient.invalidateQueries({ queryKey: ["catalog"] })}>
            <RefreshCcw className="mr-2 h-4 w-4" /> Refresh explorer
          </Button>
        </div>

        {mutation.error ? <p className="text-sm text-red-600">{(mutation.error as Error).message}</p> : null}
        {mutation.data?.lines?.[0] ? <p className="text-sm text-green-700">{mutation.data.lines[0]}</p> : null}
      </CardContent>

      <Dialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title="Confirm destructive action"
        description="Type the exact confirmation phrase to proceed."
      >
        <div className="grid gap-3">
          <p className="text-sm">Confirmation phrase:</p>
          <pre className="rounded-md bg-muted p-2 text-xs">{pendingPhrase}</pre>
          <Input
            value={confirmInput}
            onChange={(e) => setConfirmInput(e.target.value)}
            placeholder="Type confirmation phrase"
          />
          <pre className="rounded-md bg-muted p-2 text-xs">{pendingSql}</pre>
          <div className="flex gap-2">
            <Button
              variant="outline"
              onClick={() => {
                setConfirmOpen(false);
                setConfirmInput("");
                setPendingSql("");
                setPendingPhrase("");
              }}
            >
              Cancel
            </Button>
            <Button
              onClick={confirmAndRun}
              disabled={confirmInput.trim() !== pendingPhrase || mutation.isPending}
            >
              Confirm and run
            </Button>
          </div>
        </div>
      </Dialog>
    </Card>
  );
}

function buildSql(
  kind: "database" | "schema" | "table",
  operation: "create" | "update" | "delete",
  database: string,
  schema: string,
  table: string,
  columns: string
) {
  const qdb = `${database}`;
  const qschema = `${database}.${schema}`;
  const qtable = `${database}.${schema}.${table}`;

  if (operation === "update") {
    if (kind === "database") return `-- ALTER DATABASE is not implemented yet for MiniDB\n-- Suggested: create new DB and migrate objects`;
    if (kind === "schema") return `-- ALTER SCHEMA is not implemented yet for MiniDB\n-- Suggested: create new schema and move objects`;
    return `ALTER TABLE ${qtable} ADD COLUMN new_column STRING;`;
  }

  if (operation === "create") {
    if (kind === "database") return `CREATE DATABASE ${qdb};`;
    if (kind === "schema") return `CREATE SCHEMA ${qschema};`;
    return `CREATE TABLE ${qtable} (${columns});`;
  }
  if (kind === "database") return `DROP DATABASE ${qdb};`;
  if (kind === "schema") return `DROP SCHEMA ${qschema};`;
  return `DROP TABLE ${qtable};`;
}

