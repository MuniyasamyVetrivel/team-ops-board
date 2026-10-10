import { CircleCheck, Hourglass, Link2, Send, Target } from 'lucide-react';
import { useState } from 'react';

import { ErrorState } from '@/components/common/ErrorState';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from '@/lib/utils';

import { MarketingKpiCard } from '../components/MarketingKpiCard';
import { TargetBar, TargetStatusBadge } from '../components/TargetProgress';
import type { MarketingFilters } from '../filter-memory';
import { formatCount, formatPercent, MONTH_NAMES } from '../marketing-format';
import { useBacklinkSummary } from './api';
import { BACKLINK_TYPE_LABELS } from './backlink-meta';

type Compare = 'previous' | 'lastYear';

/**
 * Brief section 46: the month's backlink target against what was submitted, approved and went live, with what is
 * still to be submitted (50 target, 35 submitted, 28 approved, 22 live, 15 remaining). Each stage counts in the month
 * of its own date. The target shows only for TARGET_VIEW holders (decided by the server).
 */
export function BacklinkSummary({ filters }: { filters: MarketingFilters }) {
  const [compare, setCompare] = useState<Compare>('previous');
  const summary = useBacklinkSummary({
    month: filters.month,
    year: filters.year,
    ownerId: filters.ownerId ?? undefined,
    ...(compare === 'lastYear' ? { compareMonth: filters.month, compareYear: filters.year - 1 } : {}),
  });

  if (summary.isError) {
    return (
      <Card>
        <ErrorState error={summary.error} title="Couldn't load the monthly progress" onRetry={() => void summary.refetch()} />
      </Card>
    );
  }
  if (!summary.data) {
    return (
      <div className="grid gap-4 sm:gap-6 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-5" role="status" aria-label="Loading the monthly progress">
        {Array.from({ length: 5 }, (_, i) => (
          <Skeleton key={i} className="h-28 rounded-xl" />
        ))}
      </div>
    );
  }

  const { current, comparison, target, targetsVisible } = summary.data;
  const short = MONTH_NAMES[comparison.period.month - 1] ?? comparison.period.label;
  const compareLabel = comparison.period.year === current.period.year ? short : comparison.period.label;
  const loading = summary.isPlaceholderData;
  const goal = target?.target;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-card-title font-semibold">Monthly progress · {current.period.label}</h2>
        <Select aria-label="Compare with" className="w-56" value={compare} onChange={(e) => setCompare(e.target.value as Compare)}>
          <option value="previous">Compare with the previous month</option>
          <option value="lastYear">Compare with the same month last year</option>
        </Select>
      </div>
      <section aria-label={`Backlink progress for ${current.period.label}`} className={cn('grid gap-3 sm:grid-cols-2', targetsVisible ? 'xl:grid-cols-5' : 'xl:grid-cols-3')}>
        {targetsVisible && (
          <MarketingKpiCard label="Target" icon={Target} value={target?.targetValue ?? null} loading={loading} hint={target ? 'Backlinks to go live this month' : `No backlink target for ${current.period.label}`}>
            {goal && (
              <div className="mt-1">
                <TargetStatusBadge status={goal.status} />
              </div>
            )}
          </MarketingKpiCard>
        )}
        <MarketingKpiCard label="Submitted" icon={Send} value={current.submitted} previous={comparison.submitted} previousLabel={compareLabel} loading={loading} />
        <MarketingKpiCard label="Approved" icon={CircleCheck} value={current.approved} previous={comparison.approved} previousLabel={compareLabel} loading={loading} />
        <MarketingKpiCard label="Live" icon={Link2} value={current.live} previous={comparison.live} previousLabel={compareLabel} loading={loading}>
          {goal && (
            <div className="mt-2 space-y-1">
              <TargetBar label="Live against target" achievementPct={goal.achievementPct} status={goal.status} />
              <p className="text-xs text-muted-foreground tabular-nums">{formatPercent(goal.achievementPct)} of target live</p>
            </div>
          )}
        </MarketingKpiCard>
        {targetsVisible && <MarketingKpiCard label="Remaining" icon={Hourglass} value={target?.remaining ?? null} loading={loading} hint="Target − submitted: still to submit" />}
      </section>
      <p className="text-sm text-muted-foreground">
        Also this month: {formatCount(current.rejected)} rejected · {formatCount(current.lost)} lost. Each stage counts in the month of its own date.
      </p>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,20rem)]">
        <Card className={cn(loading && 'opacity-60')}>
          <div className="border-b px-6 py-4">
            <h3 className="font-semibold">By owner</h3>
            <p className="text-sm text-muted-foreground">{current.period.label}</p>
          </div>
          {summary.data.byOwner.length === 0 ? (
            <p className="px-6 py-4 text-sm text-muted-foreground">No backlink activity this month.</p>
          ) : (
            <Table aria-label="Backlinks by owner">
              <TableHeader>
                <TableRow>
                  <TableHead>Owner</TableHead>
                  <TableHead numeric>Submitted</TableHead>
                  <TableHead numeric>Approved</TableHead>
                  <TableHead numeric>Live</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {summary.data.byOwner.map((o) => (
                  <TableRow key={o.owner?.id ?? 'none'}>
                    <TableCell className="font-medium">{o.owner?.fullName ?? <span className="text-muted-foreground">No owner</span>}</TableCell>
                    <TableCell numeric>{formatCount(o.submitted)}</TableCell>
                    <TableCell numeric>{formatCount(o.approved)}</TableCell>
                    <TableCell numeric className="font-semibold">{formatCount(o.live)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Card>

        <Card className={cn(loading && 'opacity-60')}>
          <div className="border-b px-6 py-4">
            <h3 className="font-semibold">Live by type</h3>
            <p className="text-sm text-muted-foreground">Gone live in {current.period.label}</p>
          </div>
          {summary.data.liveByType.length === 0 ? (
            <p className="px-6 py-4 text-sm text-muted-foreground">Nothing went live this month yet.</p>
          ) : (
            <ul className="divide-y" aria-label="Live by type">
              {summary.data.liveByType.map((t) => (
                <li key={t.linkType} className="flex items-center justify-between px-6 py-2.5 text-sm">
                  <span>{BACKLINK_TYPE_LABELS[t.linkType]}</span>
                  <span className="font-medium tabular-nums">{formatCount(t.live)}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  );
}
