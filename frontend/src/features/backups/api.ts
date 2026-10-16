import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { saveBlob } from '@/lib/api/download';
import type { PageResponse } from '@/lib/api/types';

export type BackupStatusCode = 'RUNNING' | 'SUCCESS' | 'FAILED';
export type BackupTrigger = 'SCHEDULED' | 'MANUAL';
export type BackupCopyRole = 'PRIMARY' | 'SECONDARY';

/** Mirrors BackupDtos.Copy: one stored copy of a backup file. */
export interface BackupCopy {
  target: string;
  status: BackupStatusCode;
  errorMessage: string | null;
  removedAt: string | null;
}

/**
 * Mirrors BackupDtos.Run. {@code status}, {@code target}, {@code errorMessage} and {@code removedAt} describe the first
 * copy; {@code secondary} the second copy (null when no second target was configured for that run).
 */
export interface BackupRun {
  id: number;
  trigger: BackupTrigger;
  status: BackupStatusCode;
  startedAt: string;
  finishedAt: string | null;
  fileName: string | null;
  sizeBytes: number | null;
  checksum: string | null;
  target: string;
  uploadFiles: number | null;
  errorMessage: string | null;
  requestedById: number | null;
  removedAt: string | null;
  secondary: BackupCopy | null;
}

/** Mirrors BackupDtos.Problem: {@code NO_BACKUP}, {@code STALE}, {@code LAST_FAILED}, {@code NOT_CONFIGURED}, {@code SECONDARY_*}. */
export interface BackupProblem {
  code: string;
  message: string;
}

/** Mirrors BackupDtos.CopyStatus: the health of one target. */
export interface BackupCopyStatus {
  copy: BackupCopyRole;
  target: string;
  healthy: boolean;
  problems: BackupProblem[];
  lastSuccessAt: string | null;
  lastStatus: BackupStatusCode | null;
  lastError: string | null;
}

/** Mirrors BackupDtos.Status. {@code nextRunAt} is null when the nightly schedule is off. */
export interface BackupStatus {
  healthy: boolean;
  problems: BackupProblem[];
  copies: BackupCopyStatus[];
  lastSuccess: BackupRun | null;
  lastRun: BackupRun | null;
  running: boolean;
  scheduleEnabled: boolean;
  schedule: string;
  timeZone: string;
  nextRunAt: string | null;
  target: string;
  secondaryTarget: string | null;
  keepDaily: number;
  keepMonthly: number;
  maxAgeHours: number;
}

export const backupKeys = {
  all: ['backups'] as const,
  status: () => [...backupKeys.all, 'status'] as const,
  history: (page: number, size: number) => [...backupKeys.all, 'history', page, size] as const,
};

/** Polls every 5 seconds while a backup runs, otherwise every minute (the banner polls more slowly; see below). */
export function useBackupStatus(options: { enabled?: boolean; idleInterval?: number } = {}) {
  const { enabled = true, idleInterval = 60_000 } = options;
  return useQuery({
    queryKey: backupKeys.status(),
    queryFn: async () => (await api.get<BackupStatus>('/admin/backups/status')).data,
    enabled,
    refetchInterval: (query) => (query.state.data?.running ? 5_000 : idleInterval),
  });
}

export function useBackupHistory(page: number, size: number, running: boolean) {
  return useQuery({
    queryKey: backupKeys.history(page, size),
    queryFn: async () =>
      (await api.get<PageResponse<BackupRun>>('/admin/backups', { params: { page, size, sort: 'started,desc' } })).data,
    placeholderData: keepPreviousData,
    refetchInterval: running ? 5_000 : false,
  });
}

/** "Run backup now": answers at once with the RUNNING run; status and history then poll until it finishes. */
export function useRunBackup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => (await api.post<BackupRun>('/admin/backups/run')).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: backupKeys.all }),
  });
}

/** The file name from {@code Content-Disposition}, or a dated fallback. */
export function attachmentName(header: unknown, fallback: string): string {
  if (typeof header !== 'string') return fallback;
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (utf8) return decodeURIComponent(utf8[1].trim());
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain ? plain[1].trim() : fallback;
}

/**
 * "Export all data": streams the ZIP through the API client (so the bearer token goes with it), reporting the bytes
 * received so far (the server streams, so the total size is not known in advance), then saves it. Cancel with
 * {@code signal}.
 */
export async function exportAllData(onProgress: (loadedBytes: number) => void, signal?: AbortSignal): Promise<{ fileName: string; sizeBytes: number }> {
  const response = await api.get<Blob>('/admin/data-export', {
    responseType: 'blob',
    signal,
    timeout: 0,
    onDownloadProgress: (event) => onProgress(event.loaded),
  });
  const fileName = attachmentName(response.headers['content-disposition'], `team-ops-export-${new Date().toISOString().slice(0, 10)}.zip`);
  saveBlob(response.data, fileName);
  return { fileName, sizeBytes: response.data.size };
}
