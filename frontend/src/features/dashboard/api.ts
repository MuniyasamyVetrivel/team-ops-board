import { useQuery } from '@tanstack/react-query';

import type { TaskListItem, TaskStatus } from '@/features/tasks/types';
import type { WorkloadLevel, WorkloadRow } from '@/features/workload/api';
import { api } from '@/lib/api/client';

export type DashboardScope = 'ALL' | 'DEPARTMENTS' | 'OWN';

/** Mirrors com.teamops.dashboard.DashboardDtos. Nullable numbers mean "not available" and render as "—". */
export interface DashboardKpis {
  openTasks: number;
  dueToday: number;
  overdue: number;
  completedThisWeek: number;
  inProgress: number;
  blocked: number;
  teamMembers: number | null;
  openTickets: number | null;
  slaBreaches: number | null;
  pendingApprovals: number | null;
}

export interface StatusSlice {
  status: TaskStatus;
  count: number;
}

export interface DepartmentRow {
  department: { id: number; name: string; code: string };
  people: number;
  openTasks: number;
  overdue: number;
  completed: number;
  onTimePercent: number | null;
  workloadPercent: number | null;
  level: WorkloadLevel | null;
}

export interface WorkloadPanel {
  people: number;
  averagePercent: number;
  byLevel: Record<WorkloadLevel, number>;
  windowDays: number;
  rows: WorkloadRow[];
}

export interface WeekPoint {
  weekStart: string;
  completed: number;
  onTime: number;
  onTimePercent: number | null;
}

export interface ActivityItem {
  id: number;
  at: string;
  actorId: number | null;
  actorName: string | null;
  taskId: number;
  taskCode: string;
  taskTitle: string;
  field: string;
  oldValue: string | null;
  newValue: string | null;
}

export interface DashboardResponse {
  today: string;
  weekStart: string;
  scope: DashboardScope;
  kpis: DashboardKpis;
  statusDistribution: StatusSlice[];
  completedWindowDays: number;
  departments: DepartmentRow[];
  workload: WorkloadPanel;
  weeklyCompletion: WeekPoint[];
  overdueTasks: TaskListItem[];
  upcomingTasks: TaskListItem[];
  recentActivity: ActivityItem[];
}

export const dashboardKeys = {
  all: ['dashboard'] as const,
};

export function useDashboard() {
  return useQuery({
    queryKey: dashboardKeys.all,
    queryFn: async () => (await api.get<DashboardResponse>('/dashboard')).data,
    staleTime: 30_000,
  });
}
