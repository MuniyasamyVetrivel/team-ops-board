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
import { formatCount, MONTH_NAMES } from '../marketing-format';
import type { LeadSource } from '../targets/api';
import { useLeadTrend, type LeadMonthCounts } from './api';
import { TREND_SERIES } from './lead-meta';

const AXIS = { fontSize: 12, fill: 'var(--muted-foreground)' };
const INITIAL = { width: 640, height: 260 };
const RANGES = [6, 12, 24] as const;

const shortMonth = (m: LeadMonthCounts) => `${(MONTH_NAMES[m.period.month - 1] ?? '').slice(0, 3)} ${String(m.period.year).slice(2)}`;
const seriesCount = (m: LeadMonthCounts, sources: LeadSource[]) => sources.reduce((sum, s) => sum + (m.bySource[s] ?? 0), 0);

/** Leads per month, stacked by source, with the figures in a table. */
export function LeadTrendPanel({ filters }: { filters: MarketingFilters }) {
  const [months, setMonths] = useState<number>(12);
  const trend = useLeadTrend({ month: filters.month, year: filters.year, months, ownerId: filters.ownerId ?? undefined });
  const points = trend.data ?? [];
  const data = points.map((m) => ({
    label: shortMonth(m),
    full: m.period.label,
    total: m.total,
    ...Object.fromEntries(TREND_SERIES.map((s) => [s.key, seriesCount(m, s.sources)])),
  })) as ({ label: string; full: string; total: number } & Record<string, number | string>)[];
  const hasData = points.some((m) => m.total > 0);

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-3.5">
        <div>
          <h2 className="font-semibold">Leads by month</h2>
          <p className="text-sm text-muted-foreground">Each month&apos;s leads, by source.</p>
        </div>
        <Select aria-label="Trend range" className="w-40" value={months} onChange={(e) => setMonths(Number(e.target.value))}>
          {RANGES.map((r) => (
            <option key={r} value={r}>
              Last {r} months
            </option>
          ))}
        </Select>
      </div>
      <div className="space-y-4 p-5">
        {trend.isPending ? (
          <Skeleton className="h-64" role="status" aria-label="Loading the trend" />
        ) : trend.isError ? (
          <ErrorState error={trend.error} title="Couldn't load the trend" onRetry={() => void trend.refetch()} />
        ) : !hasData ? (
          <EmptyState icon={ChartColumn} title="No leads in this period" className="py-8" />
        ) : (
          <>
            <figure aria-label="Leads by month and source" className="space-y-2">
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%" initialDimension={INITIAL}>
                  <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                    <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={false} />
                    <YAxis tick={AXIS} tickLine={false} axisLine={false} width={40} allowDecimals={false} />
                    <Tooltip
                      cursor={{ fill: 'var(--muted)', opacity: 0.4 }}
                      content={({ active, payload }) => {
                        const row = payload?.[0]?.payload as (typeof data)[number] | undefined;
                        if (!active || !row) return null;
                        return (
                          <div className="rounded-lg border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
                            <p className="mb-1 font-medium">
                              {row.full}: {formatCount(row.total)} leads
                            </p>
                            {TREND_SERIES.map((s) => (
                              <p key={s.key}>
                                {s.label}: {formatCount(Number(row[s.key]))}
                              </p>
                            ))}
                          </div>
                        );
                      }}
                    />
                    {TREND_SERIES.map((s, i) => (
                      <Bar key={s.key} dataKey={s.key} name={s.label} stackId="leads" fill={s.color} radius={i === TREND_SERIES.length - 1 ? [3, 3, 0, 0] : 0} isAnimationActive={false} />
                    ))}
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <figcaption>
                <ul className="flex flex-wrap gap-4 text-xs text-muted-foreground" aria-label="Chart legend">
                  {TREND_SERIES.map((s) => (
                    <li key={s.key} className="flex items-center gap-1.5">
                      <span className="size-2.5 rounded-sm" style={{ background: s.color }} aria-hidden />
                      {s.label}
                    </li>
                  ))}
                </ul>
              </figcaption>
            </figure>
            <Table aria-label="Leads by month">
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  {TREND_SERIES.map((s) => (
                    <TableHead key={s.key} className="text-right">
                      {s.label}
                    </TableHead>
                  ))}
                  <TableHead className="text-right">Total</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[...points].reverse().map((m) => (
                  <TableRow key={m.period.label}>
                    <TableCell className="font-medium whitespace-nowrap">{m.period.label}</TableCell>
                    {TREND_SERIES.map((s) => (
                      <TableCell key={s.key} className="text-right tabular-nums">
                        {formatCount(seriesCount(m, s.sources))}
                      </TableCell>
                    ))}
                    <TableCell className="text-right font-semibold tabular-nums">{formatCount(m.total)}</TableCell>
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
