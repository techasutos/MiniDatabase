import { useQuery } from "@tanstack/react-query";
import { Activity, CheckCircle2, DatabaseZap, ShieldAlert } from "lucide-react";
import { getCapabilities, getHealth } from "../lib/api";
import { Badge } from "../components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";

export function OverviewPanel() {
  const health = useQuery({ queryKey: ["health"], queryFn: getHealth, refetchInterval: 5000 });
  const capabilities = useQuery({ queryKey: ["capabilities"], queryFn: getCapabilities, refetchInterval: 10000 });

  const capabilityRows = Object.entries(capabilities.data?.capabilities ?? {});

  return (
    <div className="grid gap-4">
      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-base">
              <Activity className="h-4 w-4" /> Server Health
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2">
              <Badge className={health.data?.ok ? "border-green-500 text-green-700" : "border-red-500 text-red-700"}>
                {health.isLoading ? "Checking" : health.data?.ok ? "Online" : "Offline"}
              </Badge>
              <span className="text-sm text-muted-foreground">{health.data?.latencyMs ?? "-"} ms</span>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-base">
              <CheckCircle2 className="h-4 w-4" /> Protocol
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-sm">{capabilities.data?.capabilities.PROTOCOL ?? "minidb-text/1"}</div>
            <div className="text-xs text-muted-foreground">{capabilities.data?.capabilities.FRAMING ?? "line + END"}</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-base">
              <DatabaseZap className="h-4 w-4" /> Recovery Baseline
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-sm">WAL + checkpoint flow enabled</div>
            <div className="text-xs text-muted-foreground">See Recovery tab for details</div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Server capabilities</CardTitle>
          <CardDescription>Live capability introspection from MiniDB protocol.</CardDescription>
        </CardHeader>
        <CardContent>
          {capabilityRows.length === 0 ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <ShieldAlert className="h-4 w-4" /> No capability payload returned.
            </div>
          ) : (
            <div className="grid gap-2 md:grid-cols-2">
              {capabilityRows.map(([key, value]) => (
                <div key={key} className="rounded-md border p-3">
                  <div className="text-xs text-muted-foreground">{key}</div>
                  <div className="font-medium">{value}</div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

