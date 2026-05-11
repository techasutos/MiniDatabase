import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { KeyRound, LogIn } from "lucide-react";
import { loginToInstance } from "../lib/api";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";

type LoginPanelProps = {
  onLoggedIn: () => void;
};

export function LoginPanel({ onLoggedIn }: LoginPanelProps) {
  const [host, setHost] = useState("127.0.0.1");
  const [port, setPort] = useState("5544");
  const [user, setUser] = useState("admin");
  const [password, setPassword] = useState("minidb");

  const login = useMutation({
    mutationFn: () => loginToInstance({ host, port: Number(port), user, password }),
    onSuccess: () => onLoggedIn()
  });

  return (
    <div className="mx-auto grid min-h-screen w-full max-w-md place-items-center px-4">
      <Card className="w-full">
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><KeyRound className="h-5 w-5" /> Connect to MiniDB</CardTitle>
          <CardDescription>Authenticate to a database instance before using the dashboard.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3">
          <label className="grid gap-1 text-sm">Host<Input value={host} onChange={(e) => setHost(e.target.value)} /></label>
          <label className="grid gap-1 text-sm">Port<Input value={port} onChange={(e) => setPort(e.target.value)} /></label>
          <label className="grid gap-1 text-sm">User<Input value={user} onChange={(e) => setUser(e.target.value)} /></label>
          <label className="grid gap-1 text-sm">Password<Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></label>

          <Button onClick={() => login.mutate()} disabled={login.isPending}>
            <LogIn className="mr-2 h-4 w-4" /> {login.isPending ? "Connecting" : "Login"}
          </Button>
          {login.error ? <p className="text-sm text-red-600">{(login.error as Error).message}</p> : null}
        </CardContent>
      </Card>
    </div>
  );
}

