import { ChartLine } from 'lucide-react';
import { useState } from 'react';
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { Panel } from '@/components/common/Panel';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

import { formatCount, formatInr, MONTH_NAMES } from '../marketing-format';
import type { DashboardTrendMonth } from './api';

const AXIS = { fontSize: 12, fill: 'var(--muted-foreground)' };
const INITIAL = { width: 640, height: 240 };
const LINE = 'var(--chart-1)';

type MetricKey = Exclude<keyof DashboardTrendMonth, 'period'>;

/** The trend series; a module the viewer cannot see has nulls throughout and is left out. */
const METRICS: { key: MetricKey; label: string; money?: boolean }[] = [
  { key: 'leads', label: 'Leads' },
  { key: 'top10Keywords', label: 'Top 10 keywords' },
  { key: 'emailLeads', label: 'Email leads' },
  { key: 'linkedinLeads', label: 'LinkedIn leads' },
  { key: 'linkedinSpend', label: 'LinkedIn spend', money: true },
  { key: 'backlinksLive', label: 'Backlinks live' },
  { key: 'blogsPublished', label: 'Blogs published' },
];

const RANGES = [6, 12, 24] as const;

const shortMonth = (m: DashboardTrendMonth) => `${(MONTH_NAMES[m.period.month - 1] ?? '').slice(0, 3)} ${String(m.period.year).slice(2)}`;

interface DashboardTrendPanelProps {
  trend: DashboardTrendMonth[];
  months: number;
  onMonthsChange: (months: number) => void;
}

/** Brief section 61 "monthly trend": one metric charted at a time, every metric in the table below. */
export function DashboardTrendPanel({ trend, months, onMonthsChange }: DashboardTrendPanelProps) {
  const visible = METRICS.filter((m) => trend.some((t) => t[m.key] !== null));
  const [picked, setPicked] = useState<MetricKey>('leads');
  const metric = visible.find((m) => m.key === picked) ?? visible[0];
  const format = (value: number | null) => (metric?.money ? formatInr(value) : formatCount(value));
  const data = trend.map((t) => ({ label: shortMonth(t), full: t.period.label, value: metric ? t[metric.key] : null }));

  return (
    <Panel
      title="Monthly trend"
      icon={ChartLine}
      description={`The last ${trend.length} months`}
      action={
        <div className="flex flex-wrap gap-2">
          {visible.length > 0 && (
            <Select aria-label="Trend metric" className="w-44" value={metric?.key ?? ''} onChange={(e) => setPicked(e.target.value as MetricKey)}>
              {visible.map((m) => (
                <option key={m.key} value={m.key}>
                  {m.label}
                </option>
              ))}
            </Select>
          )}
          <Select aria-label="Trend range" className="w-36" value={months} onChange={(e) => onMonthsChange(Number(e.target.value))}>
            {RANGES.map((r) => (
              <option key={r} value={r}>
                Last {r} months
              </option>
            ))}
          </Select>
        </div>
      }
    >
      {visible.length === 0 || !metric ? (
        <p className="px-5 py-4 text-sm text-muted-foreground">No marketing modules to chart for your access.</p>
      ) : (
        <div className="space-y-4 p-5">
          <figure aria-label={`${metric.label} by month`}>
            <div className="h-60">
              <ResponsiveContainer width="100%" height="100%" initialDimension={INITIAL}>
                <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                  <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={false} />
                  <YAxis tick={AXIS} tickLine={false} axisLine={false} width={metric.money ? 64 : 40} allowDecimals={false} tickFormatter={(v: number) => format(v)} />
                  <Tooltip
                    content={({ active, payload }) => {
                      const row = payload?.[0]?.payload as (typeof data)[number] | undefined;
                      if (!active || !row) return null;
                      return (
                        <div className="rounded-lg border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
                          <p className="font-medium">{row.full}</p>
                          <p>
                            {metric.label}: {format(row.value)}
                          </p>
                        </div>
                      );
                    }}
                  />
                  <Line type="monotone" dataKey="value" name={metric.label} stroke={LINE} strokeWidth={2} dot={{ r: 3 }} connectNulls={false} isAnimationActive={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </figure>
          <Table aria-label="Monthly trend">
            <TableHeader>
              <TableRow>
                <TableHead>Month</TableHead>
                {visible.map((m) => (
                  <TableHead key={m.key} className="text-right">
                    {m.label}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {[...trend].reverse().map((t) => (
                <TableRow key={t.period.label}>
                  <TableCell className="font-medium whitespace-nowrap">{t.period.label}</TableCell>
                  {visible.map((m) => (
                    <TableCell key={m.key} className="text-right tabular-nums">
                      {m.money ? formatInr(t[m.key]) : formatCount(t[m.key])}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </Panel>
  );
}
