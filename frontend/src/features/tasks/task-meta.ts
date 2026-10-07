import type { DueState, TaskPriority, TaskStatus } from './types';

export const STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  BLOCKED: 'Blocked',
  IN_REVIEW: 'In review',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

export const PRIORITY_LABELS: Record<TaskPriority, string> = {
  URGENT: 'Urgent',
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
};

const shortDate = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' });
const weekdayDate = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short' });

/** Parses a server LocalDate ("2026-10-07") as a calendar date, not a UTC instant. */
export function parseLocalDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year ?? 1970, (month ?? 1) - 1, day ?? 1);
}

/** Text for a due date, given the server-computed due state. */
export function dueLabel(dueDate: string | null, state: DueState): string {
  if (!dueDate) return 'No due date';
  const date = parseLocalDate(dueDate);
  switch (state) {
    case 'OVERDUE':
      return `Overdue · ${shortDate.format(date)}`;
    case 'DUE_TODAY':
      return 'Due today';
    case 'DUE_SOON':
      return weekdayDate.format(date);
    default:
      return shortDate.format(date);
  }
}

/** Human-readable label for a task_history field name. */
export function historyLabel(field: string): string {
  const labels: Record<string, string> = {
    created: 'created the task',
    status: 'changed status',
    reopened: 'reopened the task',
    assignee: 'changed assignee',
    priority: 'changed priority',
    dueDate: 'changed due date',
    startDate: 'changed start date',
    estimatedHours: 'changed estimate',
    actualHours: 'logged hours',
    title: 'renamed the task',
    description: 'edited the description',
    department: 'moved department',
    project: 'changed project',
    tags: 'changed tags',
  };
  return labels[field] ?? field;
}

/** Formats a history value: status codes become labels, everything else is shown as is. */
export function historyValue(value: string | null): string {
  if (value === null || value === '') return '—';
  return (STATUS_LABELS as Record<string, string>)[value] ?? (PRIORITY_LABELS as Record<string, string>)[value] ?? value;
}

export function formatHours(hours: number | null | undefined): string {
  return hours === null || hours === undefined ? '—' : `${Number(hours).toLocaleString(undefined, { maximumFractionDigits: 1 })} h`;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
