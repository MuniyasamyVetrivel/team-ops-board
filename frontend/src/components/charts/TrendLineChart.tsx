import { useId, type ComponentProps, type ReactNode } from 'react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { AXIS_TICK, CHART_COLORS, CHART_INITIAL, GRID_STROKE } from './chart-theme';

export interface TrendSeries<T> {
  key: Extract<keyof T, string>;
  label: string;
  color?: string;
}

interface TrendLineChartProps<T extends { label: string }> {
  data: T[];
  series: TrendSeries<T>[];
  height?: number;
  formatValue?: (value: number) => string;
  valueDomain?: ComponentProps<typeof YAxis>['domain'];
  yAxisWidth?: number;
  renderTooltip: (datum: T) => ReactNode;
}

/** Smooth lines with a soft gradient fill underneath; gaps (null) stay gaps. Series colours follow the brand palette. */
export function TrendLineChart<T extends { label: string }>({ data, series, height = 240, formatValue, valueDomain, yAxisWidth = 40, renderTooltip }: TrendLineChartProps<T>) {
  const gradientPrefix = `trend-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  const colored = series.map((s, i) => ({ ...s, color: s.color ?? CHART_COLORS[i % CHART_COLORS.length] }));

  return (
    <div style={{ height }}>
      <ResponsiveContainer width="100%" height="100%" initialDimension={{ width: CHART_INITIAL.width, height }}>
        <AreaChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          <defs>
            {colored.map((s) => (
              <linearGradient key={s.key} id={`${gradientPrefix}-${s.key}`} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={s.color} stopOpacity={0.22} />
                <stop offset="100%" stopColor={s.color} stopOpacity={0} />
              </linearGradient>
            ))}
          </defs>
          <CartesianGrid vertical={false} stroke={GRID_STROKE} />
          <XAxis dataKey="label" tick={AXIS_TICK} tickLine={false} axisLine={false} />
          <YAxis tick={AXIS_TICK} tickLine={false} axisLine={false} width={yAxisWidth} allowDecimals={false} tickFormatter={formatValue} domain={valueDomain} />
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
              strokeWidth={2.5}
              fill={`url(#${gradientPrefix}-${s.key})`}
              dot={false}
              activeDot={{ r: 4, strokeWidth: 2, stroke: 'var(--card)' }}
              connectNulls={false}
              isAnimationActive={false}
            />
          ))}
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
