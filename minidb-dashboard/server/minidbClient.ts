import net from "node:net";
import readline from "node:readline";

export type ClientConfig = {
  host: string;
  port: number;
  user: string;
  password: string;
  timeoutMs?: number;
};

export type MiniDbSession = {
  greeting: string;
  execute: (command: string) => Promise<string[]>;
  close: () => Promise<void>;
};

export type SessionRecord = {
  sessionId: number;
  user: string;
  state: string;
  remote: string;
  connectedAt: string;
  lastActivity: string;
  lastCommand: string;
};

export async function openSession(config: ClientConfig): Promise<MiniDbSession> {
  const socket = net.createConnection({ host: config.host, port: config.port });
  socket.setTimeout(config.timeoutMs ?? 5000);

  const rl = readline.createInterface({ input: socket });
  const iterator = rl[Symbol.asyncIterator]();

  const readLine = async () => {
    const next = await iterator.next();
    if (next.done || next.value == null) {
      throw new Error("Server closed connection unexpectedly");
    }
    return String(next.value);
  };

  const writeLine = (line: string) => {
    socket.write(`${line}\n`);
  };

  await new Promise<void>((resolve, reject) => {
    socket.once("connect", () => resolve());
    socket.once("error", reject);
    socket.once("timeout", () => reject(new Error("MiniDB connect timeout")));
  });

  const greeting = await readLine();
  const authPrompt = await readLine();
  if (authPrompt !== "AUTH") {
    throw new Error(`Unexpected auth prompt: ${authPrompt}`);
  }

  writeLine(config.user);
  writeLine(config.password);
  const authResult = await readLine();
  if (authResult !== "OK") {
    throw new Error(`Authentication failed: ${authResult}`);
  }

  const execute = async (command: string) => {
    writeLine(command);
    const lines: string[] = [];
    while (true) {
      const line = await readLine();
      if (line === "END") {
        return lines;
      }
      lines.push(line);
    }
  };

  const close = async () => {
    try {
      writeLine("QUIT");
    } catch {
      // Ignore write errors during close.
    }
    rl.close();
    socket.end();
    socket.destroy();
  };

  return { greeting, execute, close };
}

export async function pingServer(config: ClientConfig) {
  const session = await openSession(config);
  try {
    const started = Date.now();
    const lines = await session.execute("PING");
    const latencyMs = Date.now() - started;
    return {
      greeting: session.greeting,
      ok: lines[0] === "PONG",
      latencyMs
    };
  } finally {
    await session.close();
  }
}

export async function readCapabilities(config: ClientConfig) {
  const session = await openSession(config);
  try {
    const lines = await session.execute("CAPABILITIES");
    const capabilities: Record<string, string> = {};
    for (const line of lines) {
      const index = line.indexOf("=");
      if (index > 0) {
        const key = line.slice(0, index).trim();
        const value = line.slice(index + 1).trim();
        if (key.length > 0) {
          capabilities[key] = value;
        }
      }
    }
    return capabilities;
  } finally {
    await session.close();
  }
}

export async function readDatabases(config: ClientConfig) {
  const session = await openSession(config);
  try {
    return (await session.execute("SHOW DATABASES")).map((line) => stripPrefix(line, "DATABASE "));
  } finally {
    await session.close();
  }
}

export async function readSchemas(config: ClientConfig, database: string) {
  const session = await openSession(config);
  try {
    return (await session.execute(`SHOW SCHEMAS IN ${database}`)).map((line) => stripPrefix(line, "SCHEMA "));
  } finally {
    await session.close();
  }
}

export async function readTables(config: ClientConfig, database: string, schema: string) {
  const session = await openSession(config);
  try {
    return (await session.execute(`SHOW TABLES IN ${database}.${schema}`)).map((line) => stripPrefix(line, "TABLE "));
  } finally {
    await session.close();
  }
}

export async function readColumns(config: ClientConfig, database: string, schema: string, table: string) {
  const session = await openSession(config);
  try {
    return (await session.execute(`SHOW COLUMNS IN ${database}.${schema}.${table}`)).map((line) => stripPrefix(line, "COLUMN "));
  } finally {
    await session.close();
  }
}

export async function readSessions(config: ClientConfig) {
  const session = await openSession(config);
  try {
    const lines = await session.execute("SHOW SESSIONS");
    return lines
      .filter((line) => line.startsWith("SESSION "))
      .map(parseSessionLine);
  } finally {
    await session.close();
  }
}

function parseRow(line: string): string[] | null {
  const trimmed = line.trim();
  if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
    return null;
  }
  const body = trimmed.slice(1, -1).trim();
  if (!body) {
    return [];
  }
  return body.split(",").map((v) => v.trim());
}

function stripPrefix(line: string, prefix: string) {
  return line.startsWith(prefix) ? line.slice(prefix.length).trim() : line.trim();
}

function parseSessionLine(line: string): SessionRecord {
  const payload = line.replace(/^SESSION\s+/, "");
  const parts = payload.split(";");
  const record: Record<string, string> = {};
  for (const part of parts) {
    const idx = part.indexOf("=");
    if (idx > 0) {
      record[part.slice(0, idx).trim()] = part.slice(idx + 1).trim();
    }
  }
  return {
    sessionId: Number(record.sessionId ?? 0),
    user: record.user ?? "",
    state: record.state ?? "",
    remote: record.remote ?? "",
    connectedAt: record.connectedAt ?? "",
    lastActivity: record.lastActivity ?? "",
    lastCommand: record.lastCommand ?? ""
  };
}

export async function executeSql(config: ClientConfig, sql: string) {
  const session = await openSession(config);
  try {
    const lines = await session.execute(sql);
    const rows = lines.map(parseRow).filter((v): v is string[] => Array.isArray(v));
    return { lines, rows };
  } finally {
    await session.close();
  }
}

