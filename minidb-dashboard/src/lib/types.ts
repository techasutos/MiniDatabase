export type HealthResponse = {
  ok: boolean;
  greeting?: string;
  latencyMs?: number;
  error?: string;
};

export type CapabilitiesResponse = {
  ok: boolean;
  capabilities: Record<string, string>;
  error?: string;
};

export type QueryResponse = {
  ok: boolean;
  sql: string;
  statementsExecuted?: number;
  lines: string[];
  rows: string[][];
  error?: string;
};

export type LoginRequest = {
  host: string;
  port: number;
  user: string;
  password: string;
};

export type LoginResponse = {
  ok: boolean;
  token: string;
  user: string;
  server: string;
  error?: string;
};

export type CatalogDatabase = { name: string };
export type CatalogSchema = { name: string };
export type CatalogTable = { name: string };
export type CatalogColumn = { name: string; type: string };

export type CatalogResponse<T> = {
  ok: boolean;
  items: T[];
  error?: string;
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

export type SessionsResponse = {
  ok: boolean;
  sessions: SessionRecord[];
  error?: string;
};

export type DashboardStatus = {
  ok: boolean;
  connected: boolean;
  activeUser: string;
  server: string;
  protocol: string;
  walEnabled: boolean;
  recoveryReady: boolean;
  sessions: number;
  runningQueries: number;
  error?: string;
};

export type QueryRecord = {
  id: number;
  sql: string;
  status: "running" | "completed" | "failed" | "canceled";
  startedAt: string;
  updatedAt: string;
  finishedAt?: string;
  durationMs?: number;
  rows?: number;
  error?: string;
  cancelRequested: boolean;
};

export type QueriesResponse = {
  ok: boolean;
  queries: QueryRecord[];
  error?: string;
};

