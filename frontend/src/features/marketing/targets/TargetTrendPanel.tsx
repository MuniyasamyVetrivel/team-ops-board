import { ChartColumn } from 'lucide-react';
import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';

import { TargetStatusBadge } from '../components/TargetProgress';
import { formatMetric, formatPercent, type MetricFormat } from '../marketing-format';
import { useTargetTrend, type TrendPoint, type TrendView, type TypeRef } from './api';
import { UNIT_FORMATS } from './target-meta';

const AXIS = { fontSize: 12, fill: 'var(--muted-foreground)' };
const INITIAL = { width: 640, height: 260 };
const TARGET_COLOR = 'var(--chart-6)';
const ACTUAL_COLOR = 'var(--chart-1)';

const VIEWS: { value: TrendView; label: string }[] = [
  { value: 'MONTH', label: 'Month' },
  { value: 'QUARTER', label: 'Quarter' },
  { value: 'YEAR', label: 'Year' },
];

/** Brief section 33: a type's target against its actual by month, quarter or year. */
export function TargetTrendPanel({ types, year }: { types: TypeRef[]; year: number }) {
  const [typeId, setTypeId] = useState<number | null>(null);
  const [view, setView] = useState<TrendView>('MONTH');
  const selected = typeId ?? types[0]?.id ?? null;
  const trend = useTargetTrend(selected, view, year);

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-3.5">
        <div>
          <h2 className="font-semibold">Monthly history</h2>
          <p className="text-sm text-muted-foreground">{view === 'YEAR' ? `Five years to ${year}` : `${year}`}; quarters and years add up their months (percentages are averaged).</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Select aria-label="Target type for the history" className="w-56" value={selected ?? ''} onChange={(e) => setTypeId(Number(e.target.value))}>
            {types.map((type) => (
              <option key={type.id} value={type.id}>
                {type.name}
              </option>
            ))}
          </Select>
          <Tabs value={view} onValueChange={(value) => setView(value as TrendView)}>
            <TabsList className="border-b-0 px-0" aria-label="History view">
              {VIEWS.map((v) => (
                <TabsTrigger key={v.value} value={v.value}>
                  {v.label}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        </div>
      </div>
      <div className="p-5">
        {selected === null ? (
          <EmptyState icon={ChartColumn} title="No target types yet" className="py-8" />
        ) : trend.isPending ? (
          <Skeleton className="h-64" role="status" aria-label="Loading the history" />
        ) : trend.isError ? (
          <ErrorState error={trend.error} title="Couldn't load the history" onRetry={() => void trend.refetch()} />
        ) : trend.data.points.every((p) => p.targetValue === null) ? (
          <EmptyState icon={ChartColumn} title={`No ${trend.data.type.name} targets in this period`} className="py-8" />
        ) : (
          <TrendBody points={trend.data.points} name={trend.data.type.name} format={UNIT_FORMATS[trend.data.type.unit]} />
        )}
      </div>
    </Card>
  );
}

function TrendBody({ points, name, format }: { points: TrendPoint[]; name: string; format: MetricFormat }) {
  const data = points.map((p) => ({ label: p.label, target: p.targetValue, actual: p.actual }));
  return (
    <div className="space-y-4">
      <figure aria-label={`${name}: target against actual`} className="space-y-2">
        <div className="h-64">
          <ResponsiveContainer width="100%" height="100%" initialDimension={INITIAL}>
            <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barGap={2}>
              <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={false} />
              <YAxis tick={AXIS} tickLine={false} axisLine={false} width={56} tickFormatter={(v: number) => formatMetric(v, format)} />
              <Tooltip
                cursor={{ fill: 'var(--muted)', opacity: 0.4 }}
                content={({ active, label }) => {
                  const point = points.find((p) => p.label === label);
                  if (!active || !point) return null;
                  return (
                    <div className="rounded-lg border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
                      <p className="mb-1 font-medium">{point.label}</p>
                      <p>Target: {formatMetric(point.targetValue, format)}</p>
                      <p>Actual: {point.status === null && point.targetValue !== null ? 'planned' : formatMetric(point.actual, format)}</p>
                      <p>Achievement: {formatPercent(point.achievementPct)}</p>
                    </div>
                  );
                }}
              />
              <Bar dataKey="target" name="Target" fill={TARGET_COLOR} radius={[3, 3, 0, 0]} isAnimationActive={false} />
              <Bar dataKey="actual" name="Actual" fill={ACTUAL_COLOR} radius={[3, 3, 0, 0]} isAnimationActive={false} />
            </BarChart>
          </ResponsiveContainer>
        </div>
        <figcaption>
          <ul className="flex gap-4 text-xs text-muted-foreground" aria-label="Chart legend">
            <li className="flex items-center gap-1.5">
              <span className="size-2.5 rounded-sm" style={{ background: TARGET_COLOR }} aria-hidden />
              Target
            </li>
            <li className="flex items-center gap-1.5">
              <span className="size-2.5 rounded-sm" style={{ background: ACTUAL_COLOR }} aria-hidden />
              Actual
            </li>
          </ul>
        </figcaption>
      </figure>
      <Table aria-label={`${name} by period`}>
        <TableHeader>
          <TableRow>
            <TableHead>Period</TableHead>
            <TableHead className="text-right">Target</TableHead>
            <TableHead className="text-right">Actual</TableHead>
            <TableHead className="text-right">Achievement</TableHead>
            <TableHead className="text-right">Remaining</TableHead>
            <TableHead>Status</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {points
            .filter((p) => p.targetValue !== null)
            .map((p) => (
              <TableRow key={p.label}>
                <TableCell className="font-medium">{p.label}</TableCell>
                <TableCell className="text-right tabular-nums">{formatMetric(p.targetValue, format)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatMetric(p.actual, format)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatPercent(p.achievementPct)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatMetric(p.remaining, format)}</TableCell>
                <TableCell>
                  <TargetStatusBadge status={p.status} />
                </TableCell>
              </TableRow>
            ))}
        </TableBody>
      </Table>
    </div>
  );
}
