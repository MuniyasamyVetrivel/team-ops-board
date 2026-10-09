import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type UserSummary } from '@/lib/api/types';

import type { MarketingPeriod, TargetStatus } from '../api';

export type ReportUnit = 'COUNT' | 'PERCENT' | 'CURRENCY' | 'DECIMAL';
export type ReportBetter = 'HIGHER' | 'LOWER' | 'NEITHER';
export type GroupKey = 'SEO' | 'LEADS' | 'EMAIL' | 'LINKEDIN' | 'BACKLINKS' | 'CONTENT' | 'ACTIVITIES';

/**
 * Mirrors MarketingReportDtos.Line: `change` = current − previous (points for PERCENT); `changePct` = change ÷ previous
 * × 100, null for PERCENT figures, a missing side or a zero previous value.
 */
export interface ReportLine {
  label: string;
  unit: ReportUnit;
  better: ReportBetter;
  current: number | null;
  previous: number | null;
  change: number | null;
  changePct: number | null;
  /** Comes from a target (shown only to TARGET_VIEW holders). */
  target: boolean;
}

export interface ReportGroup {
  key: GroupKey;
  title: string;
  lines: ReportLine[];
}

/** Mirrors MarketingReportDtos.KeywordMove: `change` = previous − current (positive is better). */
export interface KeywordMove {
  keywordId: number;
  keyword: string;
  page: string | null;
  previousPosition: number | null;
  position: number | null;
  change: number | null;
}

export interface TargetRow {
  type: string;
  unit: string;
  targetValue: number | null;
  actual: number | null;
  achievementPct: number | null;
  remaining: number | null;
  status: TargetStatus | null;
  previousTargetValue: number | null;
  previousActual: number | null;
  previousAchievementPct: number | null;
}

/** Mirrors MarketingReportDtos.MonthlyReport; parts the viewer may not see are left out (groups) or null. */
export interface MonthlyReport {
  period: MarketingPeriod;
  comparisonPeriod: MarketingPeriod;
  ownerId: number | null;
  frozen: boolean;
  generatedAt: string;
  generatedBy: UserSummary | null;
  groups: ReportGroup[];
  keywordMovements: { improved: KeywordMove[]; declined: KeywordMove[] } | null;
  targets: TargetRow[] | null;
}

export interface FrozenMonth {
  period: MarketingPeriod;
  generatedAt: string;
  generatedBy: UserSummary | null;
}

export interface MonthlyReportQuery {
  month: number;
  year: number;
  ownerId?: number;
  live?: boolean;
}

export const reportKeys = {
  all: ['marketing', 'reports'] as const,
  monthly: (query: MonthlyReportQuery) => [...reportKeys.all, 'monthly', query] as const,
  frozen: () => [...reportKeys.all, 'frozen'] as const,
};

export function useMonthlyReport(query: MonthlyReportQuery | null) {
  return useQuery({
    queryKey: reportKeys.monthly(query ?? { month: 0, year: 0 }),
    queryFn: async () => (await api.get<MonthlyReport>('/marketing/reports/monthly', { params: cleanParams(query ?? {}) })).data,
    enabled: query !== null,
    placeholderData: keepPreviousData,
  });
}

export function useFrozenMonths() {
  return useQuery({ queryKey: reportKeys.frozen(), queryFn: async () => (await api.get<FrozenMonth[]>('/marketing/reports/frozen')).data });
}

/** Freezes an ended month's team-wide report; every report query is refreshed. */
export function useFreezeReport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ month, year }: { month: number; year: number }) => (await api.post<MonthlyReport>(`/marketing/reports/monthly/${year}/${month}/freeze`)).data,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: reportKeys.all }),
  });
}

export function exportMonthlyReport(query: MonthlyReportQuery): Promise<void> {
  const search = serializeParams(cleanParams({ ...query, format: 'CSV' }));
  return downloadFile(`/marketing/reports/monthly/export?${search}`, `marketing-report-${query.year}-${String(query.month).padStart(2, '0')}.csv`);
}
