import type { LucideIcon } from 'lucide-react';
import type { ComponentProps, ReactNode } from 'react';
import { Link } from 'react-router';

import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';

import { Delta, type DeltaProps } from './Delta';

/** The grid every KPI row uses: 4 per row on desktop, 2 on tablet, 1 on phone, 16px gaps, equal heights. */
export function KpiGrid({ className, ...props }: ComponentProps<'section'>) {
  return <section className={cn('grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4', className)} {...props} />;
}

interface KpiTileProps {
  label: string;
  icon?: LucideIcon;
  /** Icon colour; the label carries the meaning, never the colour alone. */
  tone?: string;
  /** Replaces the icon on the right of the label row, e.g. a target status badge. */
  aside?: ReactNode;
  /** The formatted value. Always 28px, whatever the state. */
  value: ReactNode;
  /** Zero or missing: the value is shown in muted colour. */
  muted?: boolean;
  /** Overdue, SLA breaches, over budget: red value and border plus a "Critical" tag. */
  critical?: boolean;
  /** The delta chip beside the value. */
  delta?: ReactNode;
  /** One line of helper text; always reserved so tiles line up, and cut with an ellipsis (full text in a tooltip). */
  hint?: ReactNode;
  /** Optional 4px progress bar, only where a ratio exists (see {@link KpiProgress}). */
  progress?: ReactNode;
  to?: string;
  loading?: boolean;
}

/**
 * The one KPI tile (dashboards, marketing, reports): about 112px tall. Row 1: label and a small icon. Row 2: the value
 * with a delta chip. Row 3: helper text. Row 4 (optional): a 4px progress bar.
 */
export function KpiTile({ label, icon: Icon, tone = 'text-muted-foreground', aside, value, muted, critical, delta, hint, progress, to, loading }: KpiTileProps) {
  const body = (
    <>
      <span className="flex min-h-6 items-center justify-between gap-2">
        <span className="truncate text-label font-medium text-muted-foreground" title={label}>
          {label}
        </span>
        <span className="flex shrink-0 items-center gap-1.5">
          {critical && <span className="rounded-full bg-status-danger/10 px-1.5 text-[11px] leading-5 font-semibold text-status-danger">Critical</span>}
          {aside ?? (Icon && <Icon className={cn('size-4', critical ? 'text-status-danger' : tone)} aria-hidden />)}
        </span>
      </span>
      {loading ? (
        <Skeleton className="mt-1 h-9 w-24" />
      ) : (
        <span className="mt-1 flex min-w-0 items-center gap-2">
          <span className={cn('text-kpi font-semibold tracking-tight whitespace-nowrap tabular-nums', critical ? 'text-status-danger' : muted && 'text-muted-foreground')}>{value}</span>
          {delta}
        </span>
      )}
      <span className="mt-0.5 block truncate text-xs text-muted-foreground" title={typeof hint === 'string' ? hint : undefined}>
        {loading ? ' ' : (hint ?? ' ')}
      </span>
      {!loading && progress && <span className="mt-auto block pt-2.5">{progress}</span>}
    </>
  );
  const className = cn('flex h-full min-h-28 min-w-0 flex-col rounded-xl border bg-card px-4 py-3.5 shadow-card', critical && 'border-status-danger/50');
  return to ? (
    <Link
      to={to}
      className={cn(className, 'transition-[border-color,box-shadow] hover:border-primary/40 focus-visible:ring-[3px] focus-visible:ring-ring/40 focus-visible:outline-none', critical && 'hover:border-status-danger')}
    >
      {body}
    </Link>
  ) : (
    <div className={className}>{body}</div>
  );
}

/** The 4px bar in a tile's last row: brand blue for a ratio, amber for target progress. Capped at 100%. */
export function KpiProgress({ value, label, tone = 'primary' }: { value: number | null; label: string; tone?: 'primary' | 'highlight' }) {
  const filled = Math.min(Math.max(value ?? 0, 0), 100);
  return (
    <span role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(filled)} className="block h-1 overflow-hidden rounded-full bg-muted">
      <span className={cn('block h-full rounded-full', tone === 'highlight' ? 'bg-highlight' : 'bg-primary')} style={{ width: `${filled}%` }} />
    </span>
  );
}

interface KpiCardProps {
  label: string;
  /** {@code null} means "not available" and renders "—". */
  value: number | null | undefined;
  icon: LucideIcon;
  tone?: string;
  critical?: boolean;
  /** Change against the previous period, e.g. {@code { value: 8, label: 'vs last week' }}. */
  delta?: Omit<DeltaProps, 'className' | 'variant'>;
  hint?: string;
  /** Appended to the value, e.g. "%". */
  suffix?: string;
  progress?: ReactNode;
  to?: string;
  loading?: boolean;
}

/** A count KPI on the shared tile. Links to the matching list when {@code to} is set. */
export function KpiCard({ value, suffix, delta, ...props }: KpiCardProps) {
  return (
    <KpiTile
      {...props}
      value={value == null ? '—' : `${value.toLocaleString()}${suffix ?? ''}`}
      muted={value == null || value === 0}
      delta={delta && delta.value != null ? <Delta {...delta} variant="chip" /> : undefined}
    />
  );
}
