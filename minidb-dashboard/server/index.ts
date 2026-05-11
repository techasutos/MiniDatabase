import cors from "cors";
import express from "express";
import { randomUUID } from "node:crypto";
import { ClientConfig, openSession, pingServer, readCapabilities, readColumns, readDatabases, readSchemas, readSessions, readTables } from "./minidbClient";

type QueryStatus = "running" | "completed" | "failed" | "canceled";

type QueryRecord = {
  id: number;
  sql: string;
  status: QueryStatus;
  startedAt: string;
  updatedAt: string;
  finishedAt?: string;
  durationMs?: number;
  rows?: number;
  error?: string;
  cancelRequested: boolean;
  ownerToken?: string;
};

type RunningQuery = {
  record: QueryRecord;
  session: Awaited<ReturnType<typeof openSession>>;
};

type DashboardSession = {
  token: string;
  config: ClientConfig;
  createdAt: string;
};

const app = express();
app.use(cors());
app.use(express.json());

const config = {
  host: process.env.MINIDB_HOST ?? "127.0.0.1",
  port: Number(process.env.MINIDB_PORT ?? 5544),
  user: process.env.MINIDB_USER ?? "admin",
  password: process.env.MINIDB_PASSWORD ?? "minidb"
};

const dashboardSessions = new Map<string, DashboardSession>();

let nextQueryId = 1;
const activeQueries = new Map<number, RunningQuery>();
const queryHistory: QueryRecord[] = [];

function nowIso() {
  return new Date().toISOString();
}

function addToHistory(record: QueryRecord) {
  queryHistory.unshift(record);
  queryHistory.splice(50);
}

function listQueries(ownerToken: string) {
  return [...activeQueries.values()]
    .map((item) => item.record)
    .concat(queryHistory)
    .filter((record) => record.ownerToken === ownerToken)
    .sort((a, b) => b.id - a.id);
}

async function runTrackedQuery(sql: string, queryConfig: ClientConfig, ownerToken: string) {
  const id = nextQueryId++;
  const startedAtMs = Date.now();
  const startedAt = nowIso();
  const record: QueryRecord = {
    id,
    sql,
    status: "running",
    startedAt,
    updatedAt: startedAt,
    cancelRequested: false,
    ownerToken
  };

  const session = await openSession(queryConfig);
  activeQueries.set(id, { record, session });

  try {
    const lines = await session.execute(sql);
    const rows = lines
      .map((line) => {
        const trimmed = line.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
          return null;
        }
        const body = trimmed.slice(1, -1).trim();
        return body ? body.split(",").map((value) => value.trim()) : [];
      })
      .filter((value): value is string[] => Array.isArray(value));

    const serverError = lines.find((line) => line.startsWith("ERROR:"));
    if (serverError) {
      record.status = "failed";
      record.error = serverError.slice(6).trim();
      throw new Error(record.error);
    }

    record.status = "completed";
    record.rows = rows.length;
    return { lines, rows };
  } catch (error) {
    record.status = record.cancelRequested ? "canceled" : "failed";
    record.error = record.cancelRequested ? "Query canceled" : (error as Error).message;
    throw new Error(record.error);
  } finally {
    record.updatedAt = nowIso();
    record.finishedAt = nowIso();
    record.durationMs = Date.now() - startedAtMs;
    activeQueries.delete(id);
    addToHistory(record);
    await session.close().catch(() => undefined);
  }
}

function splitSqlStatements(script: string): string[] {
  const statements: string[] = [];
  let current = "";
  let inSingle = false;
  let inDouble = false;
  for (let i = 0; i < script.length; i++) {
    const char = script[i];
    const prev = i > 0 ? script[i - 1] : "";
    if (char === "'" && prev !== "\\" && !inDouble) {
      inSingle = !inSingle;
    } else if (char === '"' && prev !== "\\" && !inSingle) {
      inDouble = !inDouble;
    }

    if (char === ";" && !inSingle && !inDouble) {
      const trimmed = current.trim();
      if (trimmed.length > 0) {
        statements.push(trimmed);
      }
      current = "";
      continue;
    }
    current += char;
  }
  const tail = current.trim();
  if (tail.length > 0) {
    statements.push(tail);
  }
  return statements;
}

function authTokenFrom(req: express.Request): string {
  const auth = String(req.header("authorization") ?? "");
  return auth.startsWith("Bearer ") ? auth.slice("Bearer ".length).trim() : "";
}

function resolveConfig(req: express.Request): ClientConfig | null {
  const token = authTokenFrom(req);
  if (!token) {
    return null;
  }
  const session = dashboardSessions.get(token);
  return session?.config ?? null;
}

async function runTrackedQueryFor(sql: string, cfg: ClientConfig, ownerToken: string) {
  return runTrackedQuery(sql, cfg, ownerToken);
}

app.post("/api/auth/login", async (req, res) => {
  const host = String(req.body?.host ?? "127.0.0.1").trim();
  const port = Number(req.body?.port ?? 5432);
  const user = String(req.body?.user ?? "").trim();
  const password = String(req.body?.password ?? "");

  if (!host || !Number.isFinite(port) || !user) {
    res.status(400).json({ ok: false, error: "host, port, and user are required" });
    return;
  }

  const candidate: ClientConfig = { host, port, user, password };
  try {
    const ping = await pingServer(candidate);
    if (!ping.ok) {
      res.status(401).json({ ok: false, error: "Authentication failed" });
      return;
    }
    const token = randomUUID();
    dashboardSessions.set(token, { token, config: candidate, createdAt: nowIso() });
    res.json({ ok: true, token, user, server: `${host}:${port}` });
  } catch (error) {
    res.status(401).json({ ok: false, error: (error as Error).message });
  }
});

app.post("/api/auth/logout", (req, res) => {
  const token = authTokenFrom(req);
  if (token) {
    dashboardSessions.delete(token);
  }
  res.json({ ok: true });
});

app.get("/api/health", async (_req, res) => {
  try {
    const result = await pingServer(config);
    res.json(result);
  } catch (error) {
    res.status(503).json({ ok: false, error: (error as Error).message });
  }
});

app.get("/api/capabilities", async (_req, res) => {
  try {
    const capabilities = await readCapabilities(config);
    res.json({ ok: true, capabilities });
  } catch (error) {
    res.status(500).json({ ok: false, capabilities: {}, error: (error as Error).message });
  }
});

app.get("/api/status", async (_req, res) => {
  const authConfig = resolveConfig(_req);
  if (!authConfig) {
    res.status(401).json({ ok: false, connected: false, error: "Unauthorized" });
    return;
  }
  try {
    const [health, capabilities, sessions] = await Promise.all([
      pingServer(authConfig),
      readCapabilities(authConfig),
      readSessions(authConfig)
    ]);
    const features = capabilities.FEATURES ?? "";
    const walEnabled = /checkpoint|recovery/i.test(features);
    res.json({
      ok: health.ok,
      connected: health.ok,
      activeUser: authConfig.user,
      server: `${authConfig.host}:${authConfig.port}`,
      protocol: capabilities.PROTOCOL ?? "minidb-text/1",
      walEnabled,
      recoveryReady: walEnabled && health.ok,
      sessions: sessions.length,
      runningQueries: activeQueries.size
    });
  } catch (error) {
    res.status(503).json({
      ok: false,
      connected: false,
      activeUser: authConfig.user,
      server: `${authConfig.host}:${authConfig.port}`,
      protocol: "unknown",
      walEnabled: false,
      recoveryReady: false,
      sessions: 0,
      runningQueries: activeQueries.size,
      error: (error as Error).message
    });
  }
});

app.get("/api/catalog/databases", async (_req, res) => {
  const authConfig = resolveConfig(_req);
  if (!authConfig) {
    res.status(401).json({ ok: false, items: [], error: "Unauthorized" });
    return;
  }
  try {
    const items = await readDatabases(authConfig);
    res.json({ ok: true, items: items.map((name) => ({ name })) });
  } catch (error) {
    res.status(500).json({ ok: false, items: [], error: (error as Error).message });
  }
});

app.get("/api/catalog/schemas", async (req, res) => {
  const authConfig = resolveConfig(req);
  if (!authConfig) {
    res.status(401).json({ ok: false, items: [], error: "Unauthorized" });
    return;
  }
  const database = String(req.query.database ?? "").trim();
  if (!database) {
    res.status(400).json({ ok: false, items: [], error: "database query parameter is required" });
    return;
  }
  try {
    const items = await readSchemas(authConfig, database);
    res.json({ ok: true, items: items.map((name) => ({ name })) });
  } catch (error) {
    res.status(500).json({ ok: false, items: [], error: (error as Error).message });
  }
});

app.get("/api/catalog/tables", async (req, res) => {
  const authConfig = resolveConfig(req);
  if (!authConfig) {
    res.status(401).json({ ok: false, items: [], error: "Unauthorized" });
    return;
  }
  const database = String(req.query.database ?? "").trim();
  const schema = String(req.query.schema ?? "").trim();
  if (!database || !schema) {
    res.status(400).json({ ok: false, items: [], error: "database and schema query parameters are required" });
    return;
  }
  try {
    const items = await readTables(authConfig, database, schema);
    res.json({ ok: true, items: items.map((name) => ({ name })) });
  } catch (error) {
    res.status(500).json({ ok: false, items: [], error: (error as Error).message });
  }
});

app.get("/api/catalog/columns", async (req, res) => {
  const authConfig = resolveConfig(req);
  if (!authConfig) {
    res.status(401).json({ ok: false, items: [], error: "Unauthorized" });
    return;
  }
  const database = String(req.query.database ?? "").trim();
  const schema = String(req.query.schema ?? "").trim();
  const table = String(req.query.table ?? "").trim();
  if (!database || !schema || !table) {
    res.status(400).json({ ok: false, items: [], error: "database, schema and table query parameters are required" });
    return;
  }
  try {
    const items = await readColumns(authConfig, database, schema, table);
    res.json({ ok: true, items: items.map((definition) => {
      const [name, type = "UNKNOWN"] = definition.split(":", 2);
      return { name, type };
    }) });
  } catch (error) {
    res.status(500).json({ ok: false, items: [], error: (error as Error).message });
  }
});

app.get("/api/sessions", async (_req, res) => {
  const authConfig = resolveConfig(_req);
  if (!authConfig) {
    res.status(401).json({ ok: false, sessions: [], error: "Unauthorized" });
    return;
  }
  try {
    const sessions = await readSessions(authConfig);
    res.json({ ok: true, sessions });
  } catch (error) {
    res.status(500).json({ ok: false, sessions: [], error: (error as Error).message });
  }
});

app.get("/api/queries", async (_req, res) => {
  const token = authTokenFrom(_req);
  if (!token || !dashboardSessions.has(token)) {
    res.status(401).json({ ok: false, queries: [], error: "Unauthorized" });
    return;
  }
  res.json({ ok: true, queries: listQueries(token) });
});

app.post("/api/queries/:id/cancel", async (req, res) => {
  const token = authTokenFrom(req);
  if (!token || !dashboardSessions.has(token)) {
    res.status(401).json({ ok: false, error: "Unauthorized" });
    return;
  }
  const id = Number(req.params.id);
  const running = activeQueries.get(id);
  if (!Number.isFinite(id) || !running || running.record.ownerToken !== token) {
    res.status(404).json({ ok: false, error: "Query not found or not running" });
    return;
  }

  running.record.cancelRequested = true;
  running.record.status = "canceled";
  running.record.updatedAt = nowIso();
  await running.session.close().catch(() => undefined);
  res.json({ ok: true, query: running.record });
});

app.post("/api/query", async (req, res) => {
  const token = authTokenFrom(req);
  const authConfig = resolveConfig(req);
  if (!authConfig || !token) {
    res.status(401).json({ ok: false, sql: "", lines: [], rows: [], error: "Unauthorized" });
    return;
  }
  const sql = String(req.body?.sql ?? "").trim();
  if (!sql) {
    res.status(400).json({ ok: false, sql, lines: [], rows: [], error: "SQL must not be empty" });
    return;
  }

  try {
    const statements = splitSqlStatements(sql);
    const lines: string[] = [];
    const rows: string[][] = [];
    for (let i = 0; i < statements.length; i++) {
      const statement = statements[i];
      const result = await runTrackedQueryFor(statement, authConfig, token);
      lines.push(`-- statement ${i + 1}: ${statement}`);
      lines.push(...result.lines);
      rows.push(...result.rows);
    }
    res.json({ ok: true, sql, statementsExecuted: statements.length, lines, rows });
  } catch (error) {
    const message = (error as Error).message;
    res.status(message === "Query canceled" ? 499 : 500).json({ ok: false, sql, lines: [], rows: [], error: message });
  }
});

const port = Number(process.env.DASHBOARD_API_PORT ?? 7070);
app.listen(port, () => {
  console.log(`MiniDB dashboard API listening on http://localhost:${port}`);
});

