import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { TicketListItem, TicketPriority } from '@/features/tickets/types';
import { api } from '@/lib/api/client';

/** Mirrors SlaDtos.PolicyResponse. */
export interface SlaPolicy {
  id: number;
  name: string;
  priority: TicketPriority;
  firstResponseMinutes: number;
  resolutionMinutes: number;
  version: number;
}

export interface SlaPriorityRow {
  priority: TicketPriority;
  firstResponseMinutes: number;
  resolutionMinutes: number;
  created: number;
  firstResponseCompliance: number | null;
  resolutionCompliance: number | null;
  openBreached: number;
}

/** Mirrors SlaDtos.Summary. Compliance values are null ("—") when nothing in the window is decided yet. */
export interface SlaSummary {
  windowDays: number;
  generatedAt: string;
  warningThresholdPct: number;
  created: number;
  firstResponseCompliance: number | null;
  resolutionCompliance: number | null;
  open: number;
  onTrack: number;
  warning: number;
  breached: number;
  paused: number;
  priorities: SlaPriorityRow[];
  atRisk: TicketListItem[];
}

export interface UpdateSlaPolicyInput {
  version: number;
  firstResponseMinutes: number;
  resolutionMinutes: number;
}

export const slaKeys = {
  all: ['sla'] as const,
  policies: () => [...slaKeys.all, 'policies'] as const,
  summary: (days: number) => [...slaKeys.all, 'summary', days] as const,
};

export function useSlaPolicies() {
  return useQuery({
    queryKey: slaKeys.policies(),
    queryFn: async () => (await api.get<SlaPolicy[]>('/sla/policies')).data,
    staleTime: 5 * 60_000,
  });
}

export function useSlaSummary(days: number) {
  return useQuery({
    queryKey: slaKeys.summary(days),
    queryFn: async () => (await api.get<SlaSummary>('/sla/summary', { params: { days } })).data,
    placeholderData: keepPreviousData,
  });
}

export function useUpdateSlaPolicy(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: UpdateSlaPolicyInput) => (await api.put<SlaPolicy>(`/sla/policies/${id}`, input)).data,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: slaKeys.all }),
  });
}
