/**
 * The one chart palette, built from the brand colours (tokens in index.css, with dark-mode values). Series take
 * colours in this order; status charts use the --status-* tokens instead, so a status never borrows a series colour.
 */
export const CHART_COLORS = ['var(--chart-1)', 'var(--chart-2)', 'var(--chart-3)', 'var(--chart-4)', 'var(--chart-5)', 'var(--chart-6)'] as const;

/** Brand amber: the current period in a bar chart. */
export const CHART_HIGHLIGHT = 'var(--chart-highlight)';

export const AXIS_TICK = { fontSize: 12, fill: 'var(--chart-axis)' } as const;
export const GRID_STROKE = 'var(--chart-grid)';
export const CURSOR_FILL = 'var(--muted)';

/** Size before (and without) a measured container, e.g. in tests, so charts still render. */
export const CHART_INITIAL = { width: 480, height: 240 } as const;

export function percentOf(value: number, total: number): number {
  return total > 0 ? Math.round((value / total) * 100) : 0;
}
