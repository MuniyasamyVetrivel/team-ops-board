import { z } from 'zod';

import { parseLocalDate } from '@/features/tasks/task-meta';

import type { AdDataSource, AdPlatform, CampaignObjective, PaidCampaign, PaidCampaignStatus, PaidMonth } from './api';

export const PLATFORM_LABELS: Record<AdPlatform, string> = {
  LINKEDIN: 'LinkedIn',
  GOOGLE_ADS: 'Google Ads',
  META: 'Meta',
  OTHER: 'Other',
};

export const OBJECTIVE_LABELS: Record<CampaignObjective, string> = {
  BRAND_AWARENESS: 'Brand awareness',
  WEBSITE_VISITS: 'Website visits',
  ENGAGEMENT: 'Engagement',
  VIDEO_VIEWS: 'Video views',
  LEAD_GENERATION: 'Lead generation',
  WEBSITE_CONVERSIONS: 'Website conversions',
  JOB_APPLICANTS: 'Job applicants',
};

export const PAID_STATUS_LABELS: Record<PaidCampaignStatus, string> = {
  DRAFT: 'Draft',
  ACTIVE: 'Active',
  PAUSED: 'Paused',
  COMPLETED: 'Completed',
};

export const SOURCE_LABELS: Record<AdDataSource, string> = {
  MANUAL: 'Manual',
  CSV: 'CSV import',
  LINKEDIN_ADS: 'LinkedIn Ads',
};

/** Formulas of the server-computed rates (brief sections 41 and 80). */
export const PAID_RATE_HINTS = {
  ctr: 'Clicks ÷ impressions',
  costPerLead: 'Spend ÷ leads',
  conversionRate: 'Conversions ÷ leads',
  costPerClick: 'Spend ÷ clicks',
} as const;

const MAX_COUNT = 1_000_000_000;
const MAX_MONEY = 999_999_999_999.99;

const strip = (v: string) => v.trim().replace(/,/g, '');

/** A whole count typed into a form ("1,50,000" allowed); empty means 0. */
export const paidCountField = z
  .string()
  .refine((v) => strip(v) === '' || /^\d+$/.test(strip(v)), 'Enter a whole number')
  .refine((v) => strip(v) === '' || Number(strip(v)) <= MAX_COUNT, 'Too large');

/** An amount in rupees with at most two decimals ("42,000.50" allowed). */
export function moneyField(required: string) {
  return z
    .string()
    .refine((v) => strip(v) !== '', required)
    .refine((v) => strip(v) === '' || /^\d+(\.\d{1,2})?$/.test(strip(v)), 'Enter an amount with at most two decimals')
    .refine((v) => strip(v) === '' || Number(strip(v)) <= MAX_MONEY, 'Too large');
}

export function parseNumber(value: string | undefined): number {
  const v = strip(value ?? '');
  return v === '' ? 0 : Number(v);
}

export interface Period {
  month: number;
  year: number;
}

/** Months inside the campaign's dates, up to the business month of today, that have no results yet; newest first. */
export function recordableMonths(campaign: PaidCampaign, months: PaidMonth[], today: string): Period[] {
  const start = parseLocalDate(campaign.startDate);
  const now = parseLocalDate(today);
  const end = campaign.endDate ? parseLocalDate(campaign.endDate) : now;
  const last = end < now ? end : now;
  const recorded = new Set(months.map((m) => `${m.period.year}-${m.period.month}`));
  const first = start.getFullYear() * 12 + start.getMonth();
  const periods: Period[] = [];
  for (let index = last.getFullYear() * 12 + last.getMonth(); index >= first; index--) {
    const period = { month: (index % 12) + 1, year: Math.floor(index / 12) };
    if (!recorded.has(`${period.year}-${period.month}`)) periods.push(period);
  }
  return periods;
}
