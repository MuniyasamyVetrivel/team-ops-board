import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { DueState, TaskPriority, TaskRef } from '@/features/tasks/types';
import { api } from '@/lib/api/client';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

export type Frequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY';
/** Overdue is not a status: it comes from `dueState`. */
export type OccurrenceStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';

export const FREQUENCIES: Frequency[] = ['DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY'];
export const OPEN_STATUSES: OccurrenceStatus[] = ['PENDING', 'IN_PROGRESS'];

interface DepartmentRef {
  id: number;
  name: string;
  code: string;
}

/**
 * Mirrors ActivityDtos.OccurrenceItem. `task` is the generated task (the occurrence then follows it); `canAct` says
 * whether the viewer may complete, skip or reopen it here (only occurrences without a task).
 */
export interface OccurrenceItem {
  id: number;
  activityId: number;
  activityName: string;
  frequency: Frequency;
  periodStart: string;
  periodEnd: string;
  periodLabel: string;
  dueDate: string;
  dueState: DueState;
  status: OccurrenceStatus;
  task: TaskRef | null;
  assignee: UserSummary | null;
  completedAt: string | null;
  completedBy: UserSummary | null;
  notes: string | null;
  version: number;
  canAct: boolean;
}

/** Mirrors ActivityDtos.ActivityListItem. */
export interface ActivityListItem {
  id: number;
  name: string;
  frequency: Frequency;
  department: DepartmentRef;
  owner: UserSummary | null;
  assignee: UserSummary | null;
  startDate: string;
  endDate: string | null;
  active: boolean;
  generatesTasks: boolean;
  lastCompletedAt: string | null;
  nextOccurrence: OccurrenceItem | null;
  openCount: number;
  overdueCount: number;
  updatedAt: string;
}

/** Mirrors ActivityDtos.ActivityDetail. `locked`: has occurrences, so frequency and start date are fixed. */
export interface ActivityDetail {
  id: number;
  name: string;
  description: string | null;
  frequency: Frequency;
  department: DepartmentRef;
  owner: UserSummary | null;
  defaultAssignee: UserSummary | null;
  startDate: string;
  endDate: string | null;
  dueOffsetDays: number;
  taskTitleTemplate: string | null;
  nextTaskTitle: string | null;
  taskPriority: TaskPriority;
  active: boolean;
  checklist: string[];
  lastCompletedAt: string | null;
  nextOccurrence: OccurrenceItem | null;
  recentOccurrences: OccurrenceItem[];
  occurrenceCount: number;
  locked: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
  permissions: { canEdit: boolean };
}

export interface ActivityQuery {
  search?: string;
  frequency?: Frequency;
  active?: boolean;
  ownerId?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface OccurrenceQuery {
  status?: OccurrenceStatus[];
  dueFrom?: string;
  dueTo?: string;
  assigneeId?: number;
  activityId?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface SaveActivityInput {
  name: string;
  description: string | null;
  departmentId: number;
  ownerId: number | null;
  frequency: Frequency;
  startDate: string;
  endDate: string | null;
  dueOffsetDays: number;
  taskTitleTemplate: string | null;
  defaultAssigneeId: number | null;
  taskPriority: TaskPriority;
  checklist: string[];
}

export interface UpdateActivityInput extends SaveActivityInput {
  version: number;
  active: boolean;
}

export const activityKeys = {
  all: ['marketing', 'activities'] as const,
  lists: () => [...activityKeys.all, 'list'] as const,
  list: (query: ActivityQuery) => [...activityKeys.lists(), query] as const,
  detail: (id: number) => [...activityKeys.all, 'detail', id] as const,
  history: (id: number, page: number) => [...activityKeys.all, 'history', id, page] as const,
  occurrences: (query: OccurrenceQuery) => [...activityKeys.all, 'occurrences', query] as const,
};

const get = <T,>(url: string, params?: object) =>
  api.get<T>(url, params ? { params: cleanParams(params), paramsSerializer: serializeParams } : undefined).then((response) => response.data);

export function useActivities(query: ActivityQuery) {
  return useQuery({ queryKey: activityKeys.list(query), queryFn: () => get<PageResponse<ActivityListItem>>('/marketing/activities', query), placeholderData: keepPreviousData });
}

export function useActivity(id: number) {
  return useQuery({ queryKey: activityKeys.detail(id), queryFn: () => get<ActivityDetail>(`/marketing/activities/${id}`), enabled: Number.isFinite(id) });
}

/** An activity's occurrences, newest period first. */
export function useActivityHistory(id: number, page: number) {
  return useQuery({
    queryKey: activityKeys.history(id, page),
    queryFn: () => get<PageResponse<OccurrenceItem>>(`/marketing/activities/${id}/occurrences`, { page, size: 12 }),
    enabled: Number.isFinite(id),
    placeholderData: keepPreviousData,
  });
}

/** Occurrences across activities, earliest due first. */
export function useOccurrences(query: OccurrenceQuery) {
  return useQuery({ queryKey: activityKeys.occurrences(query), queryFn: () => get<PageResponse<OccurrenceItem>>('/marketing/activity-occurrences', query), placeholderData: keepPreviousData });
}

/**
 * Activity changes can create occurrences and tasks, so activities and task lists are refreshed together. A saved
 * detail is cached straight away.
 */
function useActivityMutation<V, R>(mutationFn: (variables: V) => Promise<R>, onSaved?: (result: R, queryClient: ReturnType<typeof useQueryClient>) => void) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (result) => {
      onSaved?.(result, queryClient);
      void queryClient.invalidateQueries({ queryKey: activityKeys.all });
      void queryClient.invalidateQueries({ queryKey: ['tasks'] });
    },
  });
}

const cacheDetail = (detail: ActivityDetail, queryClient: ReturnType<typeof useQueryClient>) => queryClient.setQueryData(activityKeys.detail(detail.id), detail);

export function useCreateActivity() {
  return useActivityMutation((input: SaveActivityInput) => api.post<ActivityDetail>('/marketing/activities', input).then((r) => r.data), cacheDetail);
}

export function useUpdateActivity(id: number) {
  return useActivityMutation((input: UpdateActivityInput) => api.put<ActivityDetail>(`/marketing/activities/${id}`, input).then((r) => r.data), cacheDetail);
}

export function useDeleteActivity() {
  return useActivityMutation((id: number) => api.delete(`/marketing/activities/${id}`).then(() => id));
}

export type OccurrenceAction = 'complete' | 'skip' | 'reopen';

/** Complete, skip or reopen an occurrence without a task. Completing or skipping creates the next one. */
export function useOccurrenceAction() {
  return useActivityMutation(({ id, action, notes }: { id: number; action: OccurrenceAction; notes?: string | null }) =>
    api.post<OccurrenceItem>(`/marketing/activity-occurrences/${id}/${action}`, action === 'reopen' ? undefined : { notes: notes ?? null }).then((r) => r.data),
  );
}
