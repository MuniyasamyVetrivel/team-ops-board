import { parseLocalDate } from '@/features/tasks/task-meta';

import type { MilestoneStatus, ProjectStatus, RiskLevel, RiskStatus } from './api';

export const PROJECT_STATUS_LABELS: Record<ProjectStatus, string> = {
  PLANNING: 'Planning',
  ACTIVE: 'Active',
  ON_HOLD: 'On hold',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

export const MILESTONE_STATUS_LABELS: Record<MilestoneStatus, string> = {
  PLANNED: 'Planned',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
};

export const RISK_LEVEL_LABELS: Record<RiskLevel, string> = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High' };

export const RISK_STATUS_LABELS: Record<RiskStatus, string> = { OPEN: 'Open', MITIGATED: 'Mitigated', CLOSED: 'Closed' };

/** Severity 1–9 (probability × impact) in words: 6+ high, 3–4 medium, otherwise low. */
export function severityLevel(severity: number): RiskLevel {
  if (severity >= 6) return 'HIGH';
  return severity >= 3 ? 'MEDIUM' : 'LOW';
}

const dateFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

/** "3 Oct 2026 – 15 Dec 2026", "From 3 Oct 2026", or "No dates". Dates are calendar dates (LocalDate). */
export function dateRange(start: string | null, end: string | null): string {
  const format = (value: string) => dateFormat.format(parseLocalDate(value));
  if (start && end) return `${format(start)} – ${format(end)}`;
  if (start) return `From ${format(start)}`;
  if (end) return `Until ${format(end)}`;
  return 'No dates';
}
