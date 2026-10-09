import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { cleanParams } from '@/lib/api/types';

import type { MarketingPeriod } from '../api';
import type { OccurrenceItem } from '../activities/api';
import type { MonthActivity, MonthTarget } from '../backlinks/api';
import type { BlogTarget, ContentMonth } from '../content/api';
import type { EmailCounts, EmailRates } from '../email/api';
import type { BudgetProgress, PaidRates, PaidResults } from '../paid/api';
import type { SeoStats } from '../seo/api';
import type { LeadSource, TargetItem } from '../targets/api';

/** Mirrors MarketingDashboardDtos.SeoSection: the month and the month before. */
export interface SeoSection {
  totalPages: number;
  current: SeoStats;
  comparison: SeoStats;
}

/** Mirrors MarketingDashboardDtos.LeadSection; `target` is the Website Leads target. */
export interface LeadSection {
  total: number;
  comparisonTotal: number;
  bySource: { source: LeadSource; leads: number; comparison: number }[];
  target: TargetItem | null;
}

export interface EmailMonth {
  campaigns: number;
  counts: EmailCounts;
  rates: EmailRates;
}

export interface PaidMonth {
  campaigns: number;
  results: PaidResults;
  rates: PaidRates;
}

/** Mirrors MarketingDashboardDtos.LinkedInSection: LinkedIn campaigns only. */
export interface LinkedInSection {
  current: PaidMonth;
  comparison: PaidMonth;
  runningCampaigns: number;
  budget: BudgetProgress;
}

/** Mirrors MarketingDashboardDtos.ActivitySection; `completionPct` = completed ÷ (due − skipped). */
export interface ActivitySection {
  due: number;
  completed: number;
  skipped: number;
  open: number;
  overdue: number;
  completionPct: number | null;
  attention: OccurrenceItem[];
}

/** Mirrors MarketingDashboardDtos.TrendMonth: null when the viewer may not see the module (or nothing was recorded). */
export interface DashboardTrendMonth {
  period: MarketingPeriod;
  leads: number | null;
  top10Keywords: number | null;
  emailLeads: number | null;
  linkedinSpend: number | null;
  linkedinLeads: number | null;
  backlinksLive: number | null;
  blogsPublished: number | null;
}

/**
 * Mirrors MarketingDashboardDtos.Dashboard. A section is null when the viewer lacks its module's view permission;
 * targets are null without TARGET_VIEW (`targetsVisible`).
 */
export interface MarketingDashboard {
  period: MarketingPeriod;
  comparisonPeriod: MarketingPeriod;
  today: string;
  targetsVisible: boolean;
  seo: SeoSection | null;
  leads: LeadSection | null;
  email: { current: EmailMonth; comparison: EmailMonth } | null;
  linkedin: LinkedInSection | null;
  backlinks: { current: MonthActivity; comparison: MonthActivity; target: MonthTarget | null } | null;
  content: { current: ContentMonth; comparison: ContentMonth; blogTarget: BlogTarget | null } | null;
  targets: { targets: TargetItem[]; summary: { total: number; achieved: number; inProgress: number; behind: number } } | null;
  activities: ActivitySection;
  trend: DashboardTrendMonth[];
}

export interface DashboardQuery {
  month?: number;
  year?: number;
  ownerId?: number;
  months?: number;
}

export const dashboardKeys = {
  all: ['marketing', 'dashboard'] as const,
  detail: (query: DashboardQuery) => [...dashboardKeys.all, query] as const,
};

/** One request for the whole dashboard; `enabled` lets the home dashboard skip it for viewers without marketing access. */
export function useMarketingDashboard(query: DashboardQuery, enabled = true) {
  return useQuery({
    queryKey: dashboardKeys.detail(query),
    queryFn: async () => (await api.get<MarketingDashboard>('/marketing/dashboard', { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
    enabled,
  });
}
