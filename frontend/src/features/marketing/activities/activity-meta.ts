import type { Frequency, OccurrenceStatus } from './api';

export const FREQUENCY_LABELS: Record<Frequency, string> = {
  DAILY: 'Daily',
  WEEKLY: 'Weekly',
  MONTHLY: 'Monthly',
  QUARTERLY: 'Quarterly',
  YEARLY: 'Yearly',
};

/** What one period is, for the due-offset hint. */
export const PERIOD_NAMES: Record<Frequency, string> = {
  DAILY: 'day',
  WEEKLY: 'week (Monday to Sunday)',
  MONTHLY: 'month',
  QUARTERLY: 'quarter',
  YEARLY: 'year',
};

export const OCCURRENCE_STATUS_LABELS: Record<OccurrenceStatus, string> = {
  PENDING: 'Pending',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  SKIPPED: 'Skipped',
};

/** Placeholders the backend fills in task titles (Recurrence.title). */
export const TITLE_PLACEHOLDERS = ['{month}', '{year}', '{quarter}', '{week}', '{date}', '{period}'] as const;

const dateTime = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

/** "8 Oct 2026" for an instant, or "—". */
export function formatInstantDate(value: string | null): string {
  return value ? dateTime.format(new Date(value)) : '—';
}
