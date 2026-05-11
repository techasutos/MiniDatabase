import { useQuery } from "@tanstack/react-query";
import { FileClock, ShieldCheck, TriangleAlert } from "lucide-react";
import { getCapabilities, getHealth } from "../lib/api";
import { Badge } from "../components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";

export function RecoveryPanel() {
  const health = useQuery({ queryKey: ["health"], queryFn: getHealth, refetchInterval: 5000 });
  const capabilities = useQuery({ queryKey: ["capabilities"], queryFn: getCapabilities, refetchInterval: 10000 });

  return (
    <div className="grid gap-4 md:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5" /> Recovery baseline
          </CardTitle>
          <CardDescription>Current branch supports startup replay, checkpoints, truncation, and CRC validation.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-2 text-sm">
          <div className="flex items-center gap-2">
            <Badge className="border-green-500 text-green-700">Enabled</Badge>
            <span>WAL startup recovery path</span>
          </div>
          <div className="flex items-center gap-2">
            <Badge className="border-green-500 text-green-700">Enabled</Badge>
            <span>Checkpoint metadata (`minidb.wal.checkpoint`)</span>
          </div>
          <div className="flex items-center gap-2">
            <Badge className="border-green-500 text-green-700">Enabled</Badge>
            <span>CRC validation on WAL read</span>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <FileClock className="h-5 w-5" /> Runtime signals
          </CardTitle>
          <CardDescription>Quick indicators from live protocol checks.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          <p>Server status: {health.data?.ok ? "online" : "offline"}</p>
          <p>Protocol: {capabilities.data?.capabilities.PROTOCOL ?? "n/a"}</p>
          <div className="rounded-md border p-3 text-muted-foreground">
            Detailed replay counters and WAL retention telemetry are planned in the next milestone.
          </div>
          {!health.data?.ok ? (
            <div className="flex items-center gap-2 text-red-600">
              <TriangleAlert className="h-4 w-4" />
              Unable to reach MiniDB. Verify server and credentials.
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}

