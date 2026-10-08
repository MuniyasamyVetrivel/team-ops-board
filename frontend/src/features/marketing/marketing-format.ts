/** Number formats for marketing figures. Every formatter shows "—" for null (no data, or a division by zero). */

export const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
] as const;

export type MetricFormat = 'count' | 'percent' | 'currency';

const countFormat = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 });
const percentFormat = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
const inrFormat = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2, minimumFractionDigits: 0 });

/** "October 2026". */
export function periodLabel(month: number, year: number): string {
  return `${MONTH_NAMES[month - 1] ?? '?'} ${year}`;
}

export function previousPeriod(month: number, year: number): { month: number; year: number } {
  return month === 1 ? { month: 12, year: year - 1 } : { month: month - 1, year };
}

/** "24,000" (Indian digit grouping, e.g. "1,25,000"). */
export function formatCount(value: number | null | undefined): string {
  return value == null ? '—' : countFormat.format(value);
}

/** "35.42%". */
export function formatPercent(value: number | null | undefined): string {
  return value == null ? '—' : `${percentFormat.format(value)}%`;
}

/** "₹42,000" or "₹500.50". */
export function formatInr(value: number | null | undefined): string {
  return value == null ? '—' : inrFormat.format(value);
}

export function formatMetric(value: number | null | undefined, format: MetricFormat): string {
  if (format === 'percent') return formatPercent(value);
  if (format === 'currency') return formatInr(value);
  return formatCount(value);
}

/**
 * Relative change for display ("+12.5" means 12.5% up). null when either side is missing or the previous value is
 * zero, so the UI never divides by zero.
 */
export function percentChange(current: number | null | undefined, previous: number | null | undefined): number | null {
  if (current == null || previous == null || previous === 0) return null;
  return Math.round(((current - previous) / Math.abs(previous)) * 1000) / 10;
}
