import { ChartLine } from 'lucide-react';
import { useState } from 'react';

import { ChartTooltip, ChartTooltipRow } from '@/components/charts/ChartTooltip';
import { TrendLineChart } from '@/components/charts/TrendLineChart';
import { Panel } from '@/components/common/Panel';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

import { formatCount, formatInr, MONTH_NAMES } from '../marketing-format';
import type { DashboardTrendMonth } from './api';


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
        <p className="px-6 py-4 text-sm text-muted-foreground">No marketing modules to chart for your access.</p>
      ) : (
        <div className="space-y-4 p-6">
          <figure aria-label={`${metric.label} by month`}>
            <TrendLineChart
              data={data}
              series={[{ key: 'value', label: metric.label }]}
              yAxisWidth={metric.money ? 64 : 40}
              formatValue={(v) => format(v)}
              renderTooltip={(row) => (
                <ChartTooltip title={row.full}>
                  <ChartTooltipRow color="var(--chart-1)" label={metric.label} value={format(row.value)} />
                </ChartTooltip>
              )}
            />
          </figure>
          <Table aria-label="Monthly trend">
            <TableHeader>
              <TableRow>
                <TableHead>Month</TableHead>
                {visible.map((m) => (
                  <TableHead key={m.key} numeric>
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
                    <TableCell key={m.key} numeric>
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
