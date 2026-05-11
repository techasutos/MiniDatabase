import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { CheckCircle2, Info, XCircle } from "lucide-react";

type ToastType = "success" | "error" | "info";

type ToastMessage = {
  id: number;
  title: string;
  description?: string;
  type: ToastType;
};

type ToastInput = {
  title: string;
  description?: string;
  type?: ToastType;
};

type ToastContextValue = {
  pushToast: (input: ToastInput) => void;
};

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [messages, setMessages] = useState<ToastMessage[]>([]);

  const pushToast = useCallback((input: ToastInput) => {
    const id = Date.now() + Math.floor(Math.random() * 1000);
    const message: ToastMessage = {
      id,
      title: input.title,
      description: input.description,
      type: input.type ?? "info"
    };
    setMessages((current) => [...current, message]);
    window.setTimeout(() => {
      setMessages((current) => current.filter((item) => item.id !== id));
    }, 3200);
  }, []);

  const value = useMemo(() => ({ pushToast }), [pushToast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed right-4 top-4 z-[100] grid w-[340px] gap-2">
        {messages.map((message) => (
          <ToastItem key={message.id} message={message} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

function ToastItem({ message }: { message: ToastMessage }) {
  const icon =
    message.type === "success" ? <CheckCircle2 className="h-4 w-4 text-green-600" /> :
      message.type === "error" ? <XCircle className="h-4 w-4 text-red-600" /> :
        <Info className="h-4 w-4 text-blue-600" />;

  return (
    <div className="pointer-events-auto rounded-md border bg-background p-3 shadow-lg">
      <div className="flex items-start gap-2">
        {icon}
        <div>
          <p className="text-sm font-medium">{message.title}</p>
          {message.description ? <p className="text-xs text-muted-foreground">{message.description}</p> : null}
        </div>
      </div>
    </div>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error("useToast must be used within ToastProvider");
  }
  return ctx;
}

