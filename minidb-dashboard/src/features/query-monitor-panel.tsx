import { useQuery } from "@tanstack/react-query";
import { Ban, Clock3, RefreshCcw } from "lucide-react";
import { cancelQuery, getQueries } from "../lib/api";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";

export function QueryMonitorPanel() {
  const queries = useQuery({ queryKey: ["queries"], queryFn: getQueries, refetchInterval: 3000 });

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between gap-4 space-y-0">
        <div>
          <CardTitle className="flex items-center gap-2">
            <Clock3 className="h-5 w-5" /> Query monitor
          </CardTitle>
          <CardDescription>Live query history, progress, and cancel controls from the dashboard bridge.</CardDescription>
        </div>
        <Button variant="outline" onClick={() => queries.refetch()}>
          <RefreshCcw className="mr-2 h-4 w-4" /> Refresh
        </Button>
      </CardHeader>
      <CardContent className="grid gap-3">
        {(queries.data?.queries ?? []).length > 0 ? (
          (queries.data?.queries ?? []).map((query) => (
            <div key={query.id} className="grid gap-2 rounded-md border p-3 lg:grid-cols-[1fr,auto] lg:items-center">
              <div>
                <div className="mb-1 flex items-center gap-2">
                  <Badge>{query.status}</Badge>
                  <span className="text-xs text-muted-foreground">#{query.id} · {query.durationMs ?? 0} ms</span>
                </div>
                <pre className="overflow-auto whitespace-pre-wrap text-xs text-muted-foreground">{query.sql}</pre>
                {query.error ? <p className="mt-2 text-xs text-red-600">{query.error}</p> : null}
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs text-muted-foreground">Rows: {query.rows ?? 0}</span>
                <Button
                  variant="outline"
                  disabled={query.status !== "running"}
                  onClick={async () => {
                    await cancelQuery(query.id);
                    await queries.refetch();
                  }}
                >
                  <Ban className="mr-2 h-4 w-4" /> Cancel
                </Button>
              </div>
            </div>
          ))
        ) : (
          <div className="rounded-md border border-dashed p-6 text-sm text-muted-foreground">
            No recent queries yet.
          </div>
        )}
        <div className="text-xs text-muted-foreground">
          Use this panel to watch running SQL, see recent history, and cancel long-running work when the backend supports it.
        </div>
      </CardContent>
    </Card>
  );
}

