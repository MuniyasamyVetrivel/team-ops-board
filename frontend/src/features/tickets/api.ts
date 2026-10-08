import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type PageResponse } from '@/lib/api/types';

import type {
  CreateTicketInput,
  MyTicketSummary,
  TicketCategory,
  TicketDetail,
  TicketListItem,
  TicketQuery,
  TicketStatus,
  UpdateTicketInput,
} from './types';

export const ticketKeys = {
  all: ['tickets'] as const,
  lists: () => [...ticketKeys.all, 'list'] as const,
  list: (query: TicketQuery) => [...ticketKeys.lists(), query] as const,
  detail: (id: number) => [...ticketKeys.all, 'detail', id] as const,
  summary: () => [...ticketKeys.all, 'my-summary'] as const,
  categories: () => [...ticketKeys.all, 'categories'] as const,
};

export function useTickets(query: TicketQuery, enabled = true) {
  return useQuery({
    queryKey: ticketKeys.list(query),
    queryFn: async () =>
      (await api.get<PageResponse<TicketListItem>>('/tickets', { params: cleanParams(query), paramsSerializer: serializeParams })).data,
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useTicket(id: number | null) {
  return useQuery({
    queryKey: ticketKeys.detail(id ?? 0),
    queryFn: async () => (await api.get<TicketDetail>(`/tickets/${id}`)).data,
    enabled: id !== null,
  });
}

export function useMyTicketSummary() {
  return useQuery({
    queryKey: ticketKeys.summary(),
    queryFn: async () => (await api.get<MyTicketSummary>('/tickets/my/summary')).data,
  });
}

export function useTicketCategories() {
  return useQuery({
    queryKey: ticketKeys.categories(),
    queryFn: async () => (await api.get<TicketCategory[]>('/tickets/categories')).data,
    staleTime: 5 * 60_000,
  });
}

/** Every ticket mutation returns the fresh detail: cache it and refresh lists, summaries, SLA and the dashboard. */
function useTicketMutation<V>(mutationFn: (variables: V) => Promise<TicketDetail>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (detail) => {
      queryClient.setQueryData(ticketKeys.detail(detail.id), detail);
      void queryClient.invalidateQueries({ queryKey: ticketKeys.lists() });
      void queryClient.invalidateQueries({ queryKey: ticketKeys.summary() });
      void queryClient.invalidateQueries({ queryKey: ['sla'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

const data = <T,>(promise: Promise<{ data: T }>) => promise.then((response) => response.data);

export function useCreateTicket() {
  return useTicketMutation((input: CreateTicketInput) => data(api.post<TicketDetail>('/tickets', input)));
}

export function useUpdateTicket(id: number) {
  return useTicketMutation((input: UpdateTicketInput) => data(api.put<TicketDetail>(`/tickets/${id}`, input)));
}

export function useChangeTicketStatus(id: number) {
  return useTicketMutation((status: TicketStatus) => data(api.put<TicketDetail>(`/tickets/${id}/status`, { status })));
}

export function useAssignTicket(id: number) {
  return useTicketMutation((assigneeId: number | null) => data(api.put<TicketDetail>(`/tickets/${id}/assignee`, { assigneeId })));
}

export function useTicketComments(id: number) {
  return {
    add: useTicketMutation(({ body, internal }: { body: string; internal: boolean }) =>
      data(api.post<TicketDetail>(`/tickets/${id}/comments`, { body, internal })),
    ),
    remove: useTicketMutation((commentId: number) => data(api.delete<TicketDetail>(`/tickets/${id}/comments/${commentId}`))),
  };
}

export function useTicketAttachments(id: number) {
  return {
    upload: useTicketMutation((file: File) => {
      const form = new FormData();
      form.append('file', file);
      return data(api.post<TicketDetail>(`/tickets/${id}/attachments`, form));
    }),
    remove: useTicketMutation((fileId: number) => data(api.delete<TicketDetail>(`/tickets/${id}/attachments/${fileId}`))),
  };
}

export async function downloadTicketAttachment(ticketId: number, fileId: number, fileName: string): Promise<void> {
  return downloadFile(`/tickets/${ticketId}/attachments/${fileId}`, fileName);
}

/** Full update payload from the current detail plus the changed fields. */
export function toUpdateTicketInput(ticket: TicketDetail, patch: Partial<Omit<UpdateTicketInput, 'version'>>): UpdateTicketInput {
  return {
    version: ticket.version,
    subject: ticket.subject,
    description: ticket.description,
    categoryId: ticket.category?.id ?? 0,
    departmentId: ticket.department.id,
    priority: ticket.priority,
    ...patch,
  };
}
