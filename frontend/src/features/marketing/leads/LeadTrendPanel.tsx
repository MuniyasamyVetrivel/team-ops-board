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
import { formatCount, MONTH_NAMES } from '../marketing-format';
import type { LeadSource } from '../targets/api';
import { useLeadTrend, type LeadMonthCounts } from './api';
import { TREND_SERIES } from './lead-meta';

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
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <div>
          <h2 className="text-card-title font-semibold">Leads by month</h2>
          <p className="mt-0.5 text-label text-muted-foreground">Each month&apos;s leads, by source.</p>
        </div>
        <Select aria-label="Trend range" className="w-40" value={months} onChange={(e) => setMonths(Number(e.target.value))}>
          {RANGES.map((r) => (
            <option key={r} value={r}>
              Last {r} months
            </option>
          ))}
        </Select>
      </div>
      <div className="space-y-4 p-6">
        {trend.isPending ? (
          <Skeleton className="h-64" role="status" aria-label="Loading the trend" />
        ) : trend.isError ? (
          <ErrorState error={trend.error} title="Couldn't load the trend" onRetry={() => void trend.refetch()} />
        ) : !hasData ? (
          <EmptyState icon={ChartColumn} title="No leads in this period" className="py-8" />
        ) : (
          <>
            <figure aria-label="Leads by month and source" className="space-y-2">
              <ComboChart
                data={data}
                stacked
                series={TREND_SERIES.map((s) => ({ key: s.key, label: s.label, color: s.color }))}
                renderTooltip={(row) => (
                  <ChartTooltip title={`${row.full}: ${formatCount(row.total)} leads`}>
                    {TREND_SERIES.map((s) => (
                      <ChartTooltipRow key={s.key} color={s.color} label={s.label} value={formatCount(Number(row[s.key]))} />
                    ))}
                  </ChartTooltip>
                )}
              />
            </figure>
            <Table aria-label="Leads by month">
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  {TREND_SERIES.map((s) => (
                    <TableHead key={s.key} numeric>
                      {s.label}
                    </TableHead>
                  ))}
                  <TableHead numeric>Total</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[...points].reverse().map((m) => (
                  <TableRow key={m.period.label}>
                    <TableCell className="font-medium whitespace-nowrap">{m.period.label}</TableCell>
                    {TREND_SERIES.map((s) => (
                      <TableCell key={s.key} numeric>
                        {formatCount(seriesCount(m, s.sources))}
                      </TableCell>
                    ))}
                    <TableCell numeric className="font-semibold">{formatCount(m.total)}</TableCell>
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
