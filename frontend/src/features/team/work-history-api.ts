import { keepPreviousData, useQuery } from '@tanstack/react-query';

import type { TaskPriority, TaskStatus } from '@/features/tasks/types';
import type { SlaState, TicketPriority, TicketStatus } from '@/features/tickets/types';
import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

/** Mirrors WorkHistoryDtos.DateField. */
export type WorkDateField = 'COMPLETED' | 'DUE' | 'START' | 'CREATED';
/** Mirrors WorkHistoryDtos.DatePreset: resolved on the server against the business today. */
export type WorkDatePreset = 'THIS_WEEK' | 'LAST_WEEK' | 'THIS_MONTH' | 'LAST_MONTH' | 'LAST_3_MONTHS' | 'LAST_30_DAYS';

/** Mirrors WorkHistoryDtos.TaskRow. */
export interface TaskHistoryRow {
  id: number;
  code: string;
  title: string;
  project: { id: number; code: string; name: string } | null;
  priority: TaskPriority;
  status: TaskStatus;
  startDate: string | null;
  dueDate: string | null;
  completedDate: string | null;
  estimatedHours: number | null;
  actualHours: number | null;
  overdue: boolean;
  /** Completed with a due date: finished on or before it. */
  onTime: boolean | null;
  /** Days a completed task finished late, or an open one has been overdue. */
  daysLate: number | null;
}

/** Mirrors WorkHistoryDtos.TicketRow; `onTime` is whether the resolution SLA was met. */
export interface TicketHistoryRow {
  id: number;
  code: string;
  subject: string;
  category: string | null;
  priority: TicketPriority;
  status: TicketStatus;
  createdDate: string;
  dueAt: string | null;
  resolvedDate: string | null;
  slaState: SlaState;
  overdue: boolean;
  onTime: boolean | null;
  daysLate: number | null;
}

/** Mirrors WorkHistoryDtos.WorkSummary. Percentages and averages are null with nothing to divide by. */
export interface WorkSummary {
  person: UserSummary;
  today: string;
  from: string | null;
  to: string | null;
  total: number;
  byStatus: Record<string, number>;
  overdue: number;
  completed: number;
  completedOnTimePct: number | null;
  averageDaysToComplete: number | null;
  /** Tasks only. */
  totalActualHours: number | null;
  completedPerWeek: { weekStart: string; completed: number }[];
}

/** The filters every work history endpoint takes. `groupId` is sent as projectId (tasks) or categoryId (tickets). */
export interface WorkQuery {
  status?: string[];
  dateField?: WorkDateField;
  preset?: WorkDatePreset;
  from?: string;
  to?: string;
  groupId?: number;
  priority?: string[];
  search?: string;
}

export type WorkKind = 'tasks' | 'tickets';

export const workHistoryKeys = {
  all: ['team', 'work-history'] as const,
  list: (userId: number, kind: WorkKind, query: WorkQuery, page: number, sort: string) =>
    [...workHistoryKeys.all, userId, kind, 'list', query, page, sort] as const,
  summary: (userId: number, kind: WorkKind, query: WorkQuery) => [...workHistoryKeys.all, userId, kind, 'summary', query] as const,
};

function params(kind: WorkKind, query: WorkQuery, extra: Record<string, unknown> = {}) {
  const { groupId, ...rest } = query;
  return cleanParams({ ...rest, [kind === 'tasks' ? 'projectId' : 'categoryId']: groupId, ...extra });
}

const get = <T,>(url: string, query: Record<string, unknown>) => api.get<T>(url, { params: query, paramsSerializer: serializeParams }).then((r) => r.data);

export function useWorkList<T extends TaskHistoryRow | TicketHistoryRow>(userId: number, kind: WorkKind, query: WorkQuery, page: number, sort: string) {
  return useQuery({
    queryKey: workHistoryKeys.list(userId, kind, query, page, sort),
    queryFn: () => get<PageResponse<T>>(`/team/${userId}/${kind}`, params(kind, query, { page, size: 25, sort })),
    placeholderData: keepPreviousData,
  });
}

export function useWorkSummary(userId: number, kind: WorkKind, query: WorkQuery) {
  return useQuery({
    queryKey: workHistoryKeys.summary(userId, kind, query),
    queryFn: () => get<WorkSummary>(`/team/${userId}/${kind}/summary`, params(kind, query)),
    placeholderData: keepPreviousData,
  });
}

/** The filtered list as CSV, in the list's order. */
export function exportWork(userId: number, kind: WorkKind, query: WorkQuery, sort: string, fileName: string): Promise<void> {
  const search = serializeParams(params(kind, query, { sort }));
  return downloadFile(`/team/${userId}/${kind}/export?${search}`, fileName);
}
