import { useEffect, useMemo, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { BookMarked, History, Play, Plus, RotateCcw, Save } from "lucide-react";
import { runQuery } from "../lib/api";
import { consumeEditorDraft, subscribeEditorDraft } from "../lib/editor-draft";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";
import { Table, TBody, TD, TH, THead, TR } from "../components/ui/table";
import { Textarea } from "../components/ui/textarea";

const DEFAULT_SQL = "SELECT * FROM testdb.analytics.users";
const HISTORY_KEY = "minidb.dashboard.queryHistory";
const SNIPPETS_KEY = "minidb.dashboard.snippets";

type Snippet = { name: string; sql: string };

export function SqlEditorPanel() {
  const [sql, setSql] = useState(DEFAULT_SQL);
  const [history, setHistory] = useState<string[]>([]);
  const [snippets, setSnippets] = useState<Snippet[]>([]);
  const query = useMutation({ mutationFn: runQuery });

  const rows = query.data?.rows ?? [];
  const columnCount = rows[0]?.length ?? 0;

  useEffect(() => {
    setHistory(readJson<string[]>(HISTORY_KEY, []));
    setSnippets(readJson<Snippet[]>(SNIPPETS_KEY, [
      { name: "List users", sql: "SELECT * FROM testdb.analytics.users" },
      { name: "Show capabilities", sql: "CAPABILITIES" }
    ]));

    const initialDraft = consumeEditorDraft();
    if (initialDraft) {
      setSql(initialDraft);
    }

    return subscribeEditorDraft(() => {
      const draft = consumeEditorDraft();
      if (draft) {
        setSql(draft);
      }
    });
  }, []);

  useEffect(() => {
    if (query.isSuccess && query.data?.sql) {
      setHistory((current) => {
        const next = [query.data.sql, ...current.filter((item) => item !== query.data!.sql)].slice(0, 10);
        localStorage.setItem(HISTORY_KEY, JSON.stringify(next));
        return next;
      });
    }
  }, [query.isSuccess, query.data?.sql]);

  const canSaveSnippet = useMemo(() => sql.trim().length > 0, [sql]);

  const saveSnippet = () => {
    if (!canSaveSnippet) return;
    const name = `Snippet ${snippets.length + 1}`;
    const next = [{ name, sql: sql.trim() }, ...snippets].slice(0, 12);
    setSnippets(next);
    localStorage.setItem(SNIPPETS_KEY, JSON.stringify(next));
  };

  const clearHistory = () => {
    setHistory([]);
    localStorage.removeItem(HISTORY_KEY);
  };

  return (
    <div className="grid gap-4 lg:grid-cols-[1.4fr,0.9fr]">
      <Card>
        <CardHeader>
          <CardTitle>SQL editor</CardTitle>
          <CardDescription>Execute live SQL against MiniDB through the API bridge. Multiline scripts are supported; separate statements with semicolons.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3">
          <Textarea value={sql} onChange={(e) => setSql(e.target.value)} className="min-h-32 font-mono" />
          <div className="flex gap-2">
            <Button onClick={() => query.mutate(sql)} disabled={query.isPending}>
              <Play className="mr-2 h-4 w-4" />
              {query.isPending ? "Running" : "Run query"}
            </Button>
            <Button variant="secondary" onClick={saveSnippet} disabled={!canSaveSnippet}>
              <Save className="mr-2 h-4 w-4" /> Save snippet
            </Button>
            <Button variant="outline" onClick={() => setSql(DEFAULT_SQL)}>
              <RotateCcw className="mr-2 h-4 w-4" />
              Reset
            </Button>
          </div>
          {query.error ? <p className="text-sm text-red-600">{(query.error as Error).message}</p> : null}
        </CardContent>
      </Card>

      <div className="grid gap-4">
        <Card>
          <CardHeader>
            <CardTitle>Result</CardTitle>
            <CardDescription>Parsed result rows and raw protocol lines.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4">
            {rows.length > 0 ? (
              <div className="overflow-x-auto rounded-md border">
                <Table>
                  <THead>
                    <TR>
                      {Array.from({ length: columnCount }).map((_, i) => (
                        <TH key={i}>col_{i + 1}</TH>
                      ))}
                    </TR>
                  </THead>
                  <TBody>
                    {rows.map((row, rowIndex) => (
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
              <p className="text-sm text-muted-foreground">No tabular rows parsed yet.</p>
            )}

            <div>
              <p className="mb-2 text-sm font-medium">Raw response</p>
              {query.data?.statementsExecuted ? (
                <p className="mb-2 text-xs text-muted-foreground">Statements executed: {query.data.statementsExecuted}</p>
              ) : null}
              <pre className="max-h-64 overflow-auto rounded-md bg-muted p-3 text-xs">
                {(query.data?.lines ?? ["Run a query to see raw output"]).join("\n")}
              </pre>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between gap-4 space-y-0">
            <div>
              <CardTitle className="flex items-center gap-2"><History className="h-4 w-4" /> Query history</CardTitle>
              <CardDescription>Local history persisted in the browser.</CardDescription>
            </div>
            <Button variant="outline" onClick={clearHistory}>
              <RotateCcw className="mr-2 h-4 w-4" /> Clear
            </Button>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            {history.length > 0 ? history.map((item) => (
              <button key={item} className="rounded-md border p-2 text-left hover:bg-accent" onClick={() => setSql(item)}>
                {item}
              </button>
            )) : <p className="text-muted-foreground">No history yet.</p>}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><BookMarked className="h-4 w-4" /> Saved snippets</CardTitle>
            <CardDescription>Reusable SQL snippets for fast query composition.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            {snippets.map((snippet) => (
              <div key={snippet.name} className="rounded-md border p-2">
                <div className="mb-1 flex items-center justify-between gap-2">
                  <span className="font-medium">{snippet.name}</span>
                  <Button size="sm" variant="ghost" onClick={() => setSql(snippet.sql)}>
                    <Plus className="mr-2 h-3 w-3" /> Insert
                  </Button>
                </div>
                <pre className="overflow-auto text-xs text-muted-foreground">{snippet.sql}</pre>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

