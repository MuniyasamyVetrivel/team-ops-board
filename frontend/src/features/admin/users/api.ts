import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { PermissionCode, RoleCode } from '@/features/auth/permissions';
import { departmentKeys } from '@/features/departments/api';
import { teamKeys } from '@/features/team/api';
import { api } from '@/lib/api/client';
import { cleanParams, type PageResponse, type UserStatus, type UserSummary } from '@/lib/api/types';

interface DepartmentRef {
  id: number;
  name: string;
  code: string;
}

/** Mirrors com.teamops.user.dto.UserListItem. */
export interface UserListItem {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  jobTitle: string | null;
  department: DepartmentRef;
  roles: RoleCode[];
  status: UserStatus;
  lastLoginAt: string | null;
}

/** Mirrors com.teamops.user.dto.UserDetail. */
export interface UserDetail {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  jobTitle: string | null;
  phone: string | null;
  location: string | null;
  workingHours: string | null;
  weeklyCapacityHours: number;
  department: DepartmentRef;
  reportsTo: UserSummary | null;
  status: UserStatus;
  lastLoginAt: string | null;
  createdAt: string;
  roles: RoleCode[];
  directPermissions: PermissionCode[];
  effectivePermissions: PermissionCode[];
}

export interface RoleResponse {
  id: number;
  code: RoleCode;
  name: string;
  description: string | null;
  permissions: PermissionCode[];
}

export interface PermissionResponse {
  code: PermissionCode;
  name: string;
  module: string;
  description: string | null;
}

export interface UserQuery {
  search?: string;
  departmentId?: number;
  status?: UserStatus;
  role?: RoleCode;
  page?: number;
  size?: number;
  sort?: string;
}

export interface UserProfileInput {
  email: string;
  firstName: string;
  lastName?: string;
  jobTitle?: string;
  phone?: string;
  location?: string;
  workingHours?: string;
  departmentId: number;
  reportsToId?: number | null;
  weeklyCapacityHours: number;
}

export interface CreateUserInput extends UserProfileInput {
  password: string;
  roles: RoleCode[];
  permissions: PermissionCode[];
}

export interface AccessInput {
  roles: RoleCode[];
  permissions: PermissionCode[];
}

export const userKeys = {
  all: ['users'] as const,
  list: (query: UserQuery) => [...userKeys.all, 'list', query] as const,
  detail: (id: number) => [...userKeys.all, 'detail', id] as const,
  roles: ['access', 'roles'] as const,
  permissions: ['access', 'permissions'] as const,
};

export function useUsers(query: UserQuery) {
  return useQuery({
    queryKey: userKeys.list(query),
    queryFn: async () => (await api.get<PageResponse<UserListItem>>('/users', { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
  });
}

export function useUser(id: number | null) {
  return useQuery({
    queryKey: userKeys.detail(id ?? 0),
    queryFn: async () => (await api.get<UserDetail>(`/users/${id}`)).data,
    enabled: id !== null,
  });
}

export function useRoles() {
  return useQuery({
    queryKey: userKeys.roles,
    queryFn: async () => (await api.get<RoleResponse[]>('/roles')).data,
    staleTime: 10 * 60_000,
  });
}

export function usePermissions() {
  return useQuery({
    queryKey: userKeys.permissions,
    queryFn: async () => (await api.get<PermissionResponse[]>('/permissions')).data,
    staleTime: 10 * 60_000,
  });
}

/** Every user mutation returns the fresh detail; lists, team and department counts are refreshed. */
function useUserMutation<V>(mutationFn: (variables: V) => Promise<UserDetail>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (detail) => {
      queryClient.setQueryData(userKeys.detail(detail.id), detail);
      void queryClient.invalidateQueries({ queryKey: [...userKeys.all, 'list'] });
      void queryClient.invalidateQueries({ queryKey: teamKeys.all });
      void queryClient.invalidateQueries({ queryKey: departmentKeys.all });
    },
  });
}

export function useCreateUser() {
  return useUserMutation(async (input: CreateUserInput) => (await api.post<UserDetail>('/users', input)).data);
}

export function useUpdateUser(id: number) {
  return useUserMutation(async (input: UserProfileInput) => (await api.put<UserDetail>(`/users/${id}`, input)).data);
}

export function useUpdateAccess(id: number) {
  return useUserMutation(async (input: AccessInput) => (await api.put<UserDetail>(`/users/${id}/access`, input)).data);
}

export function useSetUserStatus(id: number) {
  return useUserMutation(
    async (status: UserStatus) => (await api.post<UserDetail>(`/users/${id}/${status === 'ACTIVE' ? 'enable' : 'disable'}`)).data,
  );
}

export function useResetPassword(id: number) {
  return useMutation({
    mutationFn: async (newPassword: string) => {
      await api.post(`/users/${id}/reset-password`, { newPassword });
    },
  });
}
