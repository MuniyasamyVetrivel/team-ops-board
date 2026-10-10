import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';

import { Delta, type DeltaProps } from './Delta';

interface KpiTileProps {
  label: string;
  icon: LucideIcon;
  /** Icon colour; the label and icon carry meaning, never the colour alone. */
  tone?: string;
  /** The formatted value. */
  value: ReactNode;
  /** Greys the value out, e.g. for "—". */
  muted?: boolean;
  /** Highlights the value (e.g. overdue > 0). */
  alert?: boolean;
  /** The delta line under the value. */
  change?: ReactNode;
  /** Extra content under the delta, e.g. a progress bar. */
  children?: ReactNode;
  hint?: ReactNode;
  to?: string;
  loading?: boolean;
}

/**
 * The KPI layout shared by every KPI card: icon + label, big value, delta line, helper text, each on its own line.
 * Nothing is truncated (labels and hints wrap), and the tile stretches to the height of its row.
 */
export function KpiTile({ label, icon: Icon, tone = 'text-muted-foreground', value, muted, alert, change, children, hint, to, loading }: KpiTileProps) {
  const body = (
    <>
      <span className="flex items-start gap-2.5">
        <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-muted">
          <Icon className={cn('size-4', tone)} aria-hidden />
        </span>
        <span className="min-w-0 pt-1.5 text-label font-medium break-words text-muted-foreground">{label}</span>
      </span>
      {loading ? (
        <Skeleton className="mt-4 h-10 w-20" />
      ) : (
        <span className={cn('mt-4 block text-kpi font-semibold tracking-tight tabular-nums', alert && 'text-status-danger', muted && 'text-muted-foreground')}>{value}</span>
      )}
      {!loading && change && <span className="mt-1 block">{change}</span>}
      {!loading && children}
      {hint && <span className="mt-1 block text-xs text-muted-foreground">{hint}</span>}
    </>
  );
  const className = 'flex h-full min-w-0 flex-col rounded-xl border bg-card p-5 shadow-card';
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

interface KpiCardProps {
  label: string;
  /** {@code null} means "not available" and renders "—". */
  value: number | null | undefined;
  icon: LucideIcon;
  tone?: string;
  alert?: boolean;
  /** Change against the previous period, e.g. {@code { value: 8, label: 'vs last week' }}. */
  delta?: Omit<DeltaProps, 'className'>;
  hint?: string;
  /** Appended to the value, e.g. "%". */
  suffix?: string;
  to?: string;
  loading?: boolean;
}

/** A count KPI. Links to the matching list when {@code to} is set. */
export function KpiCard({ value, suffix, delta, ...props }: KpiCardProps) {
  return (
    <KpiTile
      {...props}
      value={value == null ? '—' : `${value.toLocaleString()}${suffix ?? ''}`}
      muted={value == null}
      change={delta && delta.value != null ? <Delta {...delta} /> : undefined}
    />
  );
}
