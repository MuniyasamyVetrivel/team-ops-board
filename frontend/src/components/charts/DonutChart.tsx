import type { ReactNode } from 'react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';

import { cn } from '@/lib/utils';

import { percentOf } from './chart-theme';
import { ChartTooltip, ChartTooltipRow } from './ChartTooltip';

export interface DonutSegment {
  key: string;
  label: string;
  value: number;
  color: string;
}

interface DonutChartProps {
  segments: DonutSegment[];
  /** Names the legend list for screen readers, e.g. "Tasks by status". */
  ariaLabel: string;
  /** Small text under the total in the centre, e.g. "tasks". */
  centerLabel: string;
  /** Replaces the total in the centre. */
  centerValue?: ReactNode;
  formatValue?: (value: number) => string;
  className?: string;
}

const SIZE = 184;

/**
 * Thick ring with rounded segment ends and a small gap between segments, the total in the centre, and one aligned
 * legend: dot, label, count, share. The legend sits beside the ring when the card is wide enough, below it otherwise.
 */
export function DonutChart({ segments, ariaLabel, centerLabel, centerValue, formatValue = (v) => v.toLocaleString(), className }: DonutChartProps) {
  const total = segments.reduce((sum, s) => sum + s.value, 0);
  const visible = segments.filter((s) => s.value > 0);
  const empty = visible.length === 0;
  const data = empty ? [{ key: 'empty', label: '', value: 1, color: 'var(--muted)' }] : visible;

  return (
    <div className={cn('@container', className)}>
      <div className="flex flex-col items-center gap-6 @sm:flex-row @sm:gap-8">
        <div className="relative shrink-0" style={{ width: SIZE, height: SIZE }}>
          <ResponsiveContainer width="100%" height="100%" initialDimension={{ width: SIZE, height: SIZE }}>
            <PieChart>
              <Pie
                data={data}
                dataKey="value"
                nameKey="label"
                innerRadius="66%"
                outerRadius="100%"
                startAngle={90}
                endAngle={-270}
                cornerRadius={data.length > 1 ? 6 : 0}
                paddingAngle={data.length > 1 ? 3 : 0}
                stroke="none"
                isAnimationActive={false}
              >
                {data.map((s) => (
                  <Cell key={s.key} fill={s.color} />
                ))}
              </Pie>
              {!empty && (
                <Tooltip
                  content={({ active, payload }) => {
                    const s = payload?.[0]?.payload as DonutSegment | undefined;
                    if (!active || !s) return null;
                    return (
                      <ChartTooltip title={s.label}>
                        <ChartTooltipRow color={s.color} label="Count" value={`${formatValue(s.value)} · ${percentOf(s.value, total)}%`} />
                      </ChartTooltip>
                    );
                  }}
                />
              )}
            </PieChart>
          </ResponsiveContainer>
          <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
            <span className="font-display text-3xl font-bold tracking-tight tabular-nums">{centerValue ?? formatValue(total)}</span>
            <span className="text-xs text-muted-foreground">{centerLabel}</span>
          </div>
        </div>

        <ul className="w-full min-w-0 flex-1 space-y-1" aria-label={ariaLabel}>
          {segments.map((s) => (
            <li key={s.key} className="flex items-center gap-3 rounded-md py-1.5 text-sm">
              <span className="size-2.5 shrink-0 rounded-full" style={{ background: s.color }} aria-hidden />
              <span className="min-w-0 flex-1">{s.label}</span>
              <span className="w-10 text-right font-semibold tabular-nums">{formatValue(s.value)}</span>
              <span className="w-10 text-right text-muted-foreground tabular-nums">{percentOf(s.value, total)}%</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
