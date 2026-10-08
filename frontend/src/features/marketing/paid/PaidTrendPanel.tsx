import { ChartLine } from 'lucide-react';
import { useState } from 'react';
import { Bar, CartesianGrid, ComposedChart, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

import type { MarketingFilters } from '../filter-memory';
import { formatCount, formatInr, formatPercent, MONTH_NAMES } from '../marketing-format';
import { usePaidTrend, type PaidMonthTotals } from './api';

const AXIS = { fontSize: 12, fill: 'var(--muted-foreground)' };
const INITIAL = { width: 640, height: 260 };
/** Categorical chart tokens (never the status colours). */
const SPEND = 'var(--chart-1)';
const LEADS = 'var(--chart-6)';

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
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-3.5">
        <div>
          <h2 className="font-semibold">Spend vs leads</h2>
          <p className="text-sm text-muted-foreground">Monthly spend with the leads it brought in.</p>
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
          <EmptyState icon={ChartLine} title="No results recorded in this period" className="py-8" />
        ) : (
          <>
            <figure aria-label="Spend vs leads by month" className="space-y-2">
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%" initialDimension={INITIAL}>
                  <ComposedChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                    <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={false} />
                    <YAxis yAxisId="spend" tick={AXIS} tickLine={false} axisLine={false} width={56} tickFormatter={(v: number) => compactInr.format(v)} />
                    <YAxis yAxisId="leads" orientation="right" tick={AXIS} tickLine={false} axisLine={false} width={40} allowDecimals={false} />
                    <Tooltip
                      cursor={{ fill: 'var(--muted)', opacity: 0.4 }}
                      content={({ active, payload }) => {
                        const row = payload?.[0]?.payload as (typeof data)[number] | undefined;
                        if (!active || !row) return null;
                        return (
                          <div className="rounded-lg border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
                            <p className="mb-1 font-medium">{row.full}</p>
                            <p>Spend: {formatInr(row.spend)}</p>
                            <p>Leads: {formatCount(row.leads)}</p>
                            <p>Cost per lead: {formatInr(row.cpl)}</p>
                          </div>
                        );
                      }}
                    />
                    <Bar yAxisId="spend" dataKey="spend" name="Spend" fill={SPEND} radius={[3, 3, 0, 0]} isAnimationActive={false} />
                    <Line yAxisId="leads" type="monotone" dataKey="leads" name="Leads" stroke={LEADS} strokeWidth={2} dot={{ r: 3 }} isAnimationActive={false} />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
              <figcaption>
                <ul className="flex flex-wrap gap-4 text-xs text-muted-foreground" aria-label="Chart legend">
                  <li className="flex items-center gap-1.5">
                    <span className="size-2.5 rounded-sm" style={{ background: SPEND }} aria-hidden />
                    Spend (left axis)
                  </li>
                  <li className="flex items-center gap-1.5">
                    <span className="h-0.5 w-4 rounded" style={{ background: LEADS }} aria-hidden />
                    Leads (right axis)
                  </li>
                </ul>
              </figcaption>
            </figure>
            <Table aria-label="Paid results by month">
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  <TableHead className="text-right">Campaigns</TableHead>
                  <TableHead className="text-right">Spend</TableHead>
                  <TableHead className="text-right">Clicks</TableHead>
                  <TableHead className="text-right">CTR</TableHead>
                  <TableHead className="text-right">Leads</TableHead>
                  <TableHead className="text-right">Cost per lead</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[...(trend.data ?? [])].reverse().map((m) => (
                  <TableRow key={m.period.label}>
                    <TableCell className="font-medium">{m.period.label}</TableCell>
                    <TableCell className="text-right tabular-nums">{m.campaigns}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatInr(m.figures.results.spend)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatCount(m.figures.results.clicks)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatPercent(m.figures.rates.ctr)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatCount(m.figures.results.leads)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatInr(m.figures.rates.costPerLead)}</TableCell>
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
