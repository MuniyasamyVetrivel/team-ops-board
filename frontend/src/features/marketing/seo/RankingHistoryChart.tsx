import { TrendLineChart } from '@/components/charts/TrendLineChart';
import { ChartTooltip, ChartTooltipRow } from '@/components/charts/ChartTooltip';

import type { MarketingPeriod } from '../api';
import { MONTH_NAMES } from '../marketing-format';
import type { HistoryPoint } from './api';
import { MAX_SERIES, SERIES_COLORS } from './seo-meta';




export interface ChartSeries {
  key: string;
  name: string;
  points: HistoryPoint[];
}

interface RankingHistoryChartProps {
  periods: MarketingPeriod[];
  series: ChartSeries[];
  /** Mean ranked position per month, drawn dashed when given. */
  averages?: (number | null)[];
  /** The keyword's target position, drawn as a reference line. */
  target?: number | null;
  label: string;
}

const shortMonth = (period: MarketingPeriod) => `${(MONTH_NAMES[period.month - 1] ?? '').slice(0, 3)} ${String(period.year).slice(2)}`;

function describe(point: HistoryPoint | undefined): string {
  if (!point?.recorded) return 'No data';
  return point.position === null ? 'NR' : `#${point.position}`;
}

/**
 * Monthly positions as lines, with position 1 at the top. Not Ranked and unrecorded months leave a gap; the
 * tooltip spells them out ("NR", "No data"), and every line has a named legend entry, so colour is never the only cue.
 */
export function RankingHistoryChart({ periods, series, averages, target, label }: RankingHistoryChartProps) {
  const shown = series.slice(0, MAX_SERIES);
  const data = periods.map((period, index) => {
    const row: Record<string, number | string | null> & { label: string; fullLabel: string } = { label: shortMonth(period), fullLabel: period.label };
    shown.forEach((s) => {
      row[s.key] = s.points[index]?.position ?? null;
    });
    if (averages) row.average = averages[index] ?? null;
    return row;
  });

  return (
    <figure aria-label={label} className="space-y-2">
      <TrendLineChart
        data={data}
        series={[
          ...shown.map((s, i) => ({ key: s.key, label: s.name, color: SERIES_COLORS[i] })),
          ...(averages ? [{ key: 'average', label: 'Average position', color: 'var(--status-neutral)', dashed: true }] : []),
        ]}
        height={256}
        reversed
        valueDomain={[1, 'dataMax']}
        referenceLine={target != null ? { value: target, label: `Target #${target}` } : undefined}
        legend
        renderTooltip={(row) => {
          const index = data.indexOf(row);
          return (
            <ChartTooltip title={row.fullLabel}>
              {shown.map((s, i) => (
                <ChartTooltipRow key={s.key} color={SERIES_COLORS[i]} label={s.name} value={describe(s.points[index])} />
              ))}
              {averages && <ChartTooltipRow label="Average position" value={averages[index] == null ? '—' : averages[index]?.toFixed(1)} />}
            </ChartTooltip>
          );
        }}
      />
      <figcaption className="text-xs text-muted-foreground">Position 1 is at the top. Gaps are months with no position (not ranked or not recorded).</figcaption>
    </figure>
  );
}
