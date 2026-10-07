import type { LucideIcon } from 'lucide-react';
import { Link } from 'react-router';

import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';

interface KpiCardProps {
  label: string;
  /** {@code null} means "not available" and renders "—". */
  value: number | null | undefined;
  icon: LucideIcon;
  /** Icon colour; the label and icon carry meaning, never the colour alone. */
  tone?: string;
  /** Highlights the value (e.g. overdue > 0). */
  alert?: boolean;
  hint?: string;
  /** Appended to the value, e.g. "%". */
  suffix?: string;
  to?: string;
  loading?: boolean;
}

/** Compact KPI tile. Links to the matching list when {@code to} is set. */
export function KpiCard({ label, value, icon: Icon, tone = 'text-muted-foreground', alert, hint, suffix, to, loading }: KpiCardProps) {
  const body = (
    <>
      <span className="flex items-center gap-2 text-sm text-muted-foreground">
        <Icon className={cn('size-4 shrink-0', tone)} aria-hidden />
        <span className="truncate">{label}</span>
      </span>
      {loading ? (
        <Skeleton className="mt-2 h-8 w-12" />
      ) : (
        <span className={cn('mt-1 block text-3xl font-semibold tracking-tight tabular-nums', alert && 'text-status-danger', value == null && 'text-muted-foreground')}>
          {value == null ? '—' : `${value.toLocaleString()}${suffix ?? ''}`}
        </span>
      )}
      {hint && <span className="mt-0.5 block truncate text-xs text-muted-foreground">{hint}</span>}
    </>
  );
  const className = 'block rounded-xl border bg-card p-4 shadow-xs';
  return to ? (
    <Link to={to} className={cn(className, 'transition-colors hover:bg-muted/40 focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none')}>
      {body}
    </Link>
  ) : (
    <div className={className}>{body}</div>
  );
}
