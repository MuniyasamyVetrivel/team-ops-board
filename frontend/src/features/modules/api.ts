import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { DepartmentSummary } from '@/features/auth/types';
import { api } from '@/lib/api/client';

/** Mirrors ModuleDtos.Tool: a department tool the signed-in user may open. */
export interface DepartmentTool {
  key: string;
  name: string;
  /** Lucide icon name (see module-icons.ts). */
  icon: string;
  description: string;
  canEdit: boolean;
}

/** Mirrors ModuleDtos.ModuleSettings: a module and the departments it is switched on for. */
export interface ModuleSettings {
  key: string;
  name: string;
  icon: string;
  description: string;
  viewPermission: string;
  editPermission: string;
  departments: DepartmentSummary[];
}

export const moduleKeys = {
  tools: ['department-tools'] as const,
  settings: ['admin-modules'] as const,
};

/** The sidebar's "Department tools": switched on for the user's departments and permitted to them. */
export function useDepartmentTools(enabled = true) {
  return useQuery({
    queryKey: moduleKeys.tools,
    queryFn: async () => (await api.get<DepartmentTool[]>('/department-tools')).data,
    enabled,
    staleTime: 5 * 60_000,
  });
}

/** Every module with its departments (MODULE_MANAGE). */
export function useModuleSettings() {
  return useQuery({
    queryKey: moduleKeys.settings,
    queryFn: async () => (await api.get<ModuleSettings[]>('/admin/modules')).data,
  });
}

export interface ModuleSwitch {
  key: string;
  departmentId: number;
  enabled: boolean;
}

/** Switches a module on or off for one department. */
export function useSetModuleSwitch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ key, departmentId, enabled }: ModuleSwitch) =>
      (await api.put<ModuleSettings>(`/admin/modules/${encodeURIComponent(key)}/departments/${departmentId}`, { enabled })).data,
    onSuccess: (saved) => {
      queryClient.setQueryData<ModuleSettings[]>(moduleKeys.settings, (list) => list?.map((m) => (m.key === saved.key ? saved : m)));
      // The editor's own sidebar may gain or lose the tool.
      void queryClient.invalidateQueries({ queryKey: moduleKeys.tools });
    },
  });
}
