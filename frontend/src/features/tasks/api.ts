import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type PageResponse } from '@/lib/api/types';

import type {
  CreateTaskInput,
  MyTaskSummary,
  ProjectRef,
  TaskDetail,
  TaskListItem,
  TaskQuery,
  TaskStatus,
  UpdateTaskInput,
} from './types';

export const taskKeys = {
  all: ['tasks'] as const,
  lists: () => [...taskKeys.all, 'list'] as const,
  list: (query: TaskQuery) => [...taskKeys.lists(), query] as const,
  detail: (id: number) => [...taskKeys.all, 'detail', id] as const,
  summary: () => [...taskKeys.all, 'my-summary'] as const,
  projects: ['projects', 'options'] as const,
};

export function useTasks(query: TaskQuery, enabled = true) {
  return useQuery({
    queryKey: taskKeys.list(query),
    queryFn: async () =>
      (await api.get<PageResponse<TaskListItem>>('/tasks', { params: cleanParams(query), paramsSerializer: serializeParams })).data,
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useTask(id: number | null) {
  return useQuery({
    queryKey: taskKeys.detail(id ?? 0),
    queryFn: async () => (await api.get<TaskDetail>(`/tasks/${id}`)).data,
    enabled: id !== null,
  });
}

export function useMyTaskSummary() {
  return useQuery({
    queryKey: taskKeys.summary(),
    queryFn: async () => (await api.get<MyTaskSummary>('/tasks/my/summary')).data,
  });
}

export function useProjectOptions() {
  return useQuery({
    queryKey: taskKeys.projects,
    queryFn: async () => (await api.get<ProjectRef[]>('/projects/options')).data,
    staleTime: 5 * 60_000,
  });
}

/** Every task mutation returns the fresh detail: cache it and refresh lists, summaries and workload. */
function useTaskMutation<V, R extends TaskDetail | null>(mutationFn: (variables: V) => Promise<R>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (detail) => {
      if (detail) queryClient.setQueryData(taskKeys.detail(detail.id), detail);
      void queryClient.invalidateQueries({ queryKey: taskKeys.lists() });
      void queryClient.invalidateQueries({ queryKey: taskKeys.summary() });
      void queryClient.invalidateQueries({ queryKey: ['workload'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

const data = <T,>(promise: Promise<{ data: T }>) => promise.then((response) => response.data);

export function useCreateTask() {
  return useTaskMutation((input: CreateTaskInput) => data(api.post<TaskDetail>('/tasks', input)));
}

export function useUpdateTask(id: number) {
  return useTaskMutation((input: UpdateTaskInput) => data(api.put<TaskDetail>(`/tasks/${id}`, input)));
}

export function useChangeStatus(id: number) {
  return useTaskMutation((status: TaskStatus) => data(api.put<TaskDetail>(`/tasks/${id}/status`, { status })));
}

export function useAssignTask(id: number) {
  return useTaskMutation((assigneeId: number | null) => data(api.put<TaskDetail>(`/tasks/${id}/assignee`, { assigneeId })));
}

export function useTaskComments(id: number) {
  return {
    add: useTaskMutation((body: string) => data(api.post<TaskDetail>(`/tasks/${id}/comments`, { body }))),
    remove: useTaskMutation((commentId: number) => data(api.delete<TaskDetail>(`/tasks/${id}/comments/${commentId}`))),
  };
}

export function useTaskChecklist(id: number) {
  return {
    add: useTaskMutation((content: string) => data(api.post<TaskDetail>(`/tasks/${id}/checklist`, { content }))),
    toggle: useTaskMutation(({ itemId, done }: { itemId: number; done: boolean }) =>
      data(api.put<TaskDetail>(`/tasks/${id}/checklist/${itemId}`, { done })),
    ),
    remove: useTaskMutation((itemId: number) => data(api.delete<TaskDetail>(`/tasks/${id}/checklist/${itemId}`))),
  };
}

export function useTaskWatchers(id: number) {
  return {
    add: useTaskMutation((userId: number) => data(api.post<TaskDetail>(`/tasks/${id}/watchers`, { userId }))),
    /** Resolves to null when the caller stopped watching and can no longer see the task (204). */
    remove: useTaskMutation(async (userId: number) => {
      const response = await api.delete<TaskDetail | ''>(`/tasks/${id}/watchers/${userId}`);
      return response.status === 204 || !response.data ? null : response.data;
    }),
  };
}

export function useTaskDependencies(id: number) {
  return {
    add: useTaskMutation((dependsOnTaskId: number) => data(api.post<TaskDetail>(`/tasks/${id}/dependencies`, { dependsOnTaskId }))),
    remove: useTaskMutation((dependsOnTaskId: number) => data(api.delete<TaskDetail>(`/tasks/${id}/dependencies/${dependsOnTaskId}`))),
  };
}

export function useTaskAttachments(id: number) {
  return {
    upload: useTaskMutation((file: File) => {
      const form = new FormData();
      form.append('file', file);
      return data(api.post<TaskDetail>(`/tasks/${id}/attachments`, form));
    }),
    remove: useTaskMutation((fileId: number) => data(api.delete<TaskDetail>(`/tasks/${id}/attachments/${fileId}`))),
  };
}

export async function downloadAttachment(taskId: number, fileId: number, fileName: string): Promise<void> {
  return downloadFile(`/tasks/${taskId}/attachments/${fileId}`, fileName);
}

/** Builds the full update payload from the current detail plus the changed fields. */
export function toUpdateInput(task: TaskDetail, patch: Partial<Omit<UpdateTaskInput, 'version'>>): UpdateTaskInput {
  return {
    version: task.version,
    title: task.title,
    description: task.description,
    departmentId: task.department.id,
    projectId: task.project?.id ?? null,
    priority: task.priority,
    startDate: task.startDate,
    dueDate: task.dueDate,
    estimatedHours: task.estimatedHours,
    actualHours: task.actualHours,
    tags: task.tags,
    ...patch,
  };
}
