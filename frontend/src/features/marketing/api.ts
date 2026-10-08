import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import type { UserSummary } from '@/lib/api/types';

/** Mirrors MarketingDtos.Period. */
export interface MarketingPeriod {
  month: number;
  year: number;
  label: string;
}

/** Mirrors MarketingDtos.MarketingContext: everything the filter bar needs, resolved by the server. */
export interface MarketingContext {
  /** Business "today" (APP_TIME_ZONE), never the browser clock. */
  today: string;
  currentPeriod: MarketingPeriod;
  years: number[];
  owners: UserSummary[];
  behindThresholdPct: number;
}

export type ProviderCategory = 'SEO_RANKINGS' | 'EMAIL_CAMPAIGNS' | 'PAID_CAMPAIGNS' | 'ANALYTICS' | 'LEADS';

/** Mirrors MarketingDataProvider.ProviderInfo. */
export interface ProviderInfo {
  code: string;
  name: string;
  category: ProviderCategory;
  automated: boolean;
  connected: boolean;
  description: string;
  planned: string[];
}

/** Shared rule outputs the marketing modules send (computed server-side, never stored). */
export type RankingStatus = 'TOP_10' | 'RANKING' | 'NOT_RANKED';
export type RankingMovement = 'IMPROVED' | 'DECLINED' | 'UNCHANGED' | 'NEW';
export interface RankingChange {
  value: number | null;
  movement: RankingMovement;
}
export type TargetStatus = 'ACHIEVED' | 'IN_PROGRESS' | 'BEHIND';

/** Mirrors com.teamops.common.csv.CsvColumn. */
export interface CsvColumn {
  name: string;
  required: boolean;
  description: string;
  example: string | null;
}

/** Mirrors ImportDtos.ImportDefinition. */
export interface ImportDefinition {
  type: string;
  label: string;
  description: string;
  columns: CsvColumn[];
  maxRows: number;
}

export interface CellError {
  /** null when the problem concerns the whole row (e.g. a duplicate). */
  column: string | null;
  message: string;
}

export interface PreviewRow {
  line: number;
  values: Record<string, string>;
  errors: CellError[];
}

/** Mirrors ImportDtos.ImportPreview. Nothing has been saved yet. */
export interface ImportPreview {
  type: string;
  fileName: string;
  checksum: string;
  columns: string[];
  unknownColumns: string[];
  totalRows: number;
  validCount: number;
  invalidCount: number;
  validRows: PreviewRow[];
  invalidRows: PreviewRow[];
}

export interface ImportResult {
  type: string;
  imported: number;
  skipped: number;
}

export const marketingKeys = {
  all: ['marketing'] as const,
  context: () => [...marketingKeys.all, 'context'] as const,
  integrations: () => [...marketingKeys.all, 'integrations'] as const,
  imports: () => [...marketingKeys.all, 'imports'] as const,
};

export function useMarketingContext() {
  return useQuery({
    queryKey: marketingKeys.context(),
    queryFn: async () => (await api.get<MarketingContext>('/marketing/context')).data,
    staleTime: 5 * 60_000,
  });
}

export function useMarketingIntegrations() {
  return useQuery({
    queryKey: marketingKeys.integrations(),
    queryFn: async () => (await api.get<{ providers: ProviderInfo[] }>('/marketing/integrations')).data.providers,
    staleTime: 5 * 60_000,
  });
}

/** Imports the viewer may run (each needs its module's edit permission). */
export function useImportDefinitions() {
  return useQuery({
    queryKey: marketingKeys.imports(),
    queryFn: async () => (await api.get<ImportDefinition[]>('/marketing/imports')).data,
    staleTime: 5 * 60_000,
  });
}

function fileForm(file: File): FormData {
  const form = new FormData();
  form.append('file', file);
  return form;
}

export function usePreviewImport(type: string) {
  return useMutation({
    mutationFn: async (file: File) => (await api.post<ImportPreview>(`/marketing/imports/${type}/preview`, fileForm(file))).data,
  });
}

/** Commits the previewed file. Every marketing figure may change, so all marketing queries are refreshed. */
export function useCommitImport(type: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ file, checksum, skipInvalid }: { file: File; checksum: string; skipInvalid: boolean }) =>
      (
        await api.post<ImportResult>(`/marketing/imports/${type}/commit`, fileForm(file), {
          params: { checksum, skipInvalid },
        })
      ).data,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: marketingKeys.all }),
  });
}

export function downloadImportTemplate(type: string): Promise<void> {
  return downloadFile(`/marketing/imports/${type}/template`, `${type}-template.csv`);
}
