import type { ReactNode } from 'react';
import { Bar, CartesianGrid, Cell, ComposedChart, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { AXIS_TICK, CHART_COLORS, CHART_HIGHLIGHT, CHART_INITIAL, CURSOR_FILL, GRID_STROKE } from './chart-theme';
import { ChartLegend, type LegendItem } from './ChartLegend';
import { ChartTooltip, ChartTooltipRow } from './ChartTooltip';

export interface ComboSeries<T> {
  key: Extract<keyof T, string>;
  label: string;
  color?: string;
  /** Bars (default) or a smooth line. */
  type?: 'bar' | 'line';
  /** Lines can read off a second axis on the right, e.g. rates next to counts. */
  axis?: 'left' | 'right';
  dashed?: boolean;
  /** Formats this series' values in the default tooltip. */
  format?: (value: number) => string;
}

interface ComboChartProps<T extends { label: string }> {
  data: T[];
  series: ComboSeries<T>[];
  /** Stacks the bar series into one column per category. */
  stacked?: boolean;
  height?: number;
  formatLeft?: (value: number) => string;
  formatRight?: (value: number) => string;
  leftWidth?: number;
  rightWidth?: number;
  /** Shows every category label even when they crowd. */
  allTicks?: boolean;
  /** The bar series drawn in amber for data marked {@code highlight: true}, e.g. the selected month. */
  highlightKey?: Extract<keyof T, string>;
  /** Names the amber bar in the legend, e.g. "Selected month". */
  highlightLabel?: string;
  /** Tooltip title for a datum; defaults to its {@code full} field or label. */
  tooltipTitle?: (datum: T) => ReactNode;
  /** Replaces the default tooltip (title + one row per series). */
  renderTooltip?: (datum: T) => ReactNode;
  /** Hide the legend when the panel already names the series. */
  legend?: boolean;
}

const fullLabel = (datum: { label: string }) => ('full' in datum && typeof datum.full === 'string' ? datum.full : datum.label);

/**
 * Bars (grouped or stacked) with optional lines, in the brand palette: rounded bar tops, light gridlines, muted axis
 * labels and a worded legend. Covers every multi-series chart (target vs actual, month vs month, spend vs leads).
 */
export function ComboChart<T extends { label: string; highlight?: boolean }>({
  data,
  series,
  stacked = false,
  height = 256,
  formatLeft,
  formatRight,
  leftWidth = 40,
  rightWidth = 44,
  allTicks = false,
  highlightKey,
  highlightLabel = 'Selected period',
  tooltipTitle = fullLabel,
  renderTooltip,
  legend = true,
}: ComboChartProps<T>) {
  const colored = series.map((s, i) => ({ ...s, type: s.type ?? 'bar', color: s.color ?? CHART_COLORS[i % CHART_COLORS.length] }));
  const bars = colored.filter((s) => s.type === 'bar');
  const hasRight = colored.some((s) => s.axis === 'right');
  const legendItems: LegendItem[] = colored.map((s) => ({ label: s.label, color: s.color, shape: s.type === 'line' ? (s.dashed ? 'dashed' : 'line') : 'dot' }));
  if (highlightKey && data.some((d) => d.highlight)) legendItems.push({ label: highlightLabel, color: CHART_HIGHLIGHT });

  return (
    <div className="space-y-3">
      <div style={{ height }}>
        <ResponsiveContainer width="100%" height="100%" initialDimension={{ width: CHART_INITIAL.width, height }}>
          <ComposedChart data={data} margin={{ top: 8, right: hasRight ? 0 : 8, bottom: 0, left: 0 }} barGap={4} barCategoryGap="24%">
            <CartesianGrid vertical={false} stroke={GRID_STROKE} />
            <XAxis dataKey="label" tick={AXIS_TICK} tickLine={false} axisLine={false} interval={allTicks ? 0 : 'preserveEnd'} />
            <YAxis yAxisId="left" tick={AXIS_TICK} tickLine={false} axisLine={false} width={leftWidth} allowDecimals={false} tickFormatter={formatLeft} />
            {hasRight && <YAxis yAxisId="right" orientation="right" tick={AXIS_TICK} tickLine={false} axisLine={false} width={rightWidth} tickFormatter={formatRight} />}
            <Tooltip
              cursor={{ fill: CURSOR_FILL, opacity: 0.6 }}
              content={({ active, payload }) => {
                const datum = payload?.[0]?.payload as T | undefined;
                if (!active || !datum) return null;
                if (renderTooltip) return renderTooltip(datum);
                return (
                  <ChartTooltip title={tooltipTitle(datum)}>
                    {colored.map((s) => {
                      const value = datum[s.key] as unknown;
                      const shown = typeof value === 'number' ? (s.format ?? ((v: number) => v.toLocaleString()))(value) : '—';
                      return <ChartTooltipRow key={s.key} color={s.color} label={s.label} value={shown} />;
                    })}
                  </ChartTooltip>
                );
              }}
            />
            {colored.map((s) => {
              if (s.type === 'line') {
                return (
                  <Line
                    key={s.key}
                    yAxisId={s.axis ?? 'left'}
                    type="monotone"
                    dataKey={s.key}
                    name={s.label}
                    stroke={s.color}
                    strokeWidth={2.5}
                    strokeDasharray={s.dashed ? '6 4' : undefined}
                    dot={false}
                    activeDot={{ r: 4, strokeWidth: 2, stroke: 'var(--card)' }}
                    connectNulls={false}
                    isAnimationActive={false}
                  />
                );
              }
              const top = !stacked || s === bars[bars.length - 1];
              return (
                <Bar
                  key={s.key}
                  yAxisId={s.axis ?? 'left'}
                  dataKey={s.key}
                  name={s.label}
                  fill={s.color}
                  stackId={stacked ? 'stack' : undefined}
                  radius={top ? [6, 6, 0, 0] : 0}
                  maxBarSize={stacked ? 40 : 28}
                  isAnimationActive={false}
                >
                  {highlightKey === s.key && data.map((datum) => <Cell key={datum.label} fill={datum.highlight ? CHART_HIGHLIGHT : s.color} />)}
                </Bar>
              );
            })}
          </ComposedChart>
        </ResponsiveContainer>
      </div>
      {legend && <ChartLegend items={legendItems} />}
    </div>
  );
}
