import { ChartColumn } from 'lucide-react';
import { useState } from 'react';

import { ChartTooltip, ChartTooltipRow } from '@/components/charts/ChartTooltip';
import { ComboChart } from '@/components/charts/ComboChart';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

import type { MarketingFilters } from '../filter-memory';
import { formatCount, formatPercent, MONTH_NAMES } from '../marketing-format';
import { useContentTrend, type ContentTrendMonth } from './api';

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
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <div>
          <h2 className="text-card-title font-semibold">Monthly history</h2>
          <p className="mt-0.5 text-label text-muted-foreground">{targetsVisible ? 'Blogs published against the blog target.' : 'Blogs planned and published each month.'}</p>
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
          <EmptyState icon={ChartColumn} title="No blogs planned or published in this period" className="py-8" />
        ) : (
          <>
            <figure aria-label="Blogs published by month" className="space-y-2">
              <ComboChart
                data={data}
                series={[...(targetsVisible ? [{ key: 'target' as const, label: 'Target', color: TARGET }] : []), { key: 'published' as const, label: 'Published', color: PUBLISHED }]}
                leftWidth={32}
                renderTooltip={(row) => (
                  <ChartTooltip title={row.full}>
                    {targetsVisible && <ChartTooltipRow color={TARGET} label="Target" value={formatCount(row.target)} />}
                    <ChartTooltipRow color={PUBLISHED} label="Published" value={formatCount(row.published)} />
                    <ChartTooltipRow label="Planned" value={formatCount(row.planned)} />
                  </ChartTooltip>
                )}
              />
            </figure>
            <Table aria-label="Blog history by month">
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  <TableHead numeric>Planned</TableHead>
                  {targetsVisible && <TableHead numeric>Target</TableHead>}
                  <TableHead numeric>Published</TableHead>
                  {targetsVisible && <TableHead numeric>Remaining</TableHead>}
                  {targetsVisible && <TableHead numeric>Achieved</TableHead>}
                  <TableHead numeric>Leads</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[...points].reverse().map((m) => (
                  <TableRow key={m.figures.period.label}>
                    <TableCell className="font-medium whitespace-nowrap">{m.figures.period.label}</TableCell>
                    <TableCell numeric>{formatCount(m.figures.plannedBlogs)}</TableCell>
                    {targetsVisible && <TableCell numeric>{formatCount(m.targetValue)}</TableCell>}
                    <TableCell numeric className="font-semibold">{formatCount(m.figures.publishedBlogs)}</TableCell>
                    {targetsVisible && <TableCell numeric>{formatCount(m.remaining)}</TableCell>}
                    {targetsVisible && <TableCell numeric>{formatPercent(m.achievementPct)}</TableCell>}
                    <TableCell numeric>{formatCount(m.figures.leads)}</TableCell>
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
