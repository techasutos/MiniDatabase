import type {
  CapabilitiesResponse,
  CatalogColumn,
  CatalogDatabase,
  CatalogResponse,
  CatalogSchema,
  CatalogTable,
  DashboardStatus,
  HealthResponse,
  LoginRequest,
  LoginResponse,
  QueryResponse,
  QueriesResponse,
  SessionsResponse
} from "./types";

const AUTH_TOKEN_KEY = "minidb.dashboard.authToken";
const AUTH_EXPIRED_EVENT = "minidb-dashboard-auth-expired";

function authToken() {
  return localStorage.getItem(AUTH_TOKEN_KEY) ?? "";
}

async function jsonFetch<T>(url: string, init?: RequestInit): Promise<T> {
  const token = authToken();
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    ...init
  });
  if (!response.ok) {
    if (response.status === 401) {
      localStorage.removeItem(AUTH_TOKEN_KEY);
      window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT));
    }
    const body = await response.text();
    throw new Error(body || `HTTP ${response.status}`);
  }
  return (await response.json()) as T;
}

export function getHealth() {
  return jsonFetch<HealthResponse>("/api/health");
}

export async function loginToInstance(request: LoginRequest) {
  const response = await jsonFetch<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(request)
  });
  localStorage.setItem(AUTH_TOKEN_KEY, response.token);
  return response;
}

export async function logoutFromInstance() {
  try {
    await jsonFetch<{ ok: boolean }>("/api/auth/logout", { method: "POST" });
  } finally {
    localStorage.removeItem(AUTH_TOKEN_KEY);
  }
}

export function isAuthenticated() {
  return authToken().length > 0;
}

export function subscribeAuthExpired(listener: () => void) {
  const wrapped = () => listener();
  window.addEventListener(AUTH_EXPIRED_EVENT, wrapped);
  return () => window.removeEventListener(AUTH_EXPIRED_EVENT, wrapped);
}

export function getStatus() {
  return jsonFetch<DashboardStatus>("/api/status");
}

export function getCapabilities() {
  return jsonFetch<CapabilitiesResponse>("/api/capabilities");
}

export function runQuery(sql: string) {
  return jsonFetch<QueryResponse>("/api/query", {
    method: "POST",
    body: JSON.stringify({ sql })
  });
}

export function getDatabases() {
  return jsonFetch<CatalogResponse<CatalogDatabase>>("/api/catalog/databases");
}

export function getSchemas(database: string) {
  return jsonFetch<CatalogResponse<CatalogSchema>>(`/api/catalog/schemas?database=${encodeURIComponent(database)}`);
}

export function getTables(database: string, schema: string) {
  const query = new URLSearchParams({ database, schema });
  return jsonFetch<CatalogResponse<CatalogTable>>(`/api/catalog/tables?${query.toString()}`);
}

export function getColumns(database: string, schema: string, table: string) {
  const query = new URLSearchParams({ database, schema, table });
  return jsonFetch<CatalogResponse<CatalogColumn>>(`/api/catalog/columns?${query.toString()}`);
}

export function getSessions() {
  return jsonFetch<SessionsResponse>("/api/sessions");
}

export function getQueries() {
  return jsonFetch<QueriesResponse>("/api/queries");
}

export function cancelQuery(id: number) {
  return jsonFetch<{ ok: boolean }>(`/api/queries/${id}/cancel`, { method: "POST" });
}

