import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

import type { MarketingPeriod } from '../api';

export type EmailCampaignType = 'NEWSLETTER' | 'LEAD_GENERATION' | 'PRODUCT_PROMOTION' | 'EVENT' | 'RECRUITMENT' | 'OTHER';
export type EmailCampaignStatus = 'DRAFT' | 'SCHEDULED' | 'SENT' | 'CANCELLED';
export type CampaignProvider = 'MANUAL' | 'CSV' | 'ZOHO';

export const CAMPAIGN_TYPES: EmailCampaignType[] = ['NEWSLETTER', 'LEAD_GENERATION', 'PRODUCT_PROMOTION', 'EVENT', 'RECRUITMENT', 'OTHER'];
export const CAMPAIGN_STATUSES: EmailCampaignStatus[] = ['DRAFT', 'SCHEDULED', 'SENT', 'CANCELLED'];

/** The import type of EmailCampaignCsvImporter. */
export const EMAIL_IMPORT_TYPE = 'email-campaigns';

/** Mirrors EmailCounts: raw counts of one campaign, or summed over several. */
export interface EmailCounts {
  emailsSent: number;
  delivered: number;
  bounced: number;
  opened: number;
  uniqueOpens: number;
  clicked: number;
  uniqueClicks: number;
  unsubscribed: number;
  leads: number;
}

/** Mirrors EmailRates: percentages computed by the server; null when the denominator is zero ("—"). */
export interface EmailRates {
  deliveryRate: number | null;
  openRate: number | null;
  clickRate: number | null;
  clickToOpenRate: number | null;
  leadConversionRate: number | null;
  bounceRate: number | null;
  unsubscribeRate: number | null;
}

/** Mirrors EmailCampaignDtos.CampaignItem. */
export interface EmailCampaign {
  id: number;
  name: string;
  campaignType: EmailCampaignType;
  campaignDate: string;
  owner: UserSummary | null;
  audience: string | null;
  status: EmailCampaignStatus;
  counts: EmailCounts;
  rates: EmailRates;
  notes: string | null;
  provider: CampaignProvider;
  externalId: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors EmailCampaignDtos.MonthTotals. */
export interface MonthTotals {
  period: MarketingPeriod;
  campaigns: number;
  counts: EmailCounts;
  rates: EmailRates;
}

export interface TypeTotals {
  campaignType: EmailCampaignType;
  campaigns: number;
  counts: EmailCounts;
  rates: EmailRates;
}

/** Mirrors EmailCampaignDtos.MonthlySummary. */
export interface MonthlySummary {
  current: MonthTotals;
  comparison: MonthTotals;
  byType: TypeTotals[];
}

export interface CampaignQuery {
  search?: string;
  type?: EmailCampaignType[];
  status?: EmailCampaignStatus[];
  ownerId?: number;
  month?: number;
  year?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface SummaryQuery {
  month: number;
  year: number;
  compareMonth?: number;
  compareYear?: number;
  ownerId?: number;
}

/** Mirrors EmailCampaignDtos.SaveCampaign; `version` only when updating. */
export interface SaveCampaignInput extends Omit<EmailCounts, 'leads'> {
  version?: number;
  name: string;
  campaignType: EmailCampaignType;
  campaignDate: string;
  ownerId: number | null;
  audience: string | null;
  status: EmailCampaignStatus;
  leadsGenerated: number;
  notes: string | null;
}

export const emailKeys = {
  all: ['marketing', 'email'] as const,
  list: (query: CampaignQuery) => [...emailKeys.all, 'list', query] as const,
  summary: (query: SummaryQuery) => [...emailKeys.all, 'summary', query] as const,
  trend: (query: { month: number; year: number; months: number; ownerId?: number }) => [...emailKeys.all, 'trend', query] as const,
};

const get = <T,>(url: string, params?: object) =>
  api.get<T>(url, params ? { params: cleanParams(params), paramsSerializer: serializeParams } : undefined).then((response) => response.data);

export function useEmailCampaigns(query: CampaignQuery, enabled = true) {
  return useQuery({ queryKey: emailKeys.list(query), queryFn: () => get<PageResponse<EmailCampaign>>('/marketing/email-campaigns', query), placeholderData: keepPreviousData, enabled });
}

export function useEmailSummary(query: SummaryQuery | null) {
  return useQuery({
    queryKey: emailKeys.summary(query ?? { month: 0, year: 0 }),
    queryFn: () => get<MonthlySummary>('/marketing/email-campaigns/summary', query ?? {}),
    enabled: query !== null,
    placeholderData: keepPreviousData,
  });
}

export function useEmailTrend(query: { month: number; year: number; months: number; ownerId?: number } | null) {
  return useQuery({
    queryKey: emailKeys.trend(query ?? { month: 0, year: 0, months: 0 }),
    queryFn: () => get<{ months: MonthTotals[] }>('/marketing/email-campaigns/trend', query ?? {}).then((data) => data.months),
    enabled: query !== null,
    placeholderData: keepPreviousData,
  });
}

/** Campaign changes move the list, the summary, the trend and the Email Campaigns target. */
function useCampaignMutation<V, R>(mutationFn: (variables: V) => Promise<R>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: emailKeys.all });
      void queryClient.invalidateQueries({ queryKey: ['marketing', 'targets'] });
    },
  });
}

export function useCreateCampaign() {
  return useCampaignMutation((input: SaveCampaignInput) => api.post<EmailCampaign>('/marketing/email-campaigns', input).then((r) => r.data));
}

export function useUpdateCampaign(id: number) {
  return useCampaignMutation((input: SaveCampaignInput) => api.put<EmailCampaign>(`/marketing/email-campaigns/${id}`, input).then((r) => r.data));
}

export function useDeleteCampaign() {
  return useCampaignMutation((id: number) => api.delete(`/marketing/email-campaigns/${id}`).then(() => id));
}

/** Downloads the campaign table for the same filters as on screen. */
export function exportCampaigns(query: Omit<CampaignQuery, 'page' | 'size' | 'sort'>): Promise<void> {
  const search = serializeParams(cleanParams(query));
  const name = query.month ? `email-campaigns-${query.year}-${String(query.month).padStart(2, '0')}.csv` : 'email-campaigns.csv';
  return downloadFile(`/marketing/email-campaigns/export${search ? `?${search}` : ''}`, name);
}
