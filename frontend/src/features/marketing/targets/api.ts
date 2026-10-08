import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { cleanParams, serializeParams, type UserSummary } from '@/lib/api/types';

import type { MarketingPeriod, TargetStatus } from '../api';

export type TargetUnit = 'COUNT' | 'CURRENCY' | 'PERCENT';
export type ActualSource = 'MANUAL' | 'LEADS' | 'LEADS_BY_SOURCE' | 'BACKLINKS_LIVE' | 'BLOGS_PUBLISHED' | 'KEYWORDS_TOP10' | 'EMAIL_CAMPAIGNS' | 'PAID_CAMPAIGNS' | 'LANDING_PAGES';
export type LeadSource = 'ORGANIC' | 'EMAIL' | 'LINKEDIN' | 'PAID_CAMPAIGN' | 'BLOG' | 'WEBSITE' | 'REFERRAL' | 'OTHER';
/** MANUAL: entered by hand; AUTOMATIC: aggregated from records; NONE: nothing recorded yet. */
export type ActualOrigin = 'MANUAL' | 'AUTOMATIC' | 'NONE';
export type TrendView = 'MONTH' | 'QUARTER' | 'YEAR';

export const TARGET_UNITS: TargetUnit[] = ['COUNT', 'CURRENCY', 'PERCENT'];
export const ACTUAL_SOURCES: ActualSource[] = ['MANUAL', 'LEADS', 'LEADS_BY_SOURCE', 'BACKLINKS_LIVE', 'BLOGS_PUBLISHED', 'KEYWORDS_TOP10', 'EMAIL_CAMPAIGNS', 'PAID_CAMPAIGNS', 'LANDING_PAGES'];
export const LEAD_SOURCES: LeadSource[] = ['ORGANIC', 'EMAIL', 'LINKEDIN', 'PAID_CAMPAIGN', 'BLOG', 'WEBSITE', 'REFERRAL', 'OTHER'];

interface DepartmentRef {
  id: number;
  name: string;
  code: string;
}

/** Mirrors TargetDtos.TargetTypeItem. `automatic`: the actual is computed now; `locked`: unit and source are fixed. */
export interface TargetTypeItem {
  id: number;
  code: string;
  name: string;
  description: string | null;
  unit: TargetUnit;
  actualSource: ActualSource;
  leadSourceFilter: LeadSource | null;
  behindThresholdPct: number | null;
  effectiveThresholdPct: number;
  automatic: boolean;
  active: boolean;
  position: number;
  targetCount: number;
  locked: boolean;
  version: number;
}

/** Mirrors TargetDtos.TypeRef. */
export interface TypeRef {
  id: number;
  code: string;
  name: string;
  unit: TargetUnit;
  actualSource: ActualSource;
  automatic: boolean;
}

/**
 * Mirrors TargetDtos.TargetItem. Future months are planned: `actual`, `achievementPct` and `status` are null.
 * `actualEditable`: the actual is entered by hand for this target.
 */
export interface TargetItem {
  id: number;
  type: TypeRef;
  month: number;
  year: number;
  label: string;
  targetValue: number;
  actual: number | null;
  actualOrigin: ActualOrigin;
  achievementPct: number | null;
  remaining: number;
  status: TargetStatus | null;
  thresholdPct: number;
  owner: UserSummary | null;
  department: DepartmentRef;
  notes: string | null;
  editable: boolean;
  actualEditable: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors TargetDtos.MonthlyTargets. */
export interface MonthlyTargets {
  period: MarketingPeriod;
  targets: TargetItem[];
  summary: { total: number; achieved: number; inProgress: number; behind: number };
  typesWithoutTarget: TypeRef[];
}

/** Mirrors TargetDtos.TrendPoint: null figures for buckets with no target, or entirely in the future. */
export interface TrendPoint {
  label: string;
  from: MarketingPeriod;
  to: MarketingPeriod;
  months: number;
  targetValue: number | null;
  actual: number | null;
  achievementPct: number | null;
  remaining: number | null;
  status: TargetStatus | null;
}

export interface TargetTrend {
  type: TypeRef;
  view: TrendView;
  thresholdPct: number;
  points: TrendPoint[];
}

export interface TargetQuery {
  month: number;
  year: number;
  ownerId?: number;
  status?: TargetStatus[];
}

export interface CreateTargetInput {
  typeId: number;
  month: number;
  year: number;
  targetValue: number;
  actualValue: number | null;
  ownerId: number | null;
  departmentId: number;
  notes: string | null;
}

export interface UpdateTargetInput {
  version: number;
  targetValue: number;
  actualValue: number | null;
  ownerId: number | null;
  departmentId: number;
  notes: string | null;
}

export interface SetMonthlyTargetsInput {
  month: number;
  year: number;
  ownerId: number | null;
  departmentId: number;
  entries: { typeId: number; targetValue: number }[];
}

export interface SaveTargetTypeInput {
  name: string;
  description: string | null;
  unit: TargetUnit;
  actualSource: ActualSource;
  leadSourceFilter: LeadSource | null;
  behindThresholdPct: number | null;
}

export interface CreateTargetTypeInput extends SaveTargetTypeInput {
  code: string | null;
}

export interface UpdateTargetTypeInput extends SaveTargetTypeInput {
  version: number;
  active: boolean;
  position: number;
}

export const targetKeys = {
  all: ['marketing', 'targets'] as const,
  monthly: (query: TargetQuery) => [...targetKeys.all, 'monthly', query] as const,
  trend: (typeId: number, view: TrendView, year: number) => [...targetKeys.all, 'trend', typeId, view, year] as const,
  types: (includeInactive: boolean) => [...targetKeys.all, 'types', includeInactive] as const,
};

const get = <T,>(url: string, params?: object) =>
  api.get<T>(url, params ? { params: cleanParams(params), paramsSerializer: serializeParams } : undefined).then((response) => response.data);

export function useMonthlyTargets(query: TargetQuery | null) {
  return useQuery({
    queryKey: targetKeys.monthly(query ?? { month: 0, year: 0 }),
    queryFn: () => get<MonthlyTargets>('/marketing/targets', query ?? {}),
    enabled: query !== null,
    placeholderData: keepPreviousData,
  });
}

export function useTargetTrend(typeId: number | null, view: TrendView, year: number) {
  return useQuery({
    queryKey: targetKeys.trend(typeId ?? 0, view, year),
    queryFn: () => get<TargetTrend>('/marketing/targets/trend', { typeId, view, year }),
    enabled: typeId !== null,
    placeholderData: keepPreviousData,
  });
}

export function useTargetTypes(includeInactive = false) {
  return useQuery({
    queryKey: targetKeys.types(includeInactive),
    queryFn: () => get<TargetTypeItem[]>('/marketing/target-types', { includeInactive }),
    staleTime: 60_000,
  });
}

/** Targets, trends and types move together (a type rename shows on every target), so all are refreshed. */
function useTargetMutation<V, R>(mutationFn: (variables: V) => Promise<R>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: targetKeys.all }),
  });
}

export function useCreateTarget() {
  return useTargetMutation((input: CreateTargetInput) => api.post<TargetItem>('/marketing/targets', input).then((r) => r.data));
}

export function useUpdateTarget(id: number) {
  return useTargetMutation((input: UpdateTargetInput) => api.put<TargetItem>(`/marketing/targets/${id}`, input).then((r) => r.data));
}

export function useDeleteTarget() {
  return useTargetMutation((id: number) => api.delete(`/marketing/targets/${id}`).then(() => id));
}

/** Several types for one month, all or nothing. */
export function useSetMonthlyTargets() {
  return useTargetMutation((input: SetMonthlyTargetsInput) =>
    api.post<{ period: MarketingPeriod; created: number }>('/marketing/targets/monthly', input).then((r) => r.data),
  );
}

export function useCreateTargetType() {
  return useTargetMutation((input: CreateTargetTypeInput) => api.post<TargetTypeItem>('/marketing/target-types', input).then((r) => r.data));
}

export function useUpdateTargetType(id: number) {
  return useTargetMutation((input: UpdateTargetTypeInput) => api.put<TargetTypeItem>(`/marketing/target-types/${id}`, input).then((r) => r.data));
}

export function useDeleteTargetType() {
  return useTargetMutation((id: number) => api.delete(`/marketing/target-types/${id}`).then(() => id));
}
