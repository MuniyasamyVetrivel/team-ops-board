import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { cleanParams, type PageResponse, type UserSummary } from '@/lib/api/types';

import type { HistoryRecordType } from '@/features/history/api';

/**
 * Mirrors RecycleBinDtos.Item. {@code purgeOn} is the business day the item is deleted for good automatically and
 * {@code daysUntilPurge} how many days away that is, counted by the server's business calendar (0: tonight).
 * {@code confirmationText} is what a permanent delete must be confirmed with (the item's name).
 */
export interface RecycleBinItem {
  id: number;
  entityType: string;
  entityTypeLabel: string;
  entityId: number;
  label: string | null;
  module: string;
  moduleLabel: string;
  department: { id: number; name: string } | null;
  deletedAt: string;
  deletedBy: UserSummary | null;
  purgeOn: string;
  daysUntilPurge: number;
  confirmationText: string;
}

/** Mirrors RecycleBinDtos.Catalog: what the filters offer, and how long items are kept. */
export interface RecycleBinCatalog {
  modules: { key: string; label: string }[];
  types: { type: string; label: string; module: string | null }[];
  deletedBy: UserSummary[];
  retentionDays: number;
}

/** Mirrors RecycleBinDtos.RestoreCheck: a clash that blocks the restore, and warnings it survives. */
export interface RestoreCheck {
  canRestore: boolean;
  blockCode: string | null;
  blockMessage: string | null;
  warnings: string[];
}

/** Mirrors RecycleBinDtos.Restored. */
export interface Restored {
  entityType: string;
  entityId: number;
  label: string | null;
}

/** Filters; {@code from}/{@code to} are business days (inclusive) of the deletion. */
export interface RecycleBinQuery {
  module?: string;
  entityType?: string;
  deletedById?: number;
  from?: string;
  to?: string;
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const recycleBinKeys = {
  all: ['recycle-bin'] as const,
  list: (query: RecycleBinQuery) => [...recycleBinKeys.all, 'list', query] as const,
  catalog: () => [...recycleBinKeys.all, 'catalog'] as const,
  restoreCheck: (id: number) => [...recycleBinKeys.all, 'restore-check', id] as const,
};

export function useRecycleBin(query: RecycleBinQuery) {
  return useQuery({
    queryKey: recycleBinKeys.list(query),
    queryFn: async () => (await api.get<PageResponse<RecycleBinItem>>('/recycle-bin', { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
  });
}

export function useRecycleBinCatalog() {
  return useQuery({
    queryKey: recycleBinKeys.catalog(),
    queryFn: async () => (await api.get<RecycleBinCatalog>('/recycle-bin/catalog')).data,
  });
}

/** Checked each time the restore dialog opens: another record may have taken a value since. */
export function useRestoreCheck(id: number | null) {
  return useQuery({
    queryKey: recycleBinKeys.restoreCheck(id ?? 0),
    queryFn: async () => (await api.get<RestoreCheck>(`/recycle-bin/${id}/restore-check`)).data,
    enabled: id !== null,
    staleTime: 0,
    gcTime: 0,
  });
}

/**
 * A restored or purged item changes the bin and whatever list it belongs to, so everything is refetched (the bin
 * spans every module).
 */
function useBinMutation<TInput, TResult>(mutationFn: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries(),
  });
}

export function useRestoreItem() {
  return useBinMutation(async (id: number) => (await api.post<Restored>(`/recycle-bin/${id}/restore`)).data);
}

export function usePurgeItem() {
  return useBinMutation(async ({ id, confirm }: { id: number; confirm: string }) => {
    await api.post(`/recycle-bin/${id}/purge`, { confirm });
  });
}

/** Undoes a delete just made (the person who deleted it, within two minutes; see the "Undo" toast). */
export async function undoDelete(entityType: HistoryRecordType, entityId: number): Promise<Restored> {
  return (await api.post<Restored>('/recycle-bin/undo', { entityType, entityId })).data;
}
