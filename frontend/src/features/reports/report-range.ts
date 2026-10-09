/** Date-range presets of the management reports. "Today" is the server's business date (the report's own range end). */
export type RangePreset = 'LAST_30' | 'THIS_MONTH' | 'LAST_MONTH' | 'LAST_90' | 'CUSTOM';

export interface DateRange {
  from: string;
  to: string;
}

export const PRESET_LABELS: Record<RangePreset, string> = {
  LAST_30: 'Last 30 days',
  THIS_MONTH: 'This month',
  LAST_MONTH: 'Last month',
  LAST_90: 'Last 90 days',
  CUSTOM: 'Custom range',
};

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** ISO date arithmetic in UTC, so no browser time zone can shift a day. */
function toUtc(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y!, m! - 1, d!));
}

function toIso(date: Date): string {
  return date.toISOString().slice(0, 10);
}

export function addDays(iso: string, days: number): string {
  const date = toUtc(iso);
  date.setUTCDate(date.getUTCDate() + days);
  return toIso(date);
}

function lastDayOfMonth(year: number, monthIndex: number): number {
  return new Date(Date.UTC(year, monthIndex + 1, 0)).getUTCDate();
}

export function rangeFor(preset: Exclude<RangePreset, 'CUSTOM'>, today: string): DateRange {
  const t = toUtc(today);
  switch (preset) {
    case 'LAST_30':
      return { from: addDays(today, -29), to: today };
    case 'LAST_90':
      return { from: addDays(today, -89), to: today };
    case 'THIS_MONTH':
      return { from: toIso(new Date(Date.UTC(t.getUTCFullYear(), t.getUTCMonth(), 1))), to: today };
    case 'LAST_MONTH': {
      const first = new Date(Date.UTC(t.getUTCFullYear(), t.getUTCMonth() - 1, 1));
      return { from: toIso(first), to: toIso(new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth(), lastDayOfMonth(first.getUTCFullYear(), first.getUTCMonth())))) };
    }
  }
}

/**
 * The period to compare with. A range starting on the 1st compares with the same days of the month before (all of
 * September for "last month" in October; 1–9 September for "this month" on 9 October); any other range with the
 * same number of days just before it.
 */
export function previousRange(range: DateRange): DateRange {
  const from = toUtc(range.from);
  const to = toUtc(range.to);
  const sameMonth = from.getUTCFullYear() === to.getUTCFullYear() && from.getUTCMonth() === to.getUTCMonth();
  if (from.getUTCDate() === 1 && sameMonth) {
    const year = from.getUTCFullYear();
    const month = from.getUTCMonth() - 1;
    const last = lastDayOfMonth(year, month);
    const wholeMonth = to.getUTCDate() === lastDayOfMonth(to.getUTCFullYear(), to.getUTCMonth());
    const endDay = wholeMonth ? last : Math.min(to.getUTCDate(), last);
    return { from: toIso(new Date(Date.UTC(year, month, 1))), to: toIso(new Date(Date.UTC(year, month, endDay))) };
  }
  const days = Math.round((to.getTime() - from.getTime()) / 86_400_000) + 1;
  return { from: addDays(range.from, -days), to: addDays(range.from, -1) };
}

/** "1 Sep – 30 Sep 2026", or "September 2026" for a whole calendar month. */
export function rangeLabel(range: DateRange): string {
  const from = toUtc(range.from);
  const to = toUtc(range.to);
  const whole = from.getUTCDate() === 1 && from.getUTCMonth() === to.getUTCMonth() && from.getUTCFullYear() === to.getUTCFullYear()
    && to.getUTCDate() === lastDayOfMonth(to.getUTCFullYear(), to.getUTCMonth());
  const long = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  if (whole) return `${long[from.getUTCMonth()]} ${from.getUTCFullYear()}`;
  const day = (d: Date) => `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}`;
  return `${day(from)} – ${day(to)} ${to.getUTCFullYear()}`;
}
