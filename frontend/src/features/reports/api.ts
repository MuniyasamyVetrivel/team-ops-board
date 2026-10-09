import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type UserSummary } from '@/lib/api/types';
import type { ProjectStatus } from '@/features/projects/api';
import type { TaskStatus } from '@/features/tasks/types';
import type { TicketPriority, TicketStatus } from '@/features/tickets/types';
import type { WorkloadLevel, WorkloadResponse } from '@/features/workload/api';

interface DepartmentRef {
  id: number;
  name: string;
  code: string;
}

/** Mirrors ReportDtos.Range: business days, both ends included. */
export interface ReportRange {
  from: string;
  to: string;
}

/** Mirrors ReportDtos.TaskSummary; percentages are null with nothing to divide by ("—"). */
export interface TaskSummary {
  created: number;
  completed: number;
  completedWithDueDate: number;
  completedOnTime: number;
  onTimePct: number | null;
  open: number;
  overdue: number;
  overduePct: number | null;
  hoursLogged: number;
}

export interface TaskReport {
  range: ReportRange;
  summary: TaskSummary;
  openByStatus: { status: TaskStatus; tasks: number }[];
  departments: { department: DepartmentRef; created: number; completed: number; onTimePct: number | null; open: number; overdue: number; overduePct: number | null }[];
  employees: { user: UserSummary; department: DepartmentRef; completed: number; onTimePct: number | null; open: number; overdue: number; hoursLogged: number }[];
  trend: { weekStart: string; created: number; completed: number }[];
}

export interface WorkloadReport {
  employees: WorkloadResponse;
  departments: {
    department: DepartmentRef;
    people: number;
    activeTasks: number;
    overdue: number;
    remainingHours: number;
    capacityHours: number;
    workloadPercent: number | null;
    level: WorkloadLevel | null;
  }[];
}

export interface TicketSummary {
  created: number;
  resolved: number;
  open: number;
  openBreached: number;
  firstResponseCompliance: number | null;
  resolutionCompliance: number | null;
  /** Wall-clock hours from creation to resolution. */
  averageResolutionHours: number | null;
}

export interface TicketReport {
  range: ReportRange;
  summary: TicketSummary;
  priorities: { priority: TicketPriority; created: number; resolved: number; open: number; firstResponseCompliance: number | null; resolutionCompliance: number | null }[];
  departments: { department: DepartmentRef; created: number; resolved: number; open: number; openBreached: number; resolutionCompliance: number | null }[];
  ageing: { label: string; tickets: number }[];
  openByStatus: { status: TicketStatus; tickets: number }[];
}

export interface ProjectReport {
  today: string;
  byStatus: { status: ProjectStatus; projects: number }[];
  projects: {
    id: number;
    code: string;
    name: string;
    status: ProjectStatus;
    department: DepartmentRef | null;
    owner: UserSummary | null;
    startDate: string | null;
    endDate: string | null;
    progress: number | null;
    tasks: number;
    tasksCompleted: number;
    tasksOverdue: number;
    milestones: number;
    milestonesCompleted: number;
    milestonesOverdue: number;
    openRisks: number;
    pastEndDate: boolean;
  }[];
}

export type ReportKind = 'tasks' | 'workload' | 'tickets' | 'projects';

export interface ReportQuery {
  from?: string;
  to?: string;
  departmentId?: number;
  userId?: number;
  assigneeId?: number;
  projectId?: number;
  status?: string[];
}

export const reportKeys = {
  all: ['reports'] as const,
  report: (kind: ReportKind, query: ReportQuery) => [...reportKeys.all, kind, query] as const,
};

const get = <T,>(url: string, params: object) =>
  api.get<T>(url, { params: cleanParams(params), paramsSerializer: serializeParams }).then((r) => r.data);

function useReport<T>(kind: ReportKind, query: ReportQuery, enabled: boolean) {
  return useQuery({ queryKey: reportKeys.report(kind, query), queryFn: () => get<T>(`/reports/${kind}`, query), enabled, placeholderData: keepPreviousData });
}

export const useTaskReport = (query: ReportQuery, enabled = true) => useReport<TaskReport>('tasks', query, enabled);
export const useWorkloadReport = (query: ReportQuery, enabled = true) => useReport<WorkloadReport>('workload', query, enabled);
export const useTicketReport = (query: ReportQuery, enabled = true) => useReport<TicketReport>('tickets', query, enabled);
export const useProjectReport = (query: ReportQuery, enabled = true) => useReport<ProjectReport>('projects', query, enabled);

/** Downloads a report for the same filters as on screen (CSV). */
export function exportReport(kind: ReportKind, query: ReportQuery): Promise<void> {
  const search = serializeParams(cleanParams({ ...query, format: 'CSV' }));
  return downloadFile(`/reports/${kind}/export?${search}`, `${kind}-report.csv`);
}
