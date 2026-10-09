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
import { useContentTrend, type ContentTrendMonth } from './api';

const AXIS = { fontSize: 12, fill: 'var(--muted-foreground)' };
const INITIAL = { width: 640, height: 260 };
/** Categorical chart tokens (never the status colours). */
const TARGET = 'var(--chart-3)';
const PUBLISHED = 'var(--chart-1)';
const RANGES = [6, 12, 24] as const;

const shortMonth = (m: ContentTrendMonth) => `${(MONTH_NAMES[m.figures.period.month - 1] ?? '').slice(0, 3)} ${String(m.figures.period.year).slice(2)}`;

/** Brief section 48 "monthly history": blog target against blogs published per month, with the figures in a table. */
export function ContentHistoryPanel({ filters }: { filters: MarketingFilters }) {
  const [months, setMonths] = useState<number>(12);
  const trend = useContentTrend({ month: filters.month, year: filters.year, months, ownerId: filters.ownerId ?? undefined });
  const points = trend.data?.months ?? [];
  const targetsVisible = trend.data?.targetsVisible ?? false;
  const data = points.map((m) => ({ label: shortMonth(m), full: m.figures.period.label, target: m.targetValue, published: m.figures.publishedBlogs, planned: m.figures.plannedBlogs }));
  const hasData = points.some((m) => m.figures.publishedBlogs > 0 || m.figures.plannedBlogs > 0 || m.targetValue !== null);

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-3.5">
        <div>
          <h2 className="font-semibold">Monthly history</h2>
          <p className="text-sm text-muted-foreground">{targetsVisible ? 'Blogs published against the blog target.' : 'Blogs planned and published each month.'}</p>
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
          <EmptyState icon={ChartColumn} title="No blogs planned or published in this period" className="py-8" />
        ) : (
          <>
            <figure aria-label="Blogs published by month" className="space-y-2">
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
                            {targetsVisible && <p>Target: {formatCount(row.target)}</p>}
                            <p>Published: {formatCount(row.published)}</p>
                            <p>Planned: {formatCount(row.planned)}</p>
                          </div>
                        );
                      }}
                    />
                    {targetsVisible && <Bar dataKey="target" name="Target" fill={TARGET} radius={[3, 3, 0, 0]} isAnimationActive={false} />}
                    <Bar dataKey="published" name="Published" fill={PUBLISHED} radius={[3, 3, 0, 0]} isAnimationActive={false} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <figcaption>
                <ul className="flex flex-wrap gap-4 text-xs text-muted-foreground" aria-label="Chart legend">
                  {targetsVisible && (
                    <li className="flex items-center gap-1.5">
                      <span className="size-2.5 rounded-sm" style={{ background: TARGET }} aria-hidden />
                      Blog target
                    </li>
                  )}
                  <li className="flex items-center gap-1.5">
                    <span className="size-2.5 rounded-sm" style={{ background: PUBLISHED }} aria-hidden />
                    Blogs published
                  </li>
                </ul>
              </figcaption>
            </figure>
            <Table aria-label="Blog history by month">
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  <TableHead className="text-right">Planned</TableHead>
                  {targetsVisible && <TableHead className="text-right">Target</TableHead>}
                  <TableHead className="text-right">Published</TableHead>
                  {targetsVisible && <TableHead className="text-right">Remaining</TableHead>}
                  {targetsVisible && <TableHead className="text-right">Achieved</TableHead>}
                  <TableHead className="text-right">Leads</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[...points].reverse().map((m) => (
                  <TableRow key={m.figures.period.label}>
                    <TableCell className="font-medium whitespace-nowrap">{m.figures.period.label}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatCount(m.figures.plannedBlogs)}</TableCell>
                    {targetsVisible && <TableCell className="text-right tabular-nums">{formatCount(m.targetValue)}</TableCell>}
                    <TableCell className="text-right font-semibold tabular-nums">{formatCount(m.figures.publishedBlogs)}</TableCell>
                    {targetsVisible && <TableCell className="text-right tabular-nums">{formatCount(m.remaining)}</TableCell>}
                    {targetsVisible && <TableCell className="text-right tabular-nums">{formatPercent(m.achievementPct)}</TableCell>}
                    <TableCell className="text-right tabular-nums">{formatCount(m.figures.leads)}</TableCell>
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
