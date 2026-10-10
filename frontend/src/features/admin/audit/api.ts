import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

/** Mirrors AuditLogDtos.AuditLogItem. {@code details} is the stored JSON document, parsed; its shape depends on the action. */
export interface AuditLogItem {
  id: number;
  action: string;
  actionLabel: string;
  module: string | null;
  moduleLabel: string | null;
  actor: UserSummary | null;
  entityType: string | null;
  entityId: number | null;
  details: unknown;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
}

export interface ModuleInfo {
  key: string;
  label: string;
}

export interface ActionInfo {
  action: string;
  label: string;
  module: string;
  events: string[];
}

/** One of the audit events brief section 63 requires, and the actions that record it. */
export interface EventInfo {
  key: string;
  label: string;
  actions: string[];
}

/** Mirrors AuditLogDtos.Catalog: everything the filters offer. */
export interface AuditCatalog {
  modules: ModuleInfo[];
  actions: ActionInfo[];
  events: EventInfo[];
  entityTypes: string[];
  actors: UserSummary[];
}

/** Filters; {@code from}/{@code to} are business days (inclusive). */
export interface AuditQuery {
  search?: string;
  action?: string[];
  module?: string;
  event?: string;
  actorId?: number;
  entityType?: string;
  entityId?: number;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const auditKeys = {
  all: ['audit-logs'] as const,
  list: (query: AuditQuery) => [...auditKeys.all, 'list', query] as const,
  catalog: () => [...auditKeys.all, 'catalog'] as const,
};

export function useAuditLogs(query: AuditQuery) {
  return useQuery({
    queryKey: auditKeys.list(query),
    queryFn: async () =>
      (await api.get<PageResponse<AuditLogItem>>('/admin/audit-logs', { params: cleanParams(query), paramsSerializer: serializeParams })).data,
    placeholderData: keepPreviousData,
  });
}

export function useAuditCatalog() {
  return useQuery({
    queryKey: auditKeys.catalog(),
    queryFn: async () => (await api.get<AuditCatalog>('/admin/audit-logs/catalog')).data,
    staleTime: 5 * 60_000,
  });
}

/** Downloads the matching entries as CSV (the server caps it at 5,000 rows). */
export function exportAuditLogs(query: AuditQuery): Promise<void> {
  const search = serializeParams(cleanParams({ ...query, page: undefined, size: undefined, format: 'CSV' }));
  return downloadFile(`/admin/audit-logs/export?${search}`, 'audit-log.csv');
}
