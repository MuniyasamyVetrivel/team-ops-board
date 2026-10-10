import type { ReactNode } from 'react';

/** Tooltip card shared by every chart. */
export function ChartTooltip({ title, children }: { title: ReactNode; children?: ReactNode }) {
  return (
    <div className="min-w-36 rounded-lg border bg-popover px-3 py-2.5 text-xs text-popover-foreground shadow-popover">
      <div className="mb-1 font-semibold">{title}</div>
      <div className="space-y-0.5 tabular-nums">{children}</div>
    </div>
  );
}

/** A coloured dot + label + value row inside a tooltip. */
export function ChartTooltipRow({ color, label, value }: { color?: string; label: ReactNode; value: ReactNode }) {
  return (
    <p className="flex items-center gap-2">
      {color && <span className="size-2 shrink-0 rounded-full" style={{ background: color }} aria-hidden />}
      <span className="text-muted-foreground">{label}</span>
      <span className="ml-auto pl-3 font-medium">{value}</span>
    </p>
  );
}
