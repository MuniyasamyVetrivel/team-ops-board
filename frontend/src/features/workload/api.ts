import { keepPreviousData, useQuery } from '@tanstack/react-query';

import type { TaskPriority, TaskStatus } from '@/features/tasks/types';
import { api } from '@/lib/api/client';
import { cleanParams, serializeParams, type UserSummary } from '@/lib/api/types';

export type WorkloadLevel = 'LOW' | 'NORMAL' | 'HIGH' | 'OVERLOADED';
export type WorkloadSort = 'HIGHEST' | 'LOWEST' | 'MOST_OVERDUE' | 'MOST_COMPLETED' | 'MOST_ACTIVE' | 'NAME';

/** Mirrors com.teamops.workload.WorkloadDtos.Row. */
export interface WorkloadRow {
  user: UserSummary;
  department: { id: number; name: string; code: string };
  totalTasks: number;
  todo: number;
  inProgress: number;
  blocked: number;
  inReview: number;
  completed: number;
  overdue: number;
  dueToday: number;
  activeTasks: number;
  remainingHours: number;
  capacityHours: number;
  workloadPercent: number;
  level: WorkloadLevel;
}

export interface WorkloadResponse {
  today: string;
  windowDays: number;
  defaultTaskHours: number;
  from: string | null;
  to: string | null;
  summary: {
    people: number;
    byLevel: Record<WorkloadLevel, number>;
    activeTasks: number;
    overdue: number;
    dueToday: number;
    averagePercent: number;
  };
  rows: WorkloadRow[];
}

export interface WorkloadQuery {
  departmentId?: number;
  userId?: number;
  search?: string;
  status?: TaskStatus[];
  priority?: TaskPriority[];
  from?: string;
  to?: string;
  sort?: WorkloadSort;
}

export function useWorkload(query: WorkloadQuery, enabled = true) {
  return useQuery({
    queryKey: ['workload', query],
    queryFn: async () => (await api.get<WorkloadResponse>('/workload', { params: cleanParams(query), paramsSerializer: serializeParams })).data,
    placeholderData: keepPreviousData,
    enabled,
  });
}
