import { ChartColumn } from 'lucide-react';
import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

import type { MarketingFilters } from '../filter-memory';
import { formatCount, formatPercent, MONTH_NAMES } from '../marketing-format';
import { useBacklinkTrend, type BacklinkTrendMonth } from './api';

const AXIS = { fontSize: 12, fill: 'var(--muted-foreground)' };
const INITIAL = { width: 640, height: 260 };
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
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-3.5">
        <div>
          <h2 className="font-semibold">Monthly history</h2>
          <p className="text-sm text-muted-foreground">{targetsVisible ? 'Each month against its backlink target.' : 'Backlinks submitted, approved and gone live each month.'}</p>
        </div>
        <Select aria-label="History range" className="w-40" value={months} onChange={(e) => setMonths(Number(e.target.value))}>
          {RANGES.map((r) => (
            <option key={r} value={r}>
              Last {r} months
            </option>
          ))}
        </Select>
      </div>
      <div className="space-y-4 p-5">
        {trend.isPending ? (
          <Skeleton className="h-64" role="status" aria-label="Loading the history" />
        ) : trend.isError ? (
          <ErrorState error={trend.error} title="Couldn't load the history" onRetry={() => void trend.refetch()} />
        ) : !hasData ? (
          <EmptyState icon={ChartColumn} title="No backlink activity in this period" className="py-8" />
        ) : (
          <>
            <figure aria-label="Backlinks by month" className="space-y-2">
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%" initialDimension={INITIAL}>
                  <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                    <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={false} />
                    <YAxis tick={AXIS} tickLine={false} axisLine={false} width={32} allowDecimals={false} />
                    <Tooltip
                      cursor={{ fill: 'var(--muted)', opacity: 0.4 }}
                      content={({ active, payload }) => {
                        const row = payload?.[0]?.payload as (typeof data)[number] | undefined;
                        if (!active || !row) return null;
                        return (
                          <div className="rounded-lg border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
                            <p className="mb-1 font-medium">{row.full}</p>
                            {series.map((s) => (
                              <p key={s.key}>
                                {s.label}: {formatCount(row[s.key])}
                              </p>
                            ))}
                          </div>
                        );
                      }}
                    />
                    {series.map((s) => (
                      <Bar key={s.key} dataKey={s.key} name={s.label} fill={s.color} radius={[3, 3, 0, 0]} isAnimationActive={false} />
                    ))}
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <figcaption>
                <ul className="flex flex-wrap gap-4 text-xs text-muted-foreground" aria-label="Chart legend">
                  {series.map((s) => (
                    <li key={s.key} className="flex items-center gap-1.5">
                      <span className="size-2.5 rounded-sm" style={{ background: s.color }} aria-hidden />
                      {s.label}
                    </li>
                  ))}
                </ul>
              </figcaption>
            </figure>
            <Table aria-label="Backlink history by month">
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  {targetsVisible && <TableHead className="text-right">Target</TableHead>}
                  <TableHead className="text-right">Submitted</TableHead>
                  <TableHead className="text-right">Approved</TableHead>
                  <TableHead className="text-right">Live</TableHead>
                  {targetsVisible && <TableHead className="text-right">Remaining</TableHead>}
                  {targetsVisible && <TableHead className="text-right">Live vs target</TableHead>}
                  <TableHead className="text-right">Rejected</TableHead>
                  <TableHead className="text-right">Lost</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[...points].reverse().map((m) => (
                  <TableRow key={m.activity.period.label}>
                    <TableCell className="font-medium whitespace-nowrap">{m.activity.period.label}</TableCell>
                    {targetsVisible && <TableCell className="text-right tabular-nums">{formatCount(m.targetValue)}</TableCell>}
                    <TableCell className="text-right tabular-nums">{formatCount(m.activity.submitted)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatCount(m.activity.approved)}</TableCell>
                    <TableCell className="text-right font-semibold tabular-nums">{formatCount(m.activity.live)}</TableCell>
                    {targetsVisible && <TableCell className="text-right tabular-nums">{formatCount(m.remaining)}</TableCell>}
                    {targetsVisible && <TableCell className="text-right tabular-nums">{formatPercent(m.liveAchievementPct)}</TableCell>}
                    <TableCell className="text-right tabular-nums">{formatCount(m.activity.rejected)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatCount(m.activity.lost)}</TableCell>
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
