import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import type { UserSummary } from '@/lib/api/types';

export type DepartmentStatus = 'ACTIVE' | 'INACTIVE';
export type MemberRole = 'MANAGER' | 'MEMBER';

/** Mirrors com.teamops.department.dto.DepartmentListItem. */
export interface DepartmentListItem {
  id: number;
  name: string;
  code: string;
  description: string | null;
  status: DepartmentStatus;
  manager: UserSummary | null;
  memberCount: number;
  secondaryMemberCount: number;
}

export interface DepartmentMemberItem {
  user: UserSummary;
  /** PRIMARY, or MANAGER / MEMBER for secondary memberships. */
  membership: 'PRIMARY' | MemberRole;
  primaryDepartment: { id: number; name: string; code: string };
}

/** Mirrors com.teamops.department.dto.DepartmentDetail. */
export interface DepartmentDetail {
  id: number;
  name: string;
  code: string;
  description: string | null;
  status: DepartmentStatus;
  manager: UserSummary | null;
  createdAt: string;
  members: DepartmentMemberItem[];
  canEdit: boolean;
  canManageMembers: boolean;
}

export interface CreateDepartmentInput {
  name: string;
  code: string;
  description?: string | null;
  managerId?: number | null;
}

export interface UpdateDepartmentInput {
  name: string;
  description?: string | null;
  managerId?: number | null;
  status: DepartmentStatus;
}

export const departmentKeys = {
  all: ['departments'] as const,
  list: () => [...departmentKeys.all, 'list'] as const,
  detail: (id: number) => [...departmentKeys.all, 'detail', id] as const,
};

/** All departments; any signed-in user may call this (filters and pickers). */
export function useDepartments() {
  return useQuery({
    queryKey: departmentKeys.list(),
    queryFn: async () => (await api.get<DepartmentListItem[]>('/departments')).data,
    staleTime: 5 * 60_000,
  });
}

export function useDepartment(id: number | null) {
  return useQuery({
    queryKey: departmentKeys.detail(id ?? 0),
    queryFn: async () => (await api.get<DepartmentDetail>(`/departments/${id}`)).data,
    enabled: id !== null,
  });
}

function useDepartmentMutation<V>(mutationFn: (variables: V) => Promise<DepartmentDetail>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (detail) => {
      queryClient.setQueryData(departmentKeys.detail(detail.id), detail);
      void queryClient.invalidateQueries({ queryKey: departmentKeys.list() });
    },
  });
}

export function useCreateDepartment() {
  return useDepartmentMutation(async (input: CreateDepartmentInput) => (await api.post<DepartmentDetail>('/departments', input)).data);
}

export function useUpdateDepartment(id: number) {
  return useDepartmentMutation(async (input: UpdateDepartmentInput) => (await api.put<DepartmentDetail>(`/departments/${id}`, input)).data);
}

export function useUpsertMember(departmentId: number) {
  return useDepartmentMutation(
    async ({ userId, role }: { userId: number; role: MemberRole }) =>
      (await api.put<DepartmentDetail>(`/departments/${departmentId}/members/${userId}`, { role })).data,
  );
}

export function useRemoveMember(departmentId: number) {
  return useDepartmentMutation(
    async (userId: number) => (await api.delete<DepartmentDetail>(`/departments/${departmentId}/members/${userId}`)).data,
  );
}
