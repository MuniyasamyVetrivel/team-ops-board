import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

import type { MarketingPeriod } from '../api';
import type { TargetItem } from '../targets/api';

export type BacklinkStatus = 'PROSPECTED' | 'SUBMITTED' | 'APPROVED' | 'LIVE' | 'REJECTED' | 'LOST';
export type BacklinkType = 'GUEST_POST' | 'DIRECTORY' | 'BUSINESS_LISTING' | 'PROFILE' | 'FORUM' | 'SOCIAL_BOOKMARK' | 'PRESS_RELEASE' | 'RESOURCE_PAGE' | 'BLOG_COMMENT' | 'OTHER';
/** The dated stages; a month filter applies to one of them (or any). */
export type Stage = 'SUBMITTED' | 'APPROVED' | 'LIVE' | 'REJECTED' | 'LOST';
export type DateField = 'submittedDate' | 'approvedDate' | 'liveDate' | 'rejectedDate' | 'lostDate';

export const BACKLINK_STATUSES: BacklinkStatus[] = ['PROSPECTED', 'SUBMITTED', 'APPROVED', 'LIVE', 'REJECTED', 'LOST'];
export const BACKLINK_TYPES: BacklinkType[] = ['GUEST_POST', 'DIRECTORY', 'BUSINESS_LISTING', 'PROFILE', 'FORUM', 'SOCIAL_BOOKMARK', 'PRESS_RELEASE', 'RESOURCE_PAGE', 'BLOG_COMMENT', 'OTHER'];
export const STAGES: Stage[] = ['SUBMITTED', 'APPROVED', 'LIVE', 'REJECTED', 'LOST'];

/** The import type of BacklinkCsvImporter. */
export const BACKLINK_IMPORT_TYPE = 'backlinks';

/** Mirrors BacklinkDtos.BacklinkItem. `lockedDates`: stage dates in months closed for the viewer. */
export interface Backlink {
  id: number;
  code: string;
  targetPage: { id: number; title: string; url: string } | null;
  targetUrl: string;
  referringDomain: string;
  linkUrl: string | null;
  anchorText: string | null;
  linkType: BacklinkType;
  status: BacklinkStatus;
  submittedDate: string | null;
  approvedDate: string | null;
  liveDate: string | null;
  rejectedDate: string | null;
  lostDate: string | null;
  owner: UserSummary | null;
  domainAuthority: number | null;
  notes: string | null;
  provider: 'MANUAL' | 'CSV';
  createdBy: UserSummary | null;
  lockedDates: DateField[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors BacklinkDtos.MonthActivity: each stage counts in the month of its own date. */
export interface MonthActivity {
  period: MarketingPeriod;
  submitted: number;
  approved: number;
  live: number;
  rejected: number;
  lost: number;
}

/** Mirrors BacklinkDtos.MonthTarget: remaining = max(target − submitted, 0); the target's own actual counts live links. */
export interface MonthTarget {
  targetValue: number;
  remaining: number;
  target: TargetItem;
}

/** Mirrors BacklinkDtos.BacklinkSummary. The target is null unless `targetsVisible`. */
export interface BacklinkSummary {
  current: MonthActivity;
  comparison: MonthActivity;
  targetsVisible: boolean;
  target: MonthTarget | null;
  liveByType: { linkType: BacklinkType; live: number }[];
  byOwner: { owner: UserSummary | null; submitted: number; approved: number; live: number }[];
  pipeline: { status: BacklinkStatus; backlinks: number }[];
}

/** Mirrors BacklinkDtos.TrendMonth: target figures are null without a target or for a planned month. */
export interface BacklinkTrendMonth {
  activity: MonthActivity;
  targetValue: number | null;
  remaining: number | null;
  liveAchievementPct: number | null;
}

export interface BacklinkQuery {
  search?: string;
  status?: BacklinkStatus[];
  linkType?: BacklinkType[];
  ownerId?: number;
  targetPageId?: number;
  month?: number;
  year?: number;
  stage?: Stage;
  page?: number;
  size?: number;
  sort?: string;
}

export interface BacklinkSummaryQuery {
  month: number;
  year: number;
  compareMonth?: number;
  compareYear?: number;
  ownerId?: number;
}

export interface BacklinkTrendQuery {
  month: number;
  year: number;
  months: number;
  ownerId?: number;
}

/** Mirrors BacklinkDtos.SaveBacklink; `version` only when updating. */
export interface SaveBacklinkInput {
  version?: number;
  targetPageId: number | null;
  targetUrl: string | null;
  referringDomain: string | null;
  linkUrl: string | null;
  anchorText: string | null;
  linkType: BacklinkType;
  status: BacklinkStatus;
  submittedDate: string | null;
  approvedDate: string | null;
  liveDate: string | null;
  rejectedDate: string | null;
  lostDate: string | null;
  ownerId: number | null;
  domainAuthority: number | null;
  notes: string | null;
}

export const backlinkKeys = {
  all: ['marketing', 'backlinks'] as const,
  list: (query: BacklinkQuery) => [...backlinkKeys.all, 'list', query] as const,
  detail: (id: number) => [...backlinkKeys.all, 'detail', id] as const,
  summary: (query: BacklinkSummaryQuery) => [...backlinkKeys.all, 'summary', query] as const,
  trend: (query: BacklinkTrendQuery) => [...backlinkKeys.all, 'trend', query] as const,
};

const get = <T,>(url: string, params?: object) =>
  api.get<T>(url, params ? { params: cleanParams(params), paramsSerializer: serializeParams } : undefined).then((response) => response.data);

export function useBacklinks(query: BacklinkQuery) {
  return useQuery({ queryKey: backlinkKeys.list(query), queryFn: () => get<PageResponse<Backlink>>('/marketing/backlinks', query), placeholderData: keepPreviousData });
}

export function useBacklink(id: number | null) {
  return useQuery({ queryKey: backlinkKeys.detail(id ?? 0), queryFn: () => get<Backlink>(`/marketing/backlinks/${id}`), enabled: id !== null });
}

export function useBacklinkSummary(query: BacklinkSummaryQuery) {
  return useQuery({ queryKey: backlinkKeys.summary(query), queryFn: () => get<BacklinkSummary>('/marketing/backlinks/summary', query), placeholderData: keepPreviousData });
}

export function useBacklinkTrend(query: BacklinkTrendQuery) {
  return useQuery({
    queryKey: backlinkKeys.trend(query),
    queryFn: () => get<{ targetsVisible: boolean; months: BacklinkTrendMonth[] }>('/marketing/backlinks/trend', query),
    placeholderData: keepPreviousData,
  });
}

/** A backlink moves the list, the summary, the history and the Backlinks target; a returned one replaces the cached detail. */
function useBacklinkMutation<V>(mutationFn: (variables: V) => Promise<Backlink | number>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (result) => {
      if (typeof result === 'number') queryClient.removeQueries({ queryKey: backlinkKeys.detail(result) });
      else queryClient.setQueryData(backlinkKeys.detail(result.id), result);
      void queryClient.invalidateQueries({ queryKey: [...backlinkKeys.all, 'list'] });
      void queryClient.invalidateQueries({ queryKey: [...backlinkKeys.all, 'summary'] });
      void queryClient.invalidateQueries({ queryKey: [...backlinkKeys.all, 'trend'] });
      void queryClient.invalidateQueries({ queryKey: ['marketing', 'targets'] });
    },
  });
}

export function useCreateBacklink() {
  return useBacklinkMutation((input: SaveBacklinkInput) => api.post<Backlink>('/marketing/backlinks', input).then((r) => r.data));
}

export function useUpdateBacklink(id: number) {
  return useBacklinkMutation((input: SaveBacklinkInput) => api.put<Backlink>(`/marketing/backlinks/${id}`, input).then((r) => r.data));
}

/** Dates the stages the status now needs `date` (today when omitted) and clears the ones it no longer allows. */
export function useChangeBacklinkStatus(id: number) {
  return useBacklinkMutation((input: { version: number; status: BacklinkStatus; date?: string }) =>
    api.put<Backlink>(`/marketing/backlinks/${id}/status`, input).then((r) => r.data),
  );
}

export function useDeleteBacklink() {
  return useBacklinkMutation((id: number) => api.delete(`/marketing/backlinks/${id}`).then(() => id));
}

/** Downloads the backlink table for the same filters as on screen. */
export function exportBacklinks(query: Omit<BacklinkQuery, 'page' | 'size' | 'sort'>): Promise<void> {
  const search = serializeParams(cleanParams(query));
  const name = query.month ? `backlinks-${query.year}-${String(query.month).padStart(2, '0')}.csv` : 'backlinks.csv';
  return downloadFile(`/marketing/backlinks/export${search ? `?${search}` : ''}`, name);
}
