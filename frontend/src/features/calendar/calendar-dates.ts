import { parseLocalDate } from '@/features/tasks/task-meta';

/**
 * Calendar-date helpers for the month / week / agenda views. They work on local calendar dates (no time zone
 * maths): which day is "today" always comes from the server.
 */

export type CalendarView = 'MONTH' | 'WEEK' | 'AGENDA';

/** Days shown in the agenda view. */
export const AGENDA_DAYS = 30;

/** Date → "2026-10-08". */
export function toIsoDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function addDays(iso: string, days: number): string {
  const date = parseLocalDate(iso);
  date.setDate(date.getDate() + days);
  return toIsoDate(date);
}

/** Monday of the week containing the date. */
export function startOfWeek(iso: string): string {
  const date = parseLocalDate(iso);
  const offset = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - offset);
  return toIsoDate(date);
}

/** The visible range for a view anchored on a date: whole weeks for the month grid (Monday to Sunday). */
export function viewRange(view: CalendarView, anchor: string): { from: string; to: string } {
  if (view === 'WEEK') {
    const from = startOfWeek(anchor);
    return { from, to: addDays(from, 6) };
  }
  if (view === 'AGENDA') {
    return { from: anchor, to: addDays(anchor, AGENDA_DAYS - 1) };
  }
  const date = parseLocalDate(anchor);
  const first = toIsoDate(new Date(date.getFullYear(), date.getMonth(), 1));
  const last = toIsoDate(new Date(date.getFullYear(), date.getMonth() + 1, 0));
  return { from: startOfWeek(first), to: addDays(startOfWeek(last), 6) };
}

/** Moves the anchor one step back (-1) or forward (+1) for the view. */
export function shift(view: CalendarView, anchor: string, direction: -1 | 1): string {
  if (view === 'MONTH') {
    const date = parseLocalDate(anchor);
    return toIsoDate(new Date(date.getFullYear(), date.getMonth() + direction, 1));
  }
  return addDays(anchor, direction * (view === 'WEEK' ? 7 : AGENDA_DAYS));
}

/** Every date from {@code from} to {@code to}, inclusive. */
export function daysBetween(from: string, to: string): string[] {
  const days: string[] = [];
  for (let day = from; day <= to; day = addDays(day, 1)) {
    days.push(day);
  }
  return days;
}

/** Whether an item spanning [start, end] (inclusive) covers the day. ISO dates compare as strings. */
export function covers(start: string, end: string, day: string): boolean {
  return start <= day && day <= end;
}
