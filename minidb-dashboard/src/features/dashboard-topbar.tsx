import { useQuery } from "@tanstack/react-query";
import { Activity, DatabaseZap, LogOut, PlayCircle, Server, ShieldCheck, UserRound, UsersRound } from "lucide-react";
import { getStatus } from "../lib/api";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";

type DashboardTopbarProps = {
  onLogout: () => void;
};

export function DashboardTopbar({ onLogout }: DashboardTopbarProps) {
  const status = useQuery({ queryKey: ["dashboard-status"], queryFn: getStatus, refetchInterval: 4000 });

  return (
    <div className="border-b bg-background/90 backdrop-blur">
      <div className="mx-auto flex max-w-7xl flex-col gap-3 px-4 py-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <h1 className="text-xl font-semibold">MiniDB Dashboard</h1>
          <p className="text-sm text-muted-foreground">Operational console for a live MiniDatabase server</p>
        </div>

        <div className="flex flex-wrap items-center gap-2 text-sm">
          <Badge className={status.data?.connected ? "border-green-500 text-green-700" : "border-red-500 text-red-700"}>
            <Activity className="mr-1 h-3.5 w-3.5" />
            {status.data?.connected ? "Connected" : "Disconnected"}
          </Badge>
          <Badge className="border-muted-foreground/30">
            <UserRound className="mr-1 h-3.5 w-3.5" /> {status.data?.activeUser ?? "admin"}
          </Badge>
          <Badge className={status.data?.walEnabled ? "border-blue-500 text-blue-700" : "border-muted-foreground/30"}>
            <DatabaseZap className="mr-1 h-3.5 w-3.5" /> WAL {status.data?.walEnabled ? "On" : "Off"}
          </Badge>
          <Badge className={status.data?.recoveryReady ? "border-green-500 text-green-700" : "border-yellow-500 text-yellow-700"}>
            <ShieldCheck className="mr-1 h-3.5 w-3.5" /> Recovery {status.data?.recoveryReady ? "Ready" : "Pending"}
          </Badge>
          <Badge className="border-muted-foreground/30">
            <UsersRound className="mr-1 h-3.5 w-3.5" /> Sessions {status.data?.sessions ?? 0}
          </Badge>
          <Badge className="border-muted-foreground/30">
            <PlayCircle className="mr-1 h-3.5 w-3.5" /> Running {status.data?.runningQueries ?? 0}
          </Badge>
          <Badge className="border-muted-foreground/30">
            <Server className="mr-1 h-3.5 w-3.5" /> {status.data?.server ?? "instance"}
          </Badge>
          <Button size="sm" variant="outline" onClick={onLogout}>
            <LogOut className="mr-2 h-3.5 w-3.5" /> Logout
          </Button>
        </div>
      </div>
    </div>
  );
}

