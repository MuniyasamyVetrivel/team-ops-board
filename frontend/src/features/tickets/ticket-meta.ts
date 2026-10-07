import { PRIORITY_LABELS } from '@/features/tasks/task-meta';

import type { SlaState, SlaStatus, TicketStatus } from './types';

export const TICKET_STATUS_LABELS: Record<TicketStatus, string> = {
  NEW: 'New',
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  WAITING_FOR_REQUESTER: 'Waiting for requester',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
};

export const SLA_STATE_LABELS: Record<SlaState, string> = {
  ON_TRACK: 'On track',
  WARNING: 'At risk',
  BREACHED: 'Breached',
};

/** "45m", "3h 20m", "2d 4h". Rounds down to the minute; negative values use their absolute size. */
export function formatMinutes(minutes: number): string {
  const total = Math.abs(Math.trunc(minutes));
  const days = Math.floor(total / 1440);
  const hours = Math.floor((total % 1440) / 60);
  const mins = total % 60;
  if (days > 0) return hours > 0 ? `${days}d ${hours}h` : `${days}d`;
  if (hours > 0) return mins > 0 ? `${hours}h ${mins}m` : `${hours}h`;
  return `${mins}m`;
}

/** Countdown text for one deadline, from the server-computed status. */
export function slaText(status: SlaStatus): string {
  if (status.met === true) return 'Met';
  if (status.met === false) return `Missed by ${formatMinutes(status.remainingMinutes)}`;
  if (status.remainingMinutes < 0) return `Overdue by ${formatMinutes(status.remainingMinutes)}`;
  const left = `${formatMinutes(status.remainingMinutes)} left`;
  return status.paused ? `Paused · ${left}` : left;
}

/** Human-readable label for a ticket_history field name. */
export function ticketHistoryLabel(field: string): string {
  const labels: Record<string, string> = {
    created: 'raised the ticket',
    status: 'changed status',
    reopened: 'reopened the ticket',
    assignee: 'changed assignee',
    priority: 'changed priority',
    category: 'changed category',
    department: 'moved the ticket',
    subject: 'renamed the ticket',
    description: 'edited the description',
    'first response': 'sent the first response',
  };
  return labels[field] ?? field;
}

/** Formats a history value: status and priority codes become labels. */
export function ticketHistoryValue(value: string | null): string {
  if (value === null || value === '') return '—';
  return (TICKET_STATUS_LABELS as Record<string, string>)[value] ?? (PRIORITY_LABELS as Record<string, string>)[value] ?? value;
}
