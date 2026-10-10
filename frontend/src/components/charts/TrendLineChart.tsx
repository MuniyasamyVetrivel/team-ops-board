import { useId, type ComponentProps, type ReactNode } from 'react';
import { Area, AreaChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { AXIS_TICK, CHART_COLORS, CHART_INITIAL, GRID_STROKE } from './chart-theme';
import { ChartLegend } from './ChartLegend';

export interface TrendSeries<T> {
  key: Extract<keyof T, string>;
  label: string;
  color?: string;
  /** A dashed line without fill, e.g. an average. */
  dashed?: boolean;
}

interface TrendLineChartProps<T extends { label: string }> {
  data: T[];
  series: TrendSeries<T>[];
  height?: number;
  formatValue?: (value: number) => string;
  valueDomain?: ComponentProps<typeof YAxis>['domain'];
  yAxisWidth?: number;
  /** Puts the lowest value at the top, e.g. search positions where #1 is best. */
  reversed?: boolean;
  /** Dashed marker across the value axis, e.g. a target position. */
  referenceLine?: { value: number; label: string; color?: string };
  /** Shows a worded legend under the chart (default when there is more than one series). */
  legend?: boolean;
  renderTooltip: (datum: T) => ReactNode;
}

/**
 * Smooth lines with a soft gradient fill underneath (one series), or clean lines (several); gaps (null) stay gaps.
 * Series colours follow the brand palette.
 */
export function TrendLineChart<T extends { label: string }>({
  data,
  series,
  height = 240,
  formatValue,
  valueDomain,
  yAxisWidth = 40,
  reversed = false,
  referenceLine,
  legend,
  renderTooltip,
}: TrendLineChartProps<T>) {
  const gradientPrefix = `trend-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  const colored = series.map((s, i) => ({ ...s, color: s.color ?? CHART_COLORS[i % CHART_COLORS.length] }));
  // A gradient under several crossing lines turns to mud; only a single solid series gets the fill.
  const filled = colored.filter((s) => !s.dashed).length === 1;
  const showLegend = legend ?? colored.length > 1;

  return (
    <div className="space-y-3">
      <div style={{ height }}>
        <ResponsiveContainer width="100%" height="100%" initialDimension={{ width: CHART_INITIAL.width, height }}>
          <AreaChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
            <defs>
              {colored.map((s) => (
                <linearGradient key={s.key} id={`${gradientPrefix}-${s.key}`} x1="0" y1={reversed ? '1' : '0'} x2="0" y2={reversed ? '0' : '1'}>
                  <stop offset="0%" stopColor={s.color} stopOpacity={0.22} />
                  <stop offset="100%" stopColor={s.color} stopOpacity={0} />
                </linearGradient>
              ))}
            </defs>
            <CartesianGrid vertical={false} stroke={GRID_STROKE} />
            <XAxis dataKey="label" tick={AXIS_TICK} tickLine={false} axisLine={false} />
            <YAxis tick={AXIS_TICK} tickLine={false} axisLine={false} width={yAxisWidth} allowDecimals={false} tickFormatter={formatValue} domain={valueDomain} reversed={reversed} />
            {referenceLine && (
              <ReferenceLine
                y={referenceLine.value}
                stroke={referenceLine.color ?? 'var(--status-success)'}
                strokeDasharray="4 4"
                label={{ value: referenceLine.label, position: 'insideTopRight', ...AXIS_TICK }}
              />
            )}
            <Tooltip
              cursor={{ stroke: 'var(--border)', strokeWidth: 1 }}
              content={({ active, payload }) => {
                const datum = payload?.[0]?.payload as T | undefined;
                return active && datum ? renderTooltip(datum) : null;
              }}
            />
            {colored.map((s) => (
              <Area
                key={s.key}
                type="monotone"
                dataKey={s.key}
                name={s.label}
                stroke={s.color}
                strokeWidth={s.dashed ? 1.5 : 2.5}
                strokeDasharray={s.dashed ? '6 4' : undefined}
                fill={filled && !s.dashed ? `url(#${gradientPrefix}-${s.key})` : 'none'}
                baseValue={reversed ? 'dataMax' : undefined}
                dot={false}
                activeDot={s.dashed ? false : { r: 4, strokeWidth: 2, stroke: 'var(--card)' }}
                connectNulls={false}
                isAnimationActive={false}
              />
            ))}
          </AreaChart>
        </ResponsiveContainer>
      </div>
      {showLegend && <ChartLegend items={colored.map((s) => ({ label: s.label, color: s.color, shape: s.dashed ? 'dashed' : 'line' }))} />}
    </div>
  );
}
