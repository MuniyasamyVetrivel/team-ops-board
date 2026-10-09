import { ArrowDownRight, ArrowUpRight, Minus, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';

import { formatMetric, percentChange, type MetricFormat } from '../marketing-format';

interface MarketingKpiCardProps {
  label: string;
  icon: LucideIcon;
  /** null renders "—" (no data, or a division by zero on the server). */
  value: number | null | undefined;
  format?: MetricFormat;
  /** Last period's value; when given, the card shows the change. */
  previous?: number | null;
  /** Names the comparison, e.g. "September". */
  previousLabel?: string;
  /** Whether a rise is good news (leads), bad news (cost per lead), or neither (null: spend). */
  better?: 'higher' | 'lower' | null;
  hint?: string;
  loading?: boolean;
  /** Extra content under the value, e.g. a progress bar. */
  children?: ReactNode;
}

/** A marketing KPI with an optional month-over-month change. Direction is shown by arrow and text, not colour alone. */
export function MarketingKpiCard({ label, icon: Icon, value, format = 'count', previous, previousLabel = 'last month', better = 'higher', hint, loading, children }: MarketingKpiCardProps) {
  return (
    <div className="rounded-xl border bg-card p-4 shadow-xs">
      <span className="flex items-center gap-2 text-sm text-muted-foreground">
        <Icon className="size-4 shrink-0" aria-hidden />
        <span className="truncate">{label}</span>
      </span>
      {loading ? (
        <Skeleton className="mt-2 h-8 w-20" />
      ) : (
        <span className={cn('mt-1 block text-2xl font-semibold tracking-tight tabular-nums', value == null && 'text-muted-foreground')}>
          {formatMetric(value, format)}
        </span>
      )}
      {!loading && previous !== undefined && <ChangeText current={value} previous={previous} previousLabel={previousLabel} better={better} />}
      {!loading && children}
      {hint && <span className="mt-0.5 block truncate text-xs text-muted-foreground">{hint}</span>}
    </div>
  );
}

/** "Up 11.1% vs September" with an arrow; green or red only when the direction is clearly good or bad. */
export function ChangeText({
  current,
  previous,
  previousLabel,
  better,
  className = 'mt-1',
}: {
  current: number | null | undefined;
  previous: number | null;
  previousLabel: string;
  better: 'higher' | 'lower' | null;
  className?: string;
}) {
  const change = percentChange(current, previous);
  if (change === null) {
    return <span className={cn('block text-xs text-muted-foreground', className)}>No comparison with {previousLabel}</span>;
  }
  const Icon = change > 0 ? ArrowUpRight : change < 0 ? ArrowDownRight : Minus;
  const good = change === 0 || better === null ? null : (change > 0) === (better === 'higher');
  const text = change === 0 ? `No change vs ${previousLabel}` : `${change > 0 ? 'Up' : 'Down'} ${Math.abs(change)}% vs ${previousLabel}`;
  return (
    <span className={cn('flex items-center gap-1 text-xs font-medium', className, good === null ? 'text-muted-foreground' : good ? 'text-status-success' : 'text-status-danger')}>
      <Icon className="size-3.5" aria-hidden />
      {text}
    </span>
  );
}
