import type { LucideIcon } from 'lucide-react';
import { Link } from 'react-router';

import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';

import { Delta, type DeltaProps } from './Delta';

interface KpiCardProps {
  label: string;
  /** {@code null} means "not available" and renders "—". */
  value: number | null | undefined;
  icon: LucideIcon;
  /** Icon colour; the label and icon carry meaning, never the colour alone. */
  tone?: string;
  /** Highlights the value (e.g. overdue > 0). */
  alert?: boolean;
  /** Change against the previous period, e.g. {@code { value: 8, label: 'vs last week' }}. */
  delta?: Omit<DeltaProps, 'className'>;
  hint?: string;
  /** Appended to the value, e.g. "%". */
  suffix?: string;
  to?: string;
  loading?: boolean;
}

/**
 * KPI tile: icon + label, big value, delta line, helper text, each on its own line. Nothing is truncated (labels and
 * hints wrap), and the tile stretches to the height of its row.
 */
export function KpiCard({ label, value, icon: Icon, tone = 'text-muted-foreground', alert, delta, hint, suffix, to, loading }: KpiCardProps) {
  const body = (
    <>
      <span className="flex items-start gap-2.5">
        <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-muted">
          <Icon className={cn('size-4', tone)} aria-hidden />
        </span>
        <span className="pt-1.5 text-label font-medium text-muted-foreground">{label}</span>
      </span>
      {loading ? (
        <Skeleton className="mt-4 h-10 w-20" />
      ) : (
        <span
          className={cn(
            'mt-4 block text-kpi font-semibold tracking-tight tabular-nums',
            alert && 'text-status-danger',
            value == null && 'text-muted-foreground',
          )}
        >
          {value == null ? '—' : `${value.toLocaleString()}${suffix ?? ''}`}
        </span>
      )}
      {!loading && delta && delta.value != null && <Delta {...delta} className="mt-1 flex" />}
      {hint && <span className="mt-1 block text-xs text-muted-foreground">{hint}</span>}
    </>
  );
  const className = 'flex h-full flex-col rounded-xl border bg-card p-5 shadow-card';
  return to ? (
    <Link
      to={to}
      className={cn(className, 'transition-[border-color,box-shadow] hover:border-primary/40 focus-visible:ring-[3px] focus-visible:ring-ring/40 focus-visible:outline-none')}
    >
      {body}
    </Link>
  ) : (
    <div className={className}>{body}</div>
  );
}
