import { useInfiniteQuery } from '@tanstack/react-query';

import type { AuditLogItem } from '@/features/admin/audit/api';
import { api } from '@/lib/api/client';
import type { PageResponse } from '@/lib/api/types';

/**
 * Record types with a History tab, as the backend names them (RecordTypes / @AuditType). The server checks that the
 * viewer may open the record and answers 404 otherwise.
 */
export type HistoryRecordType =
  | 'TASK'
  | 'TICKET'
  | 'PROJECT'
  | 'APPROVAL'
  | 'APPROVAL_TYPE'
  | 'DEPARTMENT'
  | 'USER'
  | 'DOCUMENT'
  | 'ARTICLE'
  | 'ANNOUNCEMENT'
  | 'CALENDAR_EVENT'
  | 'EMPLOYEE'
  | 'IMPORTANT_DAY'
  | 'MARKETING_EVENT'
  | 'SEO_PAGE'
  | 'SEO_KEYWORD'
  | 'MARKETING_TARGET'
  | 'MARKETING_TARGET_TYPE'
  | 'MARKETING_ACTIVITY'
  | 'EMAIL_CAMPAIGN'
  | 'PAID_CAMPAIGN'
  | 'MARKETING_LEAD'
  | 'BACKLINK'
  | 'CONTENT_ITEM'
  | 'SETTING'
  | 'SLA_POLICY';

export const HISTORY_PAGE_SIZE = 50;

export const historyKeys = {
  all: ['history'] as const,
  record: (type: HistoryRecordType, id: number) => [...historyKeys.all, type, id] as const,
};

/**
 * A record's timeline, newest first: its own audit entries and those of its children (comments, checklist items,
 * attachments, steps). Always refetched when the tab opens, so it shows edits made a moment ago.
 */
export function useRecordHistory(type: HistoryRecordType, id: number) {
  return useInfiniteQuery({
    queryKey: historyKeys.record(type, id),
    queryFn: async ({ pageParam }) =>
      (await api.get<PageResponse<AuditLogItem>>(`/history/${type}/${id}`, { params: { page: pageParam, size: HISTORY_PAGE_SIZE } })).data,
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    staleTime: 0,
  });
}
