import { useQuery } from "@tanstack/react-query";
import { ActivitySquare, RefreshCcw, Users } from "lucide-react";
import { getSessions } from "../lib/api";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";
import { Table, TBody, TD, TH, THead, TR } from "../components/ui/table";

export function SessionsPanel() {
  const sessionsQuery = useQuery({ queryKey: ["sessions"], queryFn: getSessions, refetchInterval: 5000 });
  const sessions = sessionsQuery.data?.sessions ?? [];

  return (
    <div className="grid gap-4">
      <Card>
        <CardHeader className="flex-row items-center justify-between gap-4 space-y-0">
          <div>
            <CardTitle className="flex items-center gap-2">
              <Users className="h-5 w-5" /> Active sessions
            </CardTitle>
            <CardDescription>Live session monitor backed by SHOW SESSIONS.</CardDescription>
          </div>
          <Button variant="outline" onClick={() => sessionsQuery.refetch()}>
            <RefreshCcw className="mr-2 h-4 w-4" /> Refresh
          </Button>
        </CardHeader>
        <CardContent>
          {sessions.length > 0 ? (
            <div className="overflow-x-auto rounded-md border">
              <Table>
                <THead>
                  <TR>
                    <TH>Session</TH>
                    <TH>User</TH>
                    <TH>State</TH>
                    <TH>Remote</TH>
                    <TH>Last activity</TH>
                  </TR>
                </THead>
                <TBody>
                  {sessions.map((session) => (
                    <TR key={session.sessionId}>
                      <TD className="font-medium">#{session.sessionId}</TD>
                      <TD>{session.user || "-"}</TD>
                      <TD><Badge>{session.state}</Badge></TD>
                      <TD className="text-sm text-muted-foreground">{session.remote}</TD>
                      <TD className="text-sm text-muted-foreground">{session.lastActivity}</TD>
                    </TR>
                  ))}
                </TBody>
              </Table>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">
              {sessionsQuery.isLoading ? "Loading sessions..." : "No active sessions at the moment."}
            </p>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ActivitySquare className="h-5 w-5" /> Query activity
          </CardTitle>
          <CardDescription>Future dashboard endpoint for query history and cancel operations.</CardDescription>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          Planned fields: running SQL, duration, rows processed, cancel token.
        </CardContent>
      </Card>
    </div>
  );
}

