import type { ProviderCategory, RankingStatus, TargetStatus } from './api';

/** Labels from the brief: TOP 10 (green), RANKING (orange), NOT RANKED (red). */
export const RANKING_STATUS_LABELS: Record<RankingStatus, string> = {
  TOP_10: 'TOP 10',
  RANKING: 'RANKING',
  NOT_RANKED: 'NOT RANKED',
};

export const TARGET_STATUS_LABELS: Record<TargetStatus, string> = {
  ACHIEVED: 'Achieved',
  IN_PROGRESS: 'In progress',
  BEHIND: 'Behind',
};

export const PROVIDER_CATEGORY_LABELS: Record<ProviderCategory, string> = {
  SEO_RANKINGS: 'SEO rankings',
  EMAIL_CAMPAIGNS: 'Email campaigns',
  PAID_CAMPAIGNS: 'Paid campaigns',
  ANALYTICS: 'Website analytics',
  LEADS: 'Leads',
};
