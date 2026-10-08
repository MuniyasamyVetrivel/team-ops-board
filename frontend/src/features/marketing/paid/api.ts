import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

import type { MarketingPeriod } from '../api';

export type AdPlatform = 'LINKEDIN' | 'GOOGLE_ADS' | 'META' | 'OTHER';
export type CampaignObjective = 'BRAND_AWARENESS' | 'WEBSITE_VISITS' | 'ENGAGEMENT' | 'VIDEO_VIEWS' | 'LEAD_GENERATION' | 'WEBSITE_CONVERSIONS' | 'JOB_APPLICANTS';
export type PaidCampaignStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'COMPLETED';
export type AdDataSource = 'MANUAL' | 'CSV' | 'LINKEDIN_ADS';

export const AD_PLATFORMS: AdPlatform[] = ['LINKEDIN', 'GOOGLE_ADS', 'META', 'OTHER'];
export const CAMPAIGN_OBJECTIVES: CampaignObjective[] = ['LEAD_GENERATION', 'WEBSITE_CONVERSIONS', 'WEBSITE_VISITS', 'BRAND_AWARENESS', 'ENGAGEMENT', 'VIDEO_VIEWS', 'JOB_APPLICANTS'];
export const PAID_STATUSES: PaidCampaignStatus[] = ['DRAFT', 'ACTIVE', 'PAUSED', 'COMPLETED'];

/** The import type of PaidResultsCsvImporter. */
export const PAID_IMPORT_TYPE = 'paid-campaign-results';

/** Mirrors PaidResults: one campaign's month, or a sum. */
export interface PaidResults {
  spend: number;
  impressions: number;
  clicks: number;
  leads: number;
  conversions: number;
}

/** Mirrors PaidRates: computed by the server; null when the denominator is zero ("—"). */
export interface PaidRates {
  ctr: number | null;
  costPerLead: number | null;
  conversionRate: number | null;
  costPerClick: number | null;
}

/** Mirrors PaidCampaignDtos.Figures. */
export interface PaidFigures {
  results: PaidResults;
  rates: PaidRates;
}

/** Mirrors BudgetProgress: remaining goes negative when overspent. */
export interface BudgetProgress {
  budget: number | null;
  spent: number;
  remaining: number | null;
  usedPct: number | null;
  overBudget: boolean;
}

/** Mirrors PaidCampaignDtos.CampaignItem. */
export interface PaidCampaign {
  id: number;
  name: string;
  platform: AdPlatform;
  objective: CampaignObjective;
  startDate: string;
  endDate: string | null;
  budget: number;
  currency: string;
  owner: UserSummary | null;
  status: PaidCampaignStatus;
  notes: string | null;
  provider: AdDataSource;
  externalId: string | null;
  lifetime: PaidFigures;
  budgetProgress: BudgetProgress;
  /** The filtered month's figures; null when the list is not filtered by month. */
  month: PaidFigures | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors PaidCampaignDtos.MonthRow. */
export interface PaidMonth {
  id: number;
  period: MarketingPeriod;
  figures: PaidFigures;
  notes: string | null;
  source: AdDataSource;
  recordedBy: UserSummary | null;
  updatedAt: string;
  version: number;
  /** The viewer may still correct this month (server decides). */
  correctable: boolean;
}

/** Mirrors PaidCampaignDtos.CampaignDetail; months oldest first. */
export interface PaidCampaignDetail {
  campaign: PaidCampaign;
  months: PaidMonth[];
  permissions: { canEdit: boolean };
}

/** Mirrors PaidCampaignDtos.MonthTotals. */
export interface PaidMonthTotals {
  period: MarketingPeriod;
  campaigns: number;
  figures: PaidFigures;
}

export interface PlatformTotals {
  platform: AdPlatform;
  campaigns: number;
  figures: PaidFigures;
}

/** Mirrors PaidCampaignDtos.MonthlySummary. */
export interface PaidSummary {
  current: PaidMonthTotals;
  comparison: PaidMonthTotals;
  byPlatform: PlatformTotals[];
  /** Budget of the campaigns running in the month (not drafts) against their spend to date. */
  budget: { campaigns: number; progress: BudgetProgress };
}

export interface PaidCampaignQuery {
  search?: string;
  platform?: AdPlatform[];
  status?: PaidCampaignStatus[];
  ownerId?: number;
  month?: number;
  year?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface PaidSummaryQuery {
  month: number;
  year: number;
  compareMonth?: number;
  compareYear?: number;
  ownerId?: number;
}

export interface PaidTrendQuery {
  month: number;
  year: number;
  months: number;
  ownerId?: number;
}

/** Mirrors PaidCampaignDtos.SaveCampaign; `version` only when updating. */
export interface SavePaidCampaignInput {
  version?: number;
  name: string;
  platform: AdPlatform;
  objective: CampaignObjective;
  startDate: string;
  endDate: string | null;
  budget: number;
  ownerId: number | null;
  status: PaidCampaignStatus;
  notes: string | null;
}

/** Mirrors PaidCampaignDtos.SaveMonth; `version` only when correcting a recorded month. */
export interface SavePaidMonthInput {
  version?: number;
  amountSpent: number;
  impressions: number;
  clicks: number;
  leads: number;
  conversions: number;
  notes: string | null;
}

export const paidKeys = {
  all: ['marketing', 'paid'] as const,
  list: (query: PaidCampaignQuery) => [...paidKeys.all, 'list', query] as const,
  detail: (id: number) => [...paidKeys.all, 'detail', id] as const,
  summary: (query: PaidSummaryQuery) => [...paidKeys.all, 'summary', query] as const,
  trend: (query: PaidTrendQuery) => [...paidKeys.all, 'trend', query] as const,
};

const get = <T,>(url: string, params?: object) =>
  api.get<T>(url, params ? { params: cleanParams(params), paramsSerializer: serializeParams } : undefined).then((response) => response.data);

export function usePaidCampaigns(query: PaidCampaignQuery) {
  return useQuery({ queryKey: paidKeys.list(query), queryFn: () => get<PageResponse<PaidCampaign>>('/marketing/paid-campaigns', query), placeholderData: keepPreviousData });
}

export function usePaidCampaign(id: number | null) {
  return useQuery({ queryKey: paidKeys.detail(id ?? 0), queryFn: () => get<PaidCampaignDetail>(`/marketing/paid-campaigns/${id}`), enabled: id !== null });
}

export function usePaidSummary(query: PaidSummaryQuery) {
  return useQuery({ queryKey: paidKeys.summary(query), queryFn: () => get<PaidSummary>('/marketing/paid-campaigns/summary', query), placeholderData: keepPreviousData });
}

export function usePaidTrend(query: PaidTrendQuery) {
  return useQuery({
    queryKey: paidKeys.trend(query),
    queryFn: () => get<{ months: PaidMonthTotals[] }>('/marketing/paid-campaigns/trend', query).then((data) => data.months),
    placeholderData: keepPreviousData,
  });
}

/**
 * Plans and results move the list, the summary, the trend and the LinkedIn Campaigns target; a returned detail
 * replaces the cached one.
 */
function usePaidMutation<V>(mutationFn: (variables: V) => Promise<PaidCampaignDetail | number>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (result) => {
      if (typeof result === 'number') queryClient.removeQueries({ queryKey: paidKeys.detail(result) });
      else queryClient.setQueryData(paidKeys.detail(result.campaign.id), result);
      void queryClient.invalidateQueries({ queryKey: [...paidKeys.all, 'list'] });
      void queryClient.invalidateQueries({ queryKey: [...paidKeys.all, 'summary'] });
      void queryClient.invalidateQueries({ queryKey: [...paidKeys.all, 'trend'] });
      void queryClient.invalidateQueries({ queryKey: ['marketing', 'targets'] });
    },
  });
}

export function useCreatePaidCampaign() {
  return usePaidMutation((input: SavePaidCampaignInput) => api.post<PaidCampaignDetail>('/marketing/paid-campaigns', input).then((r) => r.data));
}

export function useUpdatePaidCampaign(id: number) {
  return usePaidMutation((input: SavePaidCampaignInput) => api.put<PaidCampaignDetail>(`/marketing/paid-campaigns/${id}`, input).then((r) => r.data));
}

export function useDeletePaidCampaign() {
  return usePaidMutation((id: number) => api.delete(`/marketing/paid-campaigns/${id}`).then(() => id));
}

/** Records a month's results (no `version`) or corrects them (`version` of the recorded month). */
export function useSavePaidMonth(id: number) {
  return usePaidMutation(({ month, year, input }: { month: number; year: number; input: SavePaidMonthInput }) =>
    api.put<PaidCampaignDetail>(`/marketing/paid-campaigns/${id}/results/${year}/${month}`, input).then((r) => r.data),
  );
}

/** Downloads the campaign table for the same filters as on screen. */
export function exportPaidCampaigns(query: Omit<PaidCampaignQuery, 'page' | 'size' | 'sort'>): Promise<void> {
  const search = serializeParams(cleanParams(query));
  const name = query.month ? `paid-campaigns-${query.year}-${String(query.month).padStart(2, '0')}.csv` : 'paid-campaigns.csv';
  return downloadFile(`/marketing/paid-campaigns/export${search ? `?${search}` : ''}`, name);
}
