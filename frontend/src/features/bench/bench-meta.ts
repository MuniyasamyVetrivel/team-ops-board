import {
  Armchair,
  CalendarClock,
  CalendarOff,
  CircleCheck,
  CircleDashed,
  CirclePlay,
  CircleStop,
  Hourglass,
  LogOut,
  type LucideIcon,
} from 'lucide-react';

import type { BadgeProps } from '@/components/ui/badge';
import { parseLocalDate } from '@/features/tasks/task-meta';

import type { ClientStatus, ConsultantStatus, EmploymentStatus, PeriodStatus } from './api';

type Tone = NonNullable<BadgeProps['tone']>;

interface StatusMeta {
  label: string;
  tone: Tone;
  icon: LucideIcon;
}

/** Every status is shown with its label and an icon, never by colour alone. */
export const CONSULTANT_STATUS_META: Record<ConsultantStatus, StatusMeta> = {
  ALLOCATED: { label: 'Allocated', tone: 'success', icon: CircleCheck },
  PARTIALLY_ALLOCATED: { label: 'Partially allocated', tone: 'primary', icon: CircleDashed },
  ON_BENCH: { label: 'On bench', tone: 'warning', icon: Armchair },
  LEAVE_OF_ABSENCE: { label: 'On leave of absence', tone: 'neutral', icon: CalendarOff },
  NOTICE_PERIOD: { label: 'Notice period', tone: 'neutral', icon: Hourglass },
  LEFT: { label: 'Left', tone: 'neutral', icon: LogOut },
};

export const PERIOD_STATUS_META: Record<PeriodStatus, StatusMeta> = {
  UPCOMING: { label: 'Upcoming', tone: 'primary', icon: CalendarClock },
  ACTIVE: { label: 'Active', tone: 'success', icon: CirclePlay },
  ENDED: { label: 'Ended', tone: 'neutral', icon: CircleStop },
};

export const EMPLOYMENT_STATUS_LABELS: Record<EmploymentStatus, string> = {
  ACTIVE: 'Active',
  LEAVE_OF_ABSENCE: 'On leave of absence',
  NOTICE_PERIOD: 'Notice period',
  LEFT: 'Left',
};

export const CLIENT_STATUS_LABELS: Record<ClientStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
};

/** Shown where a consultant's Employee record is in the recycle bin (the server then sends no name). */
export const EMPLOYEE_IN_BIN = 'Employee record deleted';

const longDate = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
const monthLabel = new Intl.DateTimeFormat(undefined, { month: 'short' });
const monthYearLabel = new Intl.DateTimeFormat(undefined, { month: 'short', year: 'numeric' });

/** A server LocalDate as "12 Oct 2026"; "—" without one. */
export function formatBenchDate(iso: string | null | undefined): string {
  return iso ? longDate.format(parseLocalDate(iso)) : '—';
}

/** "12 Oct 2026 – 30 Nov 2026", or "From 12 Oct 2026 (no end date)". */
export function dateRange(start: string, end: string | null): string {
  return end ? `${formatBenchDate(start)} – ${formatBenchDate(end)}` : `From ${formatBenchDate(start)} (no end date)`;
}

/** "72%", "72.5%"; "—" for null (division by zero on the server). */
export function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—';
  return `${Number.isInteger(value) ? value : Number(value.toFixed(1))}%`;
}

/** "2.50". */
export function formatFte(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : value.toFixed(2);
}

export function formatDays(days: number): string {
  return days === 1 ? '1 day' : `${days.toLocaleString()} days`;
}

/** From the server's `daysUntilEnd`, never the browser clock. */
export function rollOffLabel(daysUntilEnd: number | null): string {
  if (daysUntilEnd === null) return 'No end date';
  if (daysUntilEnd === 0) return 'Ends today';
  if (daysUntilEnd === 1) return 'Ends tomorrow';
  return `Ends in ${formatDays(daysUntilEnd)}`;
}

/** Roll-offs within 14 days get the stronger tone (the second reminder); 30 days is the first. */
export function rollOffTone(daysUntilEnd: number | null): Tone {
  if (daysUntilEnd === null) return 'neutral';
  return daysUntilEnd <= 14 ? 'danger' : 'warning';
}

export function consultantName(name: string | null | undefined): string {
  return name ?? EMPLOYEE_IN_BIN;
}

// --- allocation timeline ------------------------------------------------------------------------------------------

const DAY_MS = 86_400_000;

/** Days since the epoch for a LocalDate, in UTC so daylight saving never shifts a day. */
export function dayNumber(iso: string): number {
  const [year, month, day] = iso.split('-').map(Number);
  return Math.round(Date.UTC(year ?? 1970, (month ?? 1) - 1, day ?? 1) / DAY_MS);
}

function isoOf(day: number): string {
  return new Date(day * DAY_MS).toISOString().slice(0, 10);
}

function firstOfMonth(day: number, addMonths = 0): number {
  const date = new Date(day * DAY_MS);
  return Math.round(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + addMonths, 1) / DAY_MS);
}

export interface TimelineInput {
  id: number;
  startDate: string;
  endDate: string | null;
}

export interface TimelineBar<T> {
  item: T;
  /** Position and width as a share of the axis, 0–100. */
  left: number;
  width: number;
  /** No end date: the bar runs off the end of the axis. */
  openEnded: boolean;
}

export interface Timeline<T> {
  from: string;
  /** Exclusive: the first day after the axis. */
  to: string;
  months: { key: string; label: string; left: number }[];
  bars: TimelineBar<T>[];
  /** Where today sits, or null when it is outside the axis. */
  today: number | null;
}

/** The axis always shows at least this many months, so short allocations don't stretch across the whole width. */
const MIN_MONTHS = 6;

/**
 * Lays allocations out on a month axis: from the first of the earliest month (start dates and today) to the end of
 * the latest month (end dates and today), at least {@link MIN_MONTHS} months. Open-ended allocations run to the end.
 * `today` is the server's business date.
 */
export function buildTimeline<T extends TimelineInput>(items: T[], today: string | null): Timeline<T> {
  const todayDay = today ? dayNumber(today) : null;
  const starts = items.map((i) => dayNumber(i.startDate));
  const ends = items.flatMap((i) => (i.endDate ? [dayNumber(i.endDate)] : []));
  const anchors = [...starts, ...ends, ...(todayDay !== null ? [todayDay] : [])];
  const earliest = anchors.length ? Math.min(...anchors) : 0;
  const latest = anchors.length ? Math.max(...anchors) : 0;

  const from = firstOfMonth(earliest);
  let to = firstOfMonth(latest, 1);
  if (to < firstOfMonth(from, MIN_MONTHS)) to = firstOfMonth(from, MIN_MONTHS);
  const span = to - from;
  const share = (day: number) => ((day - from) / span) * 100;

  const months: Timeline<T>['months'] = [];
  for (let month = from; month < to; month = firstOfMonth(month, 1)) {
    const date = new Date(month * DAY_MS);
    const label = date.getUTCMonth() === 0 || month === from ? monthYearLabel : monthLabel;
    months.push({ key: isoOf(month), label: label.format(new Date(date.getUTCFullYear(), date.getUTCMonth(), 1)), left: share(month) });
  }

  const bars = items.map((item) => {
    const start = dayNumber(item.startDate);
    // The end date is included, so the bar reaches the end of that day.
    const end = item.endDate ? dayNumber(item.endDate) + 1 : to;
    return { item, left: share(start), width: Math.max(share(end) - share(start), 0.5), openEnded: item.endDate === null };
  });

  return {
    from: isoOf(from),
    to: isoOf(to),
    months,
    bars,
    today: todayDay !== null && todayDay >= from && todayDay < to ? share(todayDay) : null,
  };
}
