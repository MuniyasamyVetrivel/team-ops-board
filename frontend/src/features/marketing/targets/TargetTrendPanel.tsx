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
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';

import { TargetStatusBadge } from '../components/TargetProgress';
import { formatMetric, formatPercent, type MetricFormat } from '../marketing-format';
import { useTargetTrend, type TrendPoint, type TrendView, type TypeRef } from './api';
import { UNIT_FORMATS } from './target-meta';

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
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <div>
          <h2 className="text-card-title font-semibold">Monthly history</h2>
          <p className="mt-0.5 text-label text-muted-foreground">{view === 'YEAR' ? `Five years to ${year}` : `${year}`}; quarters and years add up their months (percentages are averaged).</p>
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
      <div className="p-6">
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
  const data = points.map((p) => ({ label: p.label, target: p.targetValue, actual: p.actual, point: p }));
  return (
    <div className="space-y-4">
      <figure aria-label={`${name}: target against actual`} className="space-y-2">
        <ComboChart
          data={data}
          series={[
            { key: 'target', label: 'Target', color: TARGET_COLOR },
            { key: 'actual', label: 'Actual', color: ACTUAL_COLOR },
          ]}
          leftWidth={56}
          formatLeft={(v) => formatMetric(v, format)}
          renderTooltip={(row) => (
            <ChartTooltip title={row.point.label}>
              <ChartTooltipRow color={TARGET_COLOR} label="Target" value={formatMetric(row.point.targetValue, format)} />
              <ChartTooltipRow
                color={ACTUAL_COLOR}
                label="Actual"
                value={row.point.status === null && row.point.targetValue !== null ? 'planned' : formatMetric(row.point.actual, format)}
              />
              <ChartTooltipRow label="Achievement" value={formatPercent(row.point.achievementPct)} />
            </ChartTooltip>
          )}
        />
      </figure>
      <Table aria-label={`${name} by period`}>
        <TableHeader>
          <TableRow>
            <TableHead>Period</TableHead>
            <TableHead numeric>Target</TableHead>
            <TableHead numeric>Actual</TableHead>
            <TableHead numeric>Achievement</TableHead>
            <TableHead numeric>Remaining</TableHead>
            <TableHead>Status</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {points
            .filter((p) => p.targetValue !== null)
            .map((p) => (
              <TableRow key={p.label}>
                <TableCell className="font-medium whitespace-nowrap">{p.label}</TableCell>
                <TableCell numeric>{formatMetric(p.targetValue, format)}</TableCell>
                <TableCell numeric>{formatMetric(p.actual, format)}</TableCell>
                <TableCell numeric>{formatPercent(p.achievementPct)}</TableCell>
                <TableCell numeric>{formatMetric(p.remaining, format)}</TableCell>
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
