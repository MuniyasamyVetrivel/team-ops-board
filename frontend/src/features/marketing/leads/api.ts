import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

import type { MarketingPeriod } from '../api';
import type { LeadSource, TargetItem } from '../targets/api';

export type LeadStatus = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'CONVERTED' | 'LOST';
export type LeadOrigin = 'MANUAL' | 'CSV' | 'WEBSITE' | 'CRM';
export type LinkKind = 'EMAIL_CAMPAIGN' | 'PAID_CAMPAIGN' | 'CONTENT';

export const LEAD_STATUSES: LeadStatus[] = ['NEW', 'CONTACTED', 'QUALIFIED', 'CONVERTED', 'LOST'];

/** The import type of LeadCsvImporter. */
export const LEAD_IMPORT_TYPE = 'marketing-leads';

/** Mirrors LeadDtos.LeadLink; `date` is when the campaign was sent or started, or the content published. */
export interface LeadLink {
  kind: LinkKind;
  id: number;
  name: string;
  date: string | null;
}

interface DepartmentRef {
  id: number;
  name: string;
  code: string;
}

/**
 * Mirrors LeadDtos.LeadItem. `countLocked`: the lead's month is closed for the viewer, so its date and source (which
 * count towards targets) are fixed and it cannot be deleted.
 */
export interface Lead {
  id: number;
  code: string;
  name: string;
  company: string | null;
  email: string | null;
  phone: string | null;
  source: LeadSource;
  link: LeadLink | null;
  department: DepartmentRef | null;
  leadDate: string;
  status: LeadStatus;
  owner: UserSummary | null;
  notes: string | null;
  provider: LeadOrigin;
  externalId: string | null;
  createdBy: UserSummary | null;
  countLocked: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors LeadDtos.LinkOption; `detail` is the campaign type, ad platform or content type. */
export interface LinkOption {
  kind: LinkKind;
  id: number;
  name: string;
  date: string | null;
  detail: string | null;
}

/** Mirrors LeadDtos.SourceCount; `target` only when the viewer may see targets. */
export interface SourceCount {
  source: LeadSource;
  leads: number;
  comparison: number;
  target: TargetItem | null;
}

export interface StatusCount {
  status: LeadStatus;
  leads: number;
}

export interface LinkCount {
  kind: LinkKind;
  id: number;
  name: string;
  leads: number;
}

/** Mirrors LeadDtos.LeadSummary. Every source and status is present, zeros included. */
export interface LeadSummary {
  period: MarketingPeriod;
  comparisonPeriod: MarketingPeriod;
  total: number;
  comparisonTotal: number;
  /** Converted ÷ all leads of the month; null when there are none. */
  convertedPct: number | null;
  targetsVisible: boolean;
  /** The Website Leads target of the month. */
  totalTarget: TargetItem | null;
  bySource: SourceCount[];
  byStatus: StatusCount[];
  topLinks: LinkCount[];
}

/** Mirrors LeadDtos.MonthCounts. */
export interface LeadMonthCounts {
  period: MarketingPeriod;
  total: number;
  bySource: Record<LeadSource, number>;
}

export interface LeadQuery {
  search?: string;
  source?: LeadSource[];
  status?: LeadStatus[];
  ownerId?: number;
  month?: number;
  year?: number;
  emailCampaignId?: number;
  paidCampaignId?: number;
  contentItemId?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface LeadSummaryQuery {
  month: number;
  year: number;
  compareMonth?: number;
  compareYear?: number;
  ownerId?: number;
}

export interface LeadTrendQuery {
  month: number;
  year: number;
  months: number;
  ownerId?: number;
}

/** Mirrors LeadDtos.SaveLead; `version` only when updating. At most one link, of the kind the source takes. */
export interface SaveLeadInput {
  version?: number;
  name: string;
  company: string | null;
  email: string | null;
  phone: string | null;
  source: LeadSource;
  emailCampaignId: number | null;
  paidCampaignId: number | null;
  contentItemId: number | null;
  departmentId: number | null;
  leadDate: string;
  status: LeadStatus;
  ownerId: number | null;
  notes: string | null;
}

export const leadKeys = {
  all: ['marketing', 'leads'] as const,
  list: (query: LeadQuery) => [...leadKeys.all, 'list', query] as const,
  detail: (id: number) => [...leadKeys.all, 'detail', id] as const,
  summary: (query: LeadSummaryQuery) => [...leadKeys.all, 'summary', query] as const,
  trend: (query: LeadTrendQuery) => [...leadKeys.all, 'trend', query] as const,
  linkOptions: (kind: LinkKind, search: string) => [...leadKeys.all, 'link-options', kind, search] as const,
};

const get = <T,>(url: string, params?: object) =>
  api.get<T>(url, params ? { params: cleanParams(params), paramsSerializer: serializeParams } : undefined).then((response) => response.data);

export function useLeads(query: LeadQuery) {
  return useQuery({ queryKey: leadKeys.list(query), queryFn: () => get<PageResponse<Lead>>('/marketing/leads', query), placeholderData: keepPreviousData });
}

export function useLead(id: number | null) {
  return useQuery({ queryKey: leadKeys.detail(id ?? 0), queryFn: () => get<Lead>(`/marketing/leads/${id}`), enabled: id !== null });
}

export function useLeadSummary(query: LeadSummaryQuery) {
  return useQuery({ queryKey: leadKeys.summary(query), queryFn: () => get<LeadSummary>('/marketing/leads/summary', query), placeholderData: keepPreviousData });
}

export function useLeadTrend(query: LeadTrendQuery) {
  return useQuery({
    queryKey: leadKeys.trend(query),
    queryFn: () => get<{ months: LeadMonthCounts[] }>('/marketing/leads/trend', query).then((data) => data.months),
    placeholderData: keepPreviousData,
  });
}

/** Campaigns or content a lead can name (newest 20 matching `search`); off while the source takes no link. */
export function useLeadLinkOptions(kind: LinkKind | null, search: string) {
  return useQuery({
    queryKey: leadKeys.linkOptions(kind ?? 'CONTENT', search),
    queryFn: () => get<LinkOption[]>('/marketing/leads/link-options', { kind, search }),
    enabled: kind !== null,
    placeholderData: keepPreviousData,
    staleTime: 60_000,
  });
}

/**
 * A lead moves the list, the summary, the trend and the lead targets; it can also make a campaign undeletable, so the
 * campaign caches refresh too. A returned lead replaces the cached detail.
 */
function useLeadMutation<V>(mutationFn: (variables: V) => Promise<Lead | number>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (result) => {
      if (typeof result === 'number') queryClient.removeQueries({ queryKey: leadKeys.detail(result) });
      else queryClient.setQueryData(leadKeys.detail(result.id), result);
      void queryClient.invalidateQueries({ queryKey: [...leadKeys.all, 'list'] });
      void queryClient.invalidateQueries({ queryKey: [...leadKeys.all, 'summary'] });
      void queryClient.invalidateQueries({ queryKey: [...leadKeys.all, 'trend'] });
      void queryClient.invalidateQueries({ queryKey: ['marketing', 'targets'] });
      void queryClient.invalidateQueries({ queryKey: ['marketing', 'email'] });
      void queryClient.invalidateQueries({ queryKey: ['marketing', 'paid'] });
    },
  });
}

export function useCreateLead() {
  return useLeadMutation((input: SaveLeadInput) => api.post<Lead>('/marketing/leads', input).then((r) => r.data));
}

export function useUpdateLead(id: number) {
  return useLeadMutation((input: SaveLeadInput) => api.put<Lead>(`/marketing/leads/${id}`, input).then((r) => r.data));
}

export function useChangeLeadStatus(id: number) {
  return useLeadMutation((input: { version: number; status: LeadStatus }) => api.put<Lead>(`/marketing/leads/${id}/status`, input).then((r) => r.data));
}

export function useDeleteLead() {
  return useLeadMutation((id: number) => api.delete(`/marketing/leads/${id}`).then(() => id));
}

/** Downloads the lead table for the same filters as on screen. */
export function exportLeads(query: Omit<LeadQuery, 'page' | 'size' | 'sort'>): Promise<void> {
  const search = serializeParams(cleanParams(query));
  const name = query.month ? `marketing-leads-${query.year}-${String(query.month).padStart(2, '0')}.csv` : 'marketing-leads.csv';
  return downloadFile(`/marketing/leads/export${search ? `?${search}` : ''}`, name);
}
