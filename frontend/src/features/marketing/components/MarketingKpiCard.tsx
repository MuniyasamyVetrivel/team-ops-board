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
  /** Last period's value; when given, the card shows the change. */
  previous?: number | null;
  /** Names the comparison, e.g. "September". */
  previousLabel?: string;
  /** Whether a rise is good news (leads), bad news (cost per lead), or neither (null: spend). */
  better?: 'higher' | 'lower' | null;
  /** A ready-made change line instead of the percentage change from {@code previous}, e.g. a rate's change in points. */
  delta?: Omit<DeltaProps, 'className'>;
  hint?: ReactNode;
  loading?: boolean;
  /** Extra content under the value, e.g. a progress bar. */
  children?: ReactNode;
}

/** A marketing KPI with an optional month-over-month change, on the shared KPI layout. */
export function MarketingKpiCard({ label, icon, value, format = 'count', previous, previousLabel = 'last month', better = 'higher', delta, hint, loading, children }: MarketingKpiCardProps) {
  const change = delta ? (
    delta.value == null ? undefined : <Delta {...delta} />
  ) : previous !== undefined ? (
    <ChangeText current={value} previous={previous} previousLabel={previousLabel} better={better} className="" />
  ) : undefined;
  return (
    <KpiTile label={label} icon={icon} value={formatMetric(value, format)} muted={value == null} change={change} hint={hint} loading={loading}>
      {children}
    </KpiTile>
  );
}

/** "↑ 11.1% vs September" on one line; green or red only when the direction is clearly good or bad. */
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
