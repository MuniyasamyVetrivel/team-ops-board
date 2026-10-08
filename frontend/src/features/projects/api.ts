import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { ProjectRef } from '@/features/tasks/types';
import { api } from '@/lib/api/client';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

export type ProjectStatus = 'PLANNING' | 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'CANCELLED';
export type MilestoneStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED';
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';
export type RiskStatus = 'OPEN' | 'MITIGATED' | 'CLOSED';

export const PROJECT_STATUSES: ProjectStatus[] = ['PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED'];

interface DepartmentRef {
  id: number;
  name: string;
  code: string;
}

export interface TaskCounts {
  total: number;
  completed: number;
  open: number;
  overdue: number;
}

export interface MilestoneCounts {
  total: number;
  completed: number;
  overdue: number;
}

/** Mirrors ProjectDtos.ProjectListItem. {@code progress} is null ("—") when there are no tasks and no override. */
export interface ProjectListItem {
  id: number;
  code: string;
  name: string;
  status: ProjectStatus;
  department: DepartmentRef;
  owner: UserSummary | null;
  startDate: string | null;
  endDate: string | null;
  progress: number | null;
  progressOverridden: boolean;
  tasks: TaskCounts;
  milestones: MilestoneCounts;
  openRisks: number;
  updatedAt: string;
}

export interface Milestone {
  id: number;
  name: string;
  description: string | null;
  dueDate: string | null;
  status: MilestoneStatus;
  completedAt: string | null;
  position: number;
  overdue: boolean;
}

export interface Risk {
  id: number;
  title: string;
  description: string | null;
  probability: RiskLevel;
  impact: RiskLevel;
  severity: number;
  mitigation: string | null;
  owner: UserSummary | null;
  status: RiskStatus;
}

/** Mirrors ProjectDtos.ProjectDetail. */
export interface ProjectDetail {
  id: number;
  code: string;
  name: string;
  description: string | null;
  status: ProjectStatus;
  department: DepartmentRef;
  owner: UserSummary | null;
  startDate: string | null;
  endDate: string | null;
  progress: number | null;
  progressOverride: number | null;
  tasks: TaskCounts;
  members: UserSummary[];
  milestones: Milestone[];
  risks: Risk[];
  dependencies: ProjectRef[];
  version: number;
  createdAt: string;
  updatedAt: string;
  permissions: { canEdit: boolean };
}

export interface ProjectQuery {
  search?: string;
  status?: ProjectStatus[];
  departmentId?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface CreateProjectInput {
  name: string;
  description: string | null;
  departmentId: number;
  ownerId: number | null;
  startDate: string | null;
  endDate: string | null;
}

export interface UpdateProjectInput extends CreateProjectInput {
  version: number;
  status: ProjectStatus;
  progressOverride: number | null;
}

export interface SaveMilestoneInput {
  name: string;
  description: string | null;
  dueDate: string | null;
  status: MilestoneStatus;
}

export interface SaveRiskInput {
  title: string;
  description: string | null;
  probability: RiskLevel;
  impact: RiskLevel;
  mitigation: string | null;
  ownerId: number | null;
  status: RiskStatus;
}

export const projectKeys = {
  all: ['projects'] as const,
  lists: () => [...projectKeys.all, 'list'] as const,
  list: (query: ProjectQuery) => [...projectKeys.lists(), query] as const,
  detail: (id: number) => [...projectKeys.all, 'detail', id] as const,
};

export function useProjects(query: ProjectQuery) {
  return useQuery({
    queryKey: projectKeys.list(query),
    queryFn: async () =>
      (await api.get<PageResponse<ProjectListItem>>('/projects', { params: cleanParams(query), paramsSerializer: serializeParams })).data,
    placeholderData: keepPreviousData,
  });
}

export function useProject(id: number) {
  return useQuery({
    queryKey: projectKeys.detail(id),
    queryFn: async () => (await api.get<ProjectDetail>(`/projects/${id}`)).data,
    enabled: Number.isFinite(id),
  });
}

/** Every project mutation returns the fresh detail: cache it and refresh lists, options and the calendar. */
function useProjectMutation<V>(mutationFn: (variables: V) => Promise<ProjectDetail>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (detail) => {
      queryClient.setQueryData(projectKeys.detail(detail.id), detail);
      void queryClient.invalidateQueries({ queryKey: projectKeys.lists() });
      void queryClient.invalidateQueries({ queryKey: ['projects', 'options'] });
      void queryClient.invalidateQueries({ queryKey: ['calendar'] });
    },
  });
}

const data = <T,>(promise: Promise<{ data: T }>) => promise.then((response) => response.data);

export function useCreateProject() {
  return useProjectMutation((input: CreateProjectInput) => data(api.post<ProjectDetail>('/projects', input)));
}

export function useUpdateProject(id: number) {
  return useProjectMutation((input: UpdateProjectInput) => data(api.put<ProjectDetail>(`/projects/${id}`, input)));
}

export function useProjectMilestones(id: number) {
  return {
    add: useProjectMutation((input: SaveMilestoneInput) => data(api.post<ProjectDetail>(`/projects/${id}/milestones`, input))),
    update: useProjectMutation(({ milestoneId, input }: { milestoneId: number; input: SaveMilestoneInput }) =>
      data(api.put<ProjectDetail>(`/projects/${id}/milestones/${milestoneId}`, input)),
    ),
    remove: useProjectMutation((milestoneId: number) => data(api.delete<ProjectDetail>(`/projects/${id}/milestones/${milestoneId}`))),
  };
}

export function useProjectRisks(id: number) {
  return {
    add: useProjectMutation((input: SaveRiskInput) => data(api.post<ProjectDetail>(`/projects/${id}/risks`, input))),
    update: useProjectMutation(({ riskId, input }: { riskId: number; input: SaveRiskInput }) =>
      data(api.put<ProjectDetail>(`/projects/${id}/risks/${riskId}`, input)),
    ),
    remove: useProjectMutation((riskId: number) => data(api.delete<ProjectDetail>(`/projects/${id}/risks/${riskId}`))),
  };
}

export function useProjectMembers(id: number) {
  return {
    add: useProjectMutation((userId: number) => data(api.post<ProjectDetail>(`/projects/${id}/members`, { userId }))),
    remove: useProjectMutation((userId: number) => data(api.delete<ProjectDetail>(`/projects/${id}/members/${userId}`))),
  };
}

export function useProjectDependencies(id: number) {
  return {
    add: useProjectMutation((projectId: number) => data(api.post<ProjectDetail>(`/projects/${id}/dependencies`, { projectId }))),
    remove: useProjectMutation((projectId: number) => data(api.delete<ProjectDetail>(`/projects/${id}/dependencies/${projectId}`))),
  };
}

/** Full update payload from the current detail plus the changed fields. */
export function toUpdateProjectInput(project: ProjectDetail, patch: Partial<Omit<UpdateProjectInput, 'version'>>): UpdateProjectInput {
  return {
    version: project.version,
    name: project.name,
    description: project.description,
    departmentId: project.department.id,
    ownerId: project.owner?.id ?? null,
    startDate: project.startDate,
    endDate: project.endDate,
    status: project.status,
    progressOverride: project.progressOverride,
    ...patch,
  };
}
