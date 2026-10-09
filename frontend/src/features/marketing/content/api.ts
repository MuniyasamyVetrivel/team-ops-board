import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

import type { MarketingPeriod } from '../api';
import type { TargetItem } from '../targets/api';

export type ContentStatus = 'IDEA' | 'PLANNED' | 'IN_PROGRESS' | 'DRAFT' | 'PUBLISHED' | 'UPDATED';
export type ContentType = 'BLOG' | 'CASE_STUDY' | 'WHITEPAPER' | 'LANDING_PAGE' | 'OTHER';
export type DateField = 'PLANNED' | 'PUBLISHED';

/** The pipeline in stage order. */
export const CONTENT_STATUSES: ContentStatus[] = ['IDEA', 'PLANNED', 'IN_PROGRESS', 'DRAFT', 'PUBLISHED', 'UPDATED'];
export const CONTENT_TYPES: ContentType[] = ['BLOG', 'CASE_STUDY', 'WHITEPAPER', 'LANDING_PAGE', 'OTHER'];

/** Mirrors ContentDtos.ContentItemDto. */
export interface ContentItem {
  id: number;
  title: string;
  url: string | null;
  contentType: ContentType;
  status: ContentStatus;
  author: UserSummary | null;
  owner: UserSummary | null;
  plannedDate: string | null;
  publicationDate: string | null;
  refreshedDate: string | null;
  targetKeyword: { id: number; keyword: string } | null;
  targetKeywordText: string | null;
  targetPage: { id: number; title: string; url: string } | null;
  organicTraffic: number | null;
  ctaClicks: number | null;
  notes: string | null;
  /** All-time leads naming this item. */
  leads: number;
  attachments: number;
  /** Live and published in a month closed for the viewer: it stays published there. */
  publicationLocked: boolean;
  refreshLocked: boolean;
  createdBy: UserSummary | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors ContentDtos.Attachment. */
export interface ContentAttachment {
  fileId: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  addedBy: UserSummary | null;
  addedAt: string;
}

/** Mirrors ContentDtos.MonthFigures. */
export interface ContentMonth {
  period: MarketingPeriod;
  plannedBlogs: number;
  publishedBlogs: number;
  publishedAll: number;
  refreshed: number;
  /** Leads dated in the month that name a content item. */
  leads: number;
}

/** Mirrors ContentDtos.BlogTarget: remaining = max(target − published, 0). */
export interface BlogTarget {
  targetValue: number;
  remaining: number;
  target: TargetItem;
}

export interface TopContent {
  id: number;
  title: string;
  contentType: ContentType;
  url: string | null;
  organicTraffic: number | null;
  ctaClicks: number | null;
  leads: number;
}

/** Mirrors ContentDtos.ContentSummary. Targets are null unless `targetsVisible`. */
export interface ContentSummary {
  current: ContentMonth;
  comparison: ContentMonth;
  targetsVisible: boolean;
  blogTarget: BlogTarget | null;
  blogLeadsTarget: TargetItem | null;
  publishedByType: { contentType: ContentType; published: number }[];
  pipeline: { status: ContentStatus; items: number }[];
  topContent: TopContent[];
}

/** Mirrors ContentDtos.TrendMonth: target figures are null without a target, or for a planned month. */
export interface ContentTrendMonth {
  figures: ContentMonth;
  targetValue: number | null;
  remaining: number | null;
  achievementPct: number | null;
}

export interface ContentQuery {
  search?: string;
  status?: ContentStatus[];
  contentType?: ContentType[];
  ownerId?: number;
  authorId?: number;
  month?: number;
  year?: number;
  dateField?: DateField;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ContentSummaryQuery {
  month: number;
  year: number;
  compareMonth?: number;
  compareYear?: number;
  ownerId?: number;
}

export interface ContentTrendQuery {
  month: number;
  year: number;
  months: number;
  ownerId?: number;
}

/** Mirrors ContentDtos.SaveContent; `version` only when updating. */
export interface SaveContentInput {
  version?: number;
  title: string;
  url: string | null;
  contentType: ContentType;
  status: ContentStatus;
  authorId: number | null;
  ownerId: number | null;
  plannedDate: string | null;
  publicationDate: string | null;
  refreshedDate: string | null;
  targetKeywordId: number | null;
  targetKeywordText: string | null;
  targetPageId: number | null;
  organicTraffic: number | null;
  ctaClicks: number | null;
  notes: string | null;
}

export const contentKeys = {
  all: ['marketing', 'content'] as const,
  list: (query: ContentQuery) => [...contentKeys.all, 'list', query] as const,
  detail: (id: number) => [...contentKeys.all, 'detail', id] as const,
  attachments: (id: number) => [...contentKeys.all, 'attachments', id] as const,
  summary: (query: ContentSummaryQuery) => [...contentKeys.all, 'summary', query] as const,
  trend: (query: ContentTrendQuery) => [...contentKeys.all, 'trend', query] as const,
};

const get = <T,>(url: string, params?: object) =>
  api.get<T>(url, params ? { params: cleanParams(params), paramsSerializer: serializeParams } : undefined).then((response) => response.data);

export function useContentItems(query: ContentQuery) {
  return useQuery({ queryKey: contentKeys.list(query), queryFn: () => get<PageResponse<ContentItem>>('/marketing/content', query), placeholderData: keepPreviousData });
}

export function useContentItem(id: number | null) {
  return useQuery({ queryKey: contentKeys.detail(id ?? 0), queryFn: () => get<ContentItem>(`/marketing/content/${id}`), enabled: id !== null });
}

export function useContentSummary(query: ContentSummaryQuery) {
  return useQuery({ queryKey: contentKeys.summary(query), queryFn: () => get<ContentSummary>('/marketing/content/summary', query), placeholderData: keepPreviousData });
}

export function useContentTrend(query: ContentTrendQuery) {
  return useQuery({
    queryKey: contentKeys.trend(query),
    queryFn: () => get<{ targetsVisible: boolean; months: ContentTrendMonth[] }>('/marketing/content/trend', query),
    placeholderData: keepPreviousData,
  });
}

/**
 * Content moves the list, the summary, the history and the Blogs Published target; a returned item replaces the
 * cached detail.
 */
function useContentMutation<V>(mutationFn: (variables: V) => Promise<ContentItem | number>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (result) => {
      if (typeof result === 'number') queryClient.removeQueries({ queryKey: contentKeys.detail(result) });
      else queryClient.setQueryData(contentKeys.detail(result.id), result);
      void queryClient.invalidateQueries({ queryKey: [...contentKeys.all, 'list'] });
      void queryClient.invalidateQueries({ queryKey: [...contentKeys.all, 'summary'] });
      void queryClient.invalidateQueries({ queryKey: [...contentKeys.all, 'trend'] });
      void queryClient.invalidateQueries({ queryKey: ['marketing', 'targets'] });
    },
  });
}

export function useCreateContent() {
  return useContentMutation((input: SaveContentInput) => api.post<ContentItem>('/marketing/content', input).then((r) => r.data));
}

export function useUpdateContent(id: number) {
  return useContentMutation((input: SaveContentInput) => api.put<ContentItem>(`/marketing/content/${id}`, input).then((r) => r.data));
}

/** Publishing dates the item `date` (today when omitted); earlier stages clear the dates. */
export function useChangeContentStatus(id: number) {
  return useContentMutation((input: { version: number; status: ContentStatus; date?: string }) =>
    api.put<ContentItem>(`/marketing/content/${id}/status`, input).then((r) => r.data),
  );
}

export function useDeleteContent() {
  return useContentMutation((id: number) => api.delete(`/marketing/content/${id}`).then(() => id));
}

export function useContentAttachments(id: number) {
  return useQuery({ queryKey: contentKeys.attachments(id), queryFn: () => get<ContentAttachment[]>(`/marketing/content/${id}/attachments`) });
}

/** Upload and remove return the item's files; the item's file count is refreshed with them. */
export function useContentAttachmentMutations(id: number) {
  const queryClient = useQueryClient();
  const onSuccess = (files: ContentAttachment[]) => {
    queryClient.setQueryData(contentKeys.attachments(id), files);
    void queryClient.invalidateQueries({ queryKey: contentKeys.detail(id) });
    void queryClient.invalidateQueries({ queryKey: [...contentKeys.all, 'list'] });
  };
  return {
    upload: useMutation({
      mutationFn: async (file: File) => {
        const form = new FormData();
        form.append('file', file);
        return (await api.post<ContentAttachment[]>(`/marketing/content/${id}/attachments`, form)).data;
      },
      onSuccess,
    }),
    remove: useMutation({
      mutationFn: async (fileId: number) => (await api.delete<ContentAttachment[]>(`/marketing/content/${id}/attachments/${fileId}`)).data,
      onSuccess,
    }),
  };
}

export function downloadContentAttachment(id: number, fileId: number, fileName: string): Promise<void> {
  return downloadFile(`/marketing/content/${id}/attachments/${fileId}`, fileName);
}

/** Downloads the content table for the same filters as on screen. */
export function exportContent(query: Omit<ContentQuery, 'page' | 'size' | 'sort'>): Promise<void> {
  const search = serializeParams(cleanParams(query));
  const name = query.month ? `content-${query.year}-${String(query.month).padStart(2, '0')}.csv` : 'content.csv';
  return downloadFile(`/marketing/content/export${search ? `?${search}` : ''}`, name);
}
