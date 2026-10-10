import type { ComponentProps, ReactNode } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { AXIS_TICK, CHART_COLORS, CHART_HIGHLIGHT, CHART_INITIAL, CURSOR_FILL, GRID_STROKE } from './chart-theme';

export interface BarDatum {
  label: string;
  value: number | null;
  /** Draws this bar in brand amber, e.g. the current week. */
  highlight?: boolean;
}

interface BarGraphProps<T extends BarDatum> {
  data: T[];
  /** "columns" (default): vertical bars over a category axis. "rows": horizontal bars, one per category. */
  layout?: 'columns' | 'rows';
  /** Chart height; rows default to one 36px band per bar. */
  height?: number;
  color?: string;
  formatValue?: (value: number) => string;
  valueDomain?: ComponentProps<typeof XAxis>['domain'];
  /** Dashed marker across the value axis, e.g. 100% capacity. */
  referenceLine?: { value: number; label: string };
  /** Width of the category labels in the "rows" layout. */
  categoryWidth?: number;
  renderTooltip: (datum: T) => ReactNode;
}

/** Brand-blue bars with rounded ends, the highlighted bar in amber, light gridlines and muted axis labels. */
export function BarGraph<T extends BarDatum>({
  data,
  layout = 'columns',
  height,
  color = CHART_COLORS[0],
  formatValue,
  valueDomain,
  referenceLine,
  categoryWidth = 120,
  renderTooltip,
}: BarGraphProps<T>) {
  const rows = layout === 'rows';
  const chartHeight = height ?? (rows ? Math.max(160, data.length * 36 + 32) : 240);
  const valueAxis = { tick: AXIS_TICK, tickLine: false, axisLine: false, allowDecimals: false, tickFormatter: formatValue, domain: valueDomain } as const;
  const categoryAxis = { dataKey: 'label', tick: AXIS_TICK, tickLine: false, axisLine: false } as const;

  return (
    <div style={{ height: chartHeight }}>
      <ResponsiveContainer width="100%" height="100%" initialDimension={{ width: CHART_INITIAL.width, height: chartHeight }}>
        <BarChart data={data} layout={rows ? 'vertical' : 'horizontal'} margin={rows ? { top: 16, right: 24, bottom: 0, left: 0 } : { top: 8, right: 8, bottom: 0, left: -12 }}>
          <CartesianGrid vertical={rows} horizontal={!rows} stroke={GRID_STROKE} />
          {rows ? (
            <>
              <XAxis type="number" {...valueAxis} />
              <YAxis type="category" {...categoryAxis} width={categoryWidth} />
            </>
          ) : (
            <>
              <XAxis {...categoryAxis} />
              <YAxis {...valueAxis} />
            </>
          )}
          {referenceLine &&
            (rows ? (
              <ReferenceLine x={referenceLine.value} stroke="var(--status-danger)" strokeDasharray="4 4" label={{ value: referenceLine.label, position: 'top', fontSize: 11, fill: 'var(--chart-axis)' }} />
            ) : (
              <ReferenceLine y={referenceLine.value} stroke="var(--status-danger)" strokeDasharray="4 4" label={{ value: referenceLine.label, position: 'insideTopRight', fontSize: 11, fill: 'var(--chart-axis)' }} />
            ))}
          <Tooltip
            cursor={{ fill: CURSOR_FILL, opacity: 0.6 }}
            content={({ active, payload }) => {
              const datum = payload?.[0]?.payload as T | undefined;
              return active && datum ? renderTooltip(datum) : null;
            }}
          />
          <Bar dataKey="value" fill={color} radius={rows ? [0, 6, 6, 0] : [6, 6, 0, 0]} maxBarSize={rows ? 18 : 40} isAnimationActive={false}>
            {data.map((datum) => (
              <Cell key={datum.label} fill={datum.highlight ? CHART_HIGHLIGHT : color} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
