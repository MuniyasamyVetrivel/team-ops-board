import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import type { UserSummary } from '@/lib/api/types';

export type SettingValueType = 'INTEGER' | 'DECIMAL';

/** Mirrors SettingDtos.SettingItem. {@code value} is the stored canonical text ("14", "4.5"). */
export interface SettingItem {
  key: string;
  group: string;
  label: string;
  description: string | null;
  valueType: SettingValueType;
  value: string;
  unit: string;
  min: number;
  max: number;
  updatedBy: UserSummary | null;
  updatedAt: string;
  version: number;
}

export const settingKeys = {
  all: ['admin-settings'] as const,
};

export function useSettings() {
  return useQuery({
    queryKey: settingKeys.all,
    queryFn: async () => (await api.get<SettingItem[]>('/admin/settings')).data,
  });
}

export function useUpdateSetting() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ key, version, value }: { key: string; version: number; value: string }) =>
      (await api.put<SettingItem>(`/admin/settings/${encodeURIComponent(key)}`, { version, value })).data,
    onSuccess: (saved) => {
      queryClient.setQueryData<SettingItem[]>(settingKeys.all, (list) => list?.map((s) => (s.key === saved.key ? saved : s)));
      // Workload, SLA and marketing figures read these settings.
      void queryClient.invalidateQueries({ queryKey: ['workload'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}
