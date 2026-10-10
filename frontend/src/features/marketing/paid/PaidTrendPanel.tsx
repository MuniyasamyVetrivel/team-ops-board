import { ChartLine } from 'lucide-react';
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
import { formatCount, formatInr, formatPercent, MONTH_NAMES } from '../marketing-format';
import { usePaidTrend, type PaidMonthTotals } from './api';

/** Categorical chart tokens (never the status colours). */
const SPEND = 'var(--chart-1)';
const LEADS = 'var(--chart-3)';

const RANGES = [6, 12, 24] as const;

const shortMonth = (m: PaidMonthTotals) => `${(MONTH_NAMES[m.period.month - 1] ?? '').slice(0, 3)} ${String(m.period.year).slice(2)}`;
const compactInr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', notation: 'compact', maximumFractionDigits: 1 });

/** Brief section 41: spend (bars) against leads (line) per month, with the figures and cost per lead in a table. */
export function PaidTrendPanel({ filters }: { filters: MarketingFilters }) {
  const [months, setMonths] = useState<number>(12);
  const trend = usePaidTrend({ month: filters.month, year: filters.year, months, ownerId: filters.ownerId ?? undefined });
  const data = (trend.data ?? []).map((m) => ({ label: shortMonth(m), full: m.period.label, spend: m.figures.results.spend, leads: m.figures.results.leads, cpl: m.figures.rates.costPerLead }));
  const hasData = (trend.data ?? []).some((m) => m.campaigns > 0);

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <div>
          <h2 className="text-card-title font-semibold">Spend vs leads</h2>
          <p className="mt-0.5 text-label text-muted-foreground">Monthly spend with the leads it brought in.</p>
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
          <EmptyState icon={ChartLine} title="No results recorded in this period" className="py-8" />
        ) : (
          <>
            <figure aria-label="Spend vs leads by month" className="space-y-2">
              <ComboChart
                data={data}
                series={[
                  { key: 'spend', label: 'Spend', color: SPEND, format: formatInr },
                  { key: 'leads', label: 'Leads', color: LEADS, type: 'line', axis: 'right', format: formatCount },
                ]}
                leftWidth={56}
                formatLeft={(v) => compactInr.format(v)}
                renderTooltip={(row) => (
                  <ChartTooltip title={row.full}>
                    <ChartTooltipRow color={SPEND} label="Spend" value={formatInr(row.spend)} />
                    <ChartTooltipRow color={LEADS} label="Leads" value={formatCount(row.leads)} />
                    <ChartTooltipRow label="Cost per lead" value={formatInr(row.cpl)} />
                  </ChartTooltip>
                )}
              />
            </figure>
            <Table aria-label="Paid results by month">
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  <TableHead numeric>Campaigns</TableHead>
                  <TableHead numeric>Spend</TableHead>
                  <TableHead numeric>Clicks</TableHead>
                  <TableHead numeric>CTR</TableHead>
                  <TableHead numeric>Leads</TableHead>
                  <TableHead numeric>Cost per lead</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[...(trend.data ?? [])].reverse().map((m) => (
                  <TableRow key={m.period.label}>
                    <TableCell className="font-medium whitespace-nowrap">{m.period.label}</TableCell>
                    <TableCell numeric>{m.campaigns}</TableCell>
                    <TableCell numeric>{formatInr(m.figures.results.spend)}</TableCell>
                    <TableCell numeric>{formatCount(m.figures.results.clicks)}</TableCell>
                    <TableCell numeric>{formatPercent(m.figures.rates.ctr)}</TableCell>
                    <TableCell numeric>{formatCount(m.figures.results.leads)}</TableCell>
                    <TableCell numeric>{formatInr(m.figures.rates.costPerLead)}</TableCell>
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
