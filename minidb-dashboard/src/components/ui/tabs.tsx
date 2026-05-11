import { cn } from "../../lib/utils";

type Tab = {
  id: string;
  label: string;
};

type TabsProps = {
  tabs: Tab[];
  value: string;
  onChange: (id: string) => void;
};

export function Tabs({ tabs, value, onChange }: TabsProps) {
  return (
    <div className="inline-flex h-10 items-center justify-center rounded-md bg-muted p-1 text-muted-foreground">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          onClick={() => onChange(tab.id)}
          className={cn(
            "inline-flex items-center justify-center whitespace-nowrap rounded-sm px-3 py-1.5 text-sm font-medium transition-all",
            value === tab.id && "bg-background text-foreground shadow-sm"
          )}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}

