import { useEffect, useState } from "react";
import { Database, LayoutDashboard, LifeBuoy, Monitor, TerminalSquare, UserRoundCog } from "lucide-react";
import { isAuthenticated, logoutFromInstance, subscribeAuthExpired } from "./lib/api";
import { Tabs } from "./components/ui/tabs";
import { DashboardTopbar } from "./features/dashboard-topbar";
import { LoginPanel } from "./features/login-panel";
import { OverviewPanel } from "./features/overview-panel";
import { RecoveryPanel } from "./features/recovery-panel";
import { QueryMonitorPanel } from "./features/query-monitor-panel";
import { SchemaPanel } from "./features/schema-panel";
import { SessionsPanel } from "./features/sessions-panel";
import { SqlEditorPanel } from "./features/sql-editor-panel";

type ViewId = "overview" | "sql" | "schema" | "sessions" | "recovery" | "monitor";

const NAV = [
  { id: "overview", label: "Overview", icon: LayoutDashboard },
  { id: "sql", label: "SQL Editor", icon: TerminalSquare },
  { id: "monitor", label: "Monitor", icon: Monitor },
  { id: "schema", label: "Schema", icon: Database },
  { id: "sessions", label: "Sessions", icon: UserRoundCog },
  { id: "recovery", label: "Recovery", icon: LifeBuoy }
] as const;

export default function App() {
  const [view, setView] = useState<ViewId>("overview");
  const [authed, setAuthed] = useState<boolean>(isAuthenticated());

  useEffect(() => subscribeAuthExpired(() => setAuthed(false)), []);

  if (!authed) {
    return <LoginPanel onLoggedIn={() => setAuthed(true)} />;
  }

  return (
    <div className="min-h-screen bg-muted/40">
      <DashboardTopbar onLogout={async () => {
        await logoutFromInstance();
        setAuthed(false);
      }} />
      <header className="border-b bg-background">
        <div className="mx-auto flex max-w-7xl items-center justify-end px-4 py-3">
          <Tabs tabs={NAV.map((n) => ({ id: n.id, label: n.label }))} value={view} onChange={(id) => setView(id as ViewId)} />
        </div>
      </header>

      <main className="mx-auto grid max-w-7xl gap-4 px-4 py-6 md:grid-cols-[220px,1fr]">
        <aside className="hidden rounded-lg border bg-background p-2 md:block">
          {NAV.map((item) => {
            const Icon = item.icon;
            const active = item.id === view;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setView(item.id)}
                className={[
                  "mb-1 flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm",
                  active ? "bg-primary text-primary-foreground" : "hover:bg-accent"
                ].join(" ")}
              >
                <Icon className="h-4 w-4" /> {item.label}
              </button>
            );
          })}

          {view === "schema" ? (
            <div className="mt-3 border-t pt-3">
              <div id="schema-catalog-sidebar-slot" />
            </div>
          ) : null}
        </aside>

        <section>
          {view === "overview" ? <OverviewPanel /> : null}
          {view === "sql" ? <SqlEditorPanel /> : null}
          {view === "monitor" ? <QueryMonitorPanel /> : null}
          {view === "schema" ? <SchemaPanel onOpenSql={() => setView("sql")} /> : null}
          {view === "sessions" ? <SessionsPanel /> : null}
          {view === "recovery" ? <RecoveryPanel /> : null}
        </section>
      </main>
    </div>
  );
}

