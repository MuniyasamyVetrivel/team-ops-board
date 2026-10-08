import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import type { MarketingPeriod } from '../api';
import { MONTH_NAMES } from '../marketing-format';
import type { HistoryPoint } from './api';
import { MAX_SERIES, SERIES_COLORS } from './seo-meta';


const AXIS = { fontSize: 12, fill: 'var(--muted-foreground)' };

/** Initial size so the chart renders before (and without) a measured container, e.g. in tests. */
const INITIAL = { width: 640, height: 260 };

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
    const row: Record<string, number | string | null> = { label: shortMonth(period), fullLabel: period.label };
    shown.forEach((s) => {
      row[s.key] = s.points[index]?.position ?? null;
    });
    if (averages) row.average = averages[index] ?? null;
    return row;
  });

  return (
    <figure aria-label={label} className="space-y-3">
      <div className="h-64">
        <ResponsiveContainer width="100%" height="100%" initialDimension={INITIAL}>
          <LineChart data={data} margin={{ top: 8, right: 16, bottom: 0, left: -8 }}>
            <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={false} />
            <YAxis reversed domain={[1, 'dataMax']} allowDecimals={false} tick={AXIS} tickLine={false} axisLine={false} width={40} />
            {target != null && <ReferenceLine y={target} stroke="var(--status-success)" strokeDasharray="4 4" label={{ value: `Target #${target}`, position: 'insideTopRight', ...AXIS }} />}
            <Tooltip
              content={({ active, payload }) => {
                const row = payload?.[0]?.payload as { fullLabel?: string } | undefined;
                if (!active || !row) return null;
                const index = data.findIndex((d) => d.fullLabel === row.fullLabel);
                return (
                  <div className="rounded-lg border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
                    <p className="mb-1 font-medium">{row.fullLabel}</p>
                    <ul className="space-y-0.5">
                      {shown.map((s, i) => (
                        <li key={s.key} className="flex items-center justify-between gap-4">
                          <span className="flex items-center gap-1.5">
                            <span className="h-0.5 w-3 rounded" style={{ background: SERIES_COLORS[i] }} aria-hidden />
                            {s.name}
                          </span>
                          <span className="font-medium tabular-nums">{describe(s.points[index])}</span>
                        </li>
                      ))}
                      {averages && (
                        <li className="flex items-center justify-between gap-4 text-muted-foreground">
                          <span>Average position</span>
                          <span className="tabular-nums">{averages[index] == null ? '—' : averages[index]?.toFixed(1)}</span>
                        </li>
                      )}
                    </ul>
                  </div>
                );
              }}
            />
            {shown.map((s, i) => (
              <Line key={s.key} type="monotone" dataKey={s.key} name={s.name} stroke={SERIES_COLORS[i]} strokeWidth={2} dot={{ r: 3 }} connectNulls={false} isAnimationActive={false} />
            ))}
            {averages && <Line type="monotone" dataKey="average" name="Average position" stroke="var(--status-neutral)" strokeDasharray="5 4" strokeWidth={1.5} dot={false} connectNulls={false} isAnimationActive={false} />}
          </LineChart>
        </ResponsiveContainer>
      </div>
      <figcaption>
        <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground" aria-label="Chart legend">
          {shown.map((s, i) => (
            <li key={s.key} className="flex items-center gap-1.5">
              <span className="h-0.5 w-4 rounded" style={{ background: SERIES_COLORS[i] }} aria-hidden />
              {s.name}
            </li>
          ))}
          {averages && (
            <li className="flex items-center gap-1.5">
              <span className="w-4 border-t border-dashed border-status-neutral" aria-hidden />
              Average position
            </li>
          )}
        </ul>
        <p className="mt-1 text-xs text-muted-foreground">Position 1 is at the top. Gaps are months with no position (not ranked or not recorded).</p>
      </figcaption>
    </figure>
  );
}
