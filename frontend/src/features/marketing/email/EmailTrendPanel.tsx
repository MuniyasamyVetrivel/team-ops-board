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
import { formatCount, formatPercent, MONTH_NAMES } from '../marketing-format';
import { useEmailTrend, type MonthTotals } from './api';

const AXIS = { fontSize: 12, fill: 'var(--muted-foreground)' };
const INITIAL = { width: 640, height: 260 };
/** Categorical chart tokens (never the status colours). */
const LEADS = 'var(--chart-6)';
const OPEN = 'var(--chart-1)';
const CLICK = 'var(--chart-2)';

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
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-3.5">
        <div>
          <h2 className="font-semibold">Monthly email trend</h2>
          <p className="text-sm text-muted-foreground">Leads per month with open and click rates.</p>
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
          <EmptyState icon={ChartLine} title="No campaigns sent in this period" className="py-8" />
        ) : (
          <>
            <figure aria-label="Monthly email trend: leads, open rate and click rate" className="space-y-2">
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%" initialDimension={INITIAL}>
                  <ComposedChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                    <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={false} />
                    <YAxis yAxisId="leads" tick={AXIS} tickLine={false} axisLine={false} width={40} allowDecimals={false} />
                    <YAxis yAxisId="rate" orientation="right" tick={AXIS} tickLine={false} axisLine={false} width={44} tickFormatter={(v: number) => `${v}%`} />
                    <Tooltip
                      cursor={{ fill: 'var(--muted)', opacity: 0.4 }}
                      content={({ active, payload }) => {
                        const row = payload?.[0]?.payload as (typeof data)[number] | undefined;
                        if (!active || !row) return null;
                        return (
                          <div className="rounded-lg border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
                            <p className="mb-1 font-medium">{row.full}</p>
                            <p>Leads: {formatCount(row.leads)}</p>
                            <p>Open rate: {formatPercent(row.open)}</p>
                            <p>Click rate: {formatPercent(row.click)}</p>
                          </div>
                        );
                      }}
                    />
                    <Bar yAxisId="leads" dataKey="leads" name="Leads" fill={LEADS} radius={[3, 3, 0, 0]} isAnimationActive={false} />
                    <Line yAxisId="rate" type="monotone" dataKey="open" name="Open rate" stroke={OPEN} strokeWidth={2} dot={{ r: 3 }} connectNulls={false} isAnimationActive={false} />
                    <Line yAxisId="rate" type="monotone" dataKey="click" name="Click rate" stroke={CLICK} strokeWidth={2} strokeDasharray="5 3" dot={{ r: 3 }} connectNulls={false} isAnimationActive={false} />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
              <figcaption>
                <ul className="flex flex-wrap gap-4 text-xs text-muted-foreground" aria-label="Chart legend">
                  <li className="flex items-center gap-1.5">
                    <span className="size-2.5 rounded-sm" style={{ background: LEADS }} aria-hidden />
                    Leads (left axis)
                  </li>
                  <li className="flex items-center gap-1.5">
                    <span className="h-0.5 w-4 rounded" style={{ background: OPEN }} aria-hidden />
                    Open rate (right axis)
                  </li>
                  <li className="flex items-center gap-1.5">
                    <span className="w-4 border-t-2 border-dashed" style={{ borderColor: CLICK }} aria-hidden />
                    Click rate (right axis)
                  </li>
                </ul>
              </figcaption>
            </figure>
            <Table aria-label="Email results by month">
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  <TableHead className="text-right">Campaigns</TableHead>
                  <TableHead className="text-right">Emails sent</TableHead>
                  <TableHead className="text-right">Open rate</TableHead>
                  <TableHead className="text-right">Click rate</TableHead>
                  <TableHead className="text-right">Leads</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {[...(trend.data ?? [])].reverse().map((m) => (
                  <TableRow key={m.period.label}>
                    <TableCell className="font-medium">{m.period.label}</TableCell>
                    <TableCell className="text-right tabular-nums">{m.campaigns}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatCount(m.counts.emailsSent)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatPercent(m.rates.openRate)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatPercent(m.rates.clickRate)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatCount(m.counts.leads)}</TableCell>
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
