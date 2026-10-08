import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

import type { MarketingPeriod, RankingChange, RankingStatus } from '../api';

export type PageType = 'SERVICE' | 'INDUSTRY' | 'LOCATION' | 'BLOG' | 'LANDING_PAGE' | 'PRODUCT' | 'OTHER';
export type PageStatus = 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
export type KeywordStatus = 'ACTIVE' | 'PAUSED' | 'ARCHIVED';
export type SearchEngine = 'GOOGLE' | 'BING';
export type Device = 'DESKTOP' | 'MOBILE';

export const PAGE_TYPES: PageType[] = ['SERVICE', 'INDUSTRY', 'LOCATION', 'BLOG', 'LANDING_PAGE', 'PRODUCT', 'OTHER'];
export const PAGE_STATUSES: PageStatus[] = ['ACTIVE', 'INACTIVE', 'ARCHIVED'];
export const KEYWORD_STATUSES: KeywordStatus[] = ['ACTIVE', 'PAUSED', 'ARCHIVED'];
export const SEARCH_ENGINES: SearchEngine[] = ['GOOGLE', 'BING'];
export const DEVICES: Device[] = ['DESKTOP', 'MOBILE'];

interface DepartmentRef {
  id: number;
  name: string;
  code: string;
}

/** Mirrors SeoStats: figures for one month, computed from the ranking history. */
export interface SeoStats {
  totalKeywords: number;
  top3: number;
  top10: number;
  /** Positions 11–100. */
  ranking: number;
  /** No position: recorded as Not Ranked, or nothing recorded for the month. */
  notRanked: number;
  /** Keywords without a ranking for the month (included in notRanked). */
  notRecorded: number;
  improved: number;
  declined: number;
  unchanged: number;
  /** null when nothing ranks ("—"). */
  averagePosition: number | null;
}

/** Mirrors KeywordStanding: a keyword's ranking for the selected month. */
export interface KeywordStanding {
  /** Whether a ranking was recorded for the month (a recorded Not Ranked is still recorded). */
  recorded: boolean;
  position: number | null;
  status: RankingStatus;
  previousRecorded: boolean;
  previousPosition: number | null;
  /** null when the month has no ranking. */
  change: RankingChange | null;
}

/** Mirrors SeoDtos.PageRef. */
export interface PageRef {
  id: number;
  title: string;
  url: string;
  status: PageStatus;
}

/** Mirrors SeoDtos.PageListItem. */
export interface SeoPageListItem {
  id: number;
  url: string;
  title: string;
  pageType: PageType;
  primaryKeyword: string | null;
  department: DepartmentRef;
  owner: UserSummary | null;
  status: PageStatus;
  stats: SeoStats;
  updatedAt: string;
}

/** Mirrors SeoDtos.PageDetail. */
export interface SeoPageDetail {
  id: number;
  url: string;
  title: string;
  pageType: PageType;
  primaryKeyword: string | null;
  department: DepartmentRef;
  owner: UserSummary | null;
  status: PageStatus;
  period: MarketingPeriod;
  stats: SeoStats;
  previousPeriod: MarketingPeriod;
  previousStats: SeoStats;
  keywordCount: number;
  version: number;
  createdAt: string;
  updatedAt: string;
  permissions: { canEdit: boolean };
}

/** Mirrors SeoDtos.KeywordItem. */
export interface KeywordItem {
  id: number;
  keyword: string;
  page: PageRef;
  searchEngine: SearchEngine;
  location: string;
  device: Device;
  targetPosition: number | null;
  searchVolume: number | null;
  keywordDifficulty: number | null;
  owner: UserSummary | null;
  status: KeywordStatus;
  ranking: KeywordStanding;
  lastRankedAt: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

interface PeriodQuery {
  month?: number;
  year?: number;
}

export interface SeoPageQuery extends PeriodQuery {
  search?: string;
  type?: PageType[];
  status?: PageStatus[];
  ownerId?: number;
  departmentId?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface KeywordQuery extends PeriodQuery {
  search?: string;
  pageId?: number;
  ownerId?: number;
  status?: KeywordStatus[];
  device?: Device;
  page?: number;
  size?: number;
  sort?: string;
}

export interface CreatePageInput {
  url: string;
  title: string;
  pageType: PageType;
  primaryKeyword: string | null;
  departmentId: number;
  ownerId: number | null;
}

export interface UpdatePageInput extends CreatePageInput {
  version: number;
  status: PageStatus;
}

export interface CreateKeywordInput {
  pageId: number;
  keyword: string;
  searchEngine: SearchEngine;
  location: string;
  device: Device;
  targetPosition: number | null;
  searchVolume: number | null;
  keywordDifficulty: number | null;
  ownerId: number | null;
}

export interface UpdateKeywordInput extends CreateKeywordInput {
  version: number;
  status: KeywordStatus;
}

export const seoKeys = {
  all: ['marketing', 'seo'] as const,
  pages: () => [...seoKeys.all, 'pages'] as const,
  pageList: (query: SeoPageQuery) => [...seoKeys.pages(), 'list', query] as const,
  pageOptions: () => [...seoKeys.pages(), 'options'] as const,
  pageDetail: (id: number, period?: PeriodQuery) => [...seoKeys.pages(), 'detail', id, period ?? {}] as const,
  keywords: () => [...seoKeys.all, 'keywords'] as const,
  keywordList: (query: KeywordQuery) => [...seoKeys.keywords(), 'list', query] as const,
};

const get = <T,>(url: string, params?: object) =>
  api.get<T>(url, params ? { params: cleanParams(params), paramsSerializer: serializeParams } : undefined).then((response) => response.data);

export function useSeoPages(query: SeoPageQuery, enabled = true) {
  return useQuery({
    queryKey: seoKeys.pageList(query),
    queryFn: () => get<PageResponse<SeoPageListItem>>('/marketing/pages', query),
    placeholderData: keepPreviousData,
    enabled,
  });
}

/** Pages that can take keywords (not archived), for pickers and filters. */
export function useSeoPageOptions() {
  return useQuery({ queryKey: seoKeys.pageOptions(), queryFn: () => get<PageRef[]>('/marketing/pages/options'), staleTime: 60_000 });
}

export function useSeoPage(id: number, period: PeriodQuery | null) {
  return useQuery({
    queryKey: seoKeys.pageDetail(id, period ?? undefined),
    queryFn: () => get<SeoPageDetail>(`/marketing/pages/${id}`, period ?? {}),
    enabled: Number.isFinite(id) && period !== null,
    placeholderData: keepPreviousData,
  });
}

export function useSeoKeywords(query: KeywordQuery, enabled = true) {
  return useQuery({
    queryKey: seoKeys.keywordList(query),
    queryFn: () => get<PageResponse<KeywordItem>>('/marketing/keywords', query),
    placeholderData: keepPreviousData,
    enabled,
  });
}

/**
 * Page and keyword changes move page statistics, keyword counts and pickers together, so every SEO query is
 * refreshed. A saved page detail is cached for the month it was returned for.
 */
function useSeoMutation<V, R>(mutationFn: (variables: V) => Promise<R>, onSaved?: (result: R, queryClient: ReturnType<typeof useQueryClient>) => void) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (result) => {
      onSaved?.(result, queryClient);
      void queryClient.invalidateQueries({ queryKey: seoKeys.all });
    },
  });
}

const cachePage = (page: SeoPageDetail, queryClient: ReturnType<typeof useQueryClient>) =>
  queryClient.setQueryData(seoKeys.pageDetail(page.id, { month: page.period.month, year: page.period.year }), page);

export function useCreateSeoPage() {
  return useSeoMutation((input: CreatePageInput) => api.post<SeoPageDetail>('/marketing/pages', input).then((r) => r.data), cachePage);
}

export function useUpdateSeoPage(id: number) {
  return useSeoMutation((input: UpdatePageInput) => api.put<SeoPageDetail>(`/marketing/pages/${id}`, input).then((r) => r.data), cachePage);
}

export function useDeleteSeoPage() {
  return useSeoMutation((id: number) => api.delete(`/marketing/pages/${id}`).then(() => id));
}

export function useCreateKeyword() {
  return useSeoMutation((input: CreateKeywordInput) => api.post<KeywordItem>('/marketing/keywords', input).then((r) => r.data));
}

export function useUpdateKeyword(id: number) {
  return useSeoMutation((input: UpdateKeywordInput) => api.put<KeywordItem>(`/marketing/keywords/${id}`, input).then((r) => r.data));
}

export function useDeleteKeyword() {
  return useSeoMutation((id: number) => api.delete(`/marketing/keywords/${id}`).then(() => id));
}
