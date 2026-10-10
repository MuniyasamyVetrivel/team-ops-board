import { ChartLine } from 'lucide-react';
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
import { useEmailTrend, type MonthTotals } from './api';

/** Categorical chart tokens (never the status colours). */
const LEADS = 'var(--chart-1)';
const OPEN = 'var(--chart-3)';
const CLICK = 'var(--chart-4)';

const RANGES = [6, 12, 24] as const;

const shortMonth = (m: MonthTotals) => `${(MONTH_NAMES[m.period.month - 1] ?? '').slice(0, 3)} ${String(m.period.year).slice(2)}`;

/** Brief section 38: the monthly email trend, leads as bars and open/click rates as lines, with a table of the figures. */
export function EmailTrendPanel({ filters }: { filters: MarketingFilters }) {
  const [months, setMonths] = useState<number>(12);
  const trend = useEmailTrend({ month: filters.month, year: filters.year, months, ownerId: filters.ownerId ?? undefined });
  const data = (trend.data ?? []).map((m) => ({ label: shortMonth(m), full: m.period.label, leads: m.counts.leads, open: m.rates.openRate, click: m.rates.clickRate }));
  const hasData = (trend.data ?? []).some((m) => m.campaigns > 0);

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <div>
          <h2 className="text-card-title font-semibold">Monthly email trend</h2>
          <p className="mt-0.5 text-label text-muted-foreground">Leads per month with open and click rates.</p>
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
          <EmptyState icon={ChartLine} title="No campaigns sent in this period" className="py-8" />
        ) : (
          <>
            <figure aria-label="Monthly email trend: leads, open rate and click rate" className="space-y-2">
              <ComboChart
                data={data}
                series={[
                  { key: 'leads', label: 'Leads', color: LEADS, format: formatCount },
                  { key: 'open', label: 'Open rate', color: OPEN, type: 'line', axis: 'right', format: formatPercent },
                  { key: 'click', label: 'Click rate', color: CLICK, type: 'line', axis: 'right', dashed: true, format: formatPercent },
                ]}
                formatRight={(v) => `${v}%`}
              />
            </figure>
            <Table aria-label="Email results by month">
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  <TableHead numeric>Campaigns</TableHead>
                  <TableHead numeric>Emails sent</TableHead>
                  <TableHead numeric>Open rate</TableHead>
                  <TableHead numeric>Click rate</TableHead>
                  <TableHead numeric>Leads</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[...(trend.data ?? [])].reverse().map((m) => (
                  <TableRow key={m.period.label}>
                    <TableCell className="font-medium whitespace-nowrap">{m.period.label}</TableCell>
                    <TableCell numeric>{m.campaigns}</TableCell>
                    <TableCell numeric>{formatCount(m.counts.emailsSent)}</TableCell>
                    <TableCell numeric>{formatPercent(m.rates.openRate)}</TableCell>
                    <TableCell numeric>{formatPercent(m.rates.clickRate)}</TableCell>
                    <TableCell numeric>{formatCount(m.counts.leads)}</TableCell>
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
