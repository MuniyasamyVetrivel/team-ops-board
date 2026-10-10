import { ChartColumn } from 'lucide-react';
import { useState } from 'react';

import { ComboChart } from '@/components/charts/ComboChart';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

import type { MarketingFilters } from '../filter-memory';
import { formatCount, formatPercent, MONTH_NAMES } from '../marketing-format';
import { useBacklinkTrend, type BacklinkTrendMonth } from './api';

const RANGES = [6, 12, 24] as const;
/** Categorical chart tokens (never the status colours). */
const SERIES = [
  { key: 'target', label: 'Target', color: 'var(--chart-3)', target: true },
  { key: 'submitted', label: 'Submitted', color: 'var(--chart-2)', target: false },
  { key: 'approved', label: 'Approved', color: 'var(--chart-5)', target: false },
  { key: 'live', label: 'Live', color: 'var(--chart-1)', target: false },
] as const;

const shortMonth = (m: BacklinkTrendMonth) => `${(MONTH_NAMES[m.activity.period.month - 1] ?? '').slice(0, 3)} ${String(m.activity.period.year).slice(2)}`;

/** Brief section 46 "monthly history": target, submitted, approved and live per month, with the figures in a table. */
export function BacklinkHistoryPanel({ filters }: { filters: MarketingFilters }) {
  const [months, setMonths] = useState<number>(12);
  const trend = useBacklinkTrend({ month: filters.month, year: filters.year, months, ownerId: filters.ownerId ?? undefined });
  const points = trend.data?.months ?? [];
  const targetsVisible = trend.data?.targetsVisible ?? false;
  const series = SERIES.filter((s) => targetsVisible || !s.target);
  const data = points.map((m) => ({ label: shortMonth(m), full: m.activity.period.label, target: m.targetValue, submitted: m.activity.submitted, approved: m.activity.approved, live: m.activity.live }));
  const hasData = points.some((m) => m.activity.submitted + m.activity.approved + m.activity.live > 0 || m.targetValue !== null);

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <div>
          <h2 className="text-card-title font-semibold">Monthly history</h2>
          <p className="mt-0.5 text-label text-muted-foreground">{targetsVisible ? 'Each month against its backlink target.' : 'Backlinks submitted, approved and gone live each month.'}</p>
        </div>
        <Select aria-label="History range" className="w-40" value={months} onChange={(e) => setMonths(Number(e.target.value))}>
          {RANGES.map((r) => (
            <option key={r} value={r}>
              Last {r} months
            </option>
          ))}
        </Select>
      </div>
      <div className="space-y-4 p-6">
        {trend.isPending ? (
          <Skeleton className="h-64" role="status" aria-label="Loading the history" />
        ) : trend.isError ? (
          <ErrorState error={trend.error} title="Couldn't load the history" onRetry={() => void trend.refetch()} />
        ) : !hasData ? (
          <EmptyState icon={ChartColumn} title="No backlink activity in this period" className="py-8" />
        ) : (
          <>
            <figure aria-label="Backlinks by month" className="space-y-2">
              <ComboChart data={data} series={series.map((s) => ({ key: s.key, label: s.label, color: s.color, format: formatCount }))} leftWidth={32} />
            </figure>
            <Table aria-label="Backlink history by month">
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  {targetsVisible && <TableHead numeric>Target</TableHead>}
                  <TableHead numeric>Submitted</TableHead>
                  <TableHead numeric>Approved</TableHead>
                  <TableHead numeric>Live</TableHead>
                  {targetsVisible && <TableHead numeric>Remaining</TableHead>}
                  {targetsVisible && <TableHead numeric>Live vs target</TableHead>}
                  <TableHead numeric>Rejected</TableHead>
                  <TableHead numeric>Lost</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[...points].reverse().map((m) => (
                  <TableRow key={m.activity.period.label}>
                    <TableCell className="font-medium whitespace-nowrap">{m.activity.period.label}</TableCell>
                    {targetsVisible && <TableCell numeric>{formatCount(m.targetValue)}</TableCell>}
                    <TableCell numeric>{formatCount(m.activity.submitted)}</TableCell>
                    <TableCell numeric>{formatCount(m.activity.approved)}</TableCell>
                    <TableCell numeric className="font-semibold">{formatCount(m.activity.live)}</TableCell>
                    {targetsVisible && <TableCell numeric>{formatCount(m.remaining)}</TableCell>}
                    {targetsVisible && <TableCell numeric>{formatPercent(m.liveAchievementPct)}</TableCell>}
                    <TableCell numeric>{formatCount(m.activity.rejected)}</TableCell>
                    <TableCell numeric>{formatCount(m.activity.lost)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </>
        )}
      </div>
    </Card>
  );
}
