import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

import { Delta, type DeltaProps } from '@/components/common/Delta';
import { KpiTile } from '@/components/common/KpiCard';
import { cn } from '@/lib/utils';

import { formatMetric, percentChange, type MetricFormat } from '../marketing-format';

interface MarketingKpiCardProps {
  label: string;
  icon: LucideIcon;
  /** null renders "—" (no data, or a division by zero on the server). */
  value: number | null | undefined;
  format?: MetricFormat;
  /** Last period's value; when given, the tile shows the change as a chip. */
  previous?: number | null;
  /** Names the comparison, e.g. "September". */
  previousLabel?: string;
  /** Whether a rise is good news (leads), bad news (cost per lead), or neither (null: spend). */
  better?: 'higher' | 'lower' | null;
  /** A ready-made change instead of the percentage change from {@code previous}, e.g. a rate's change in points. */
  delta?: Omit<DeltaProps, 'className' | 'variant'>;
  /** The helper line. Defaults to the comparison ("vs September"). */
  hint?: ReactNode;
  /** Replaces the icon, e.g. a target status badge. */
  aside?: ReactNode;
  /** A 4px bar where a ratio exists, e.g. a target's achievement. */
  progress?: ReactNode;
  critical?: boolean;
  loading?: boolean;
}

/** A marketing KPI on the shared tile, with the month-over-month change as a chip beside the value. */
export function MarketingKpiCard({ label, icon, value, format = 'count', previous, previousLabel = 'last month', better = 'higher', delta, hint, aside, progress, critical, loading }: MarketingKpiCardProps) {
  const change = previous !== undefined ? percentChange(value, previous) : null;
  const chip = delta ? (
    delta.value == null ? undefined : <Delta {...delta} variant="chip" />
  ) : change !== null ? (
    <Delta value={change} amount={`${Math.abs(change)}%`} label={`vs ${previousLabel}`} better={better} variant="chip" />
  ) : undefined;
  const comparison = delta?.label ?? (previous !== undefined ? (change === null ? `No comparison with ${previousLabel}` : `vs ${previousLabel}`) : undefined);
  return (
    <KpiTile
      label={label}
      icon={icon}
      aside={aside}
      value={formatMetric(value, format)}
      muted={value == null || value === 0}
      critical={critical}
      delta={chip}
      // The chip already reads its comparison out, so a "vs September" helper is for sighted users only.
      hint={hint ?? (comparison && (chip ? <span aria-hidden>{comparison}</span> : comparison))}
      progress={progress}
      loading={loading}
    />
  );
}

/** "↑ 11.1% vs September" on one line, for rows and tables; green or red only when the direction is clearly good or bad. */
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
  return <Delta value={change} amount={`${Math.abs(change)}%`} label={`vs ${previousLabel}`} better={better} className={className} />;
}
