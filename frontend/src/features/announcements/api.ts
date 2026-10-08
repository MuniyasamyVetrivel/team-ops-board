import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import type { PageResponse, UserSummary } from '@/lib/api/types';

export type AnnouncementPriority = 'NORMAL' | 'IMPORTANT' | 'URGENT';
export type AnnouncementState = 'SCHEDULED' | 'ACTIVE' | 'EXPIRED';

/** Mirrors AnnouncementDtos.AnnouncementItem. {@code stats} is only sent to people who can manage it. */
export interface Announcement {
  id: number;
  title: string;
  body: string;
  priority: AnnouncementPriority;
  targetDepartment: { id: number; name: string; code: string } | null;
  publishAt: string;
  expiresAt: string | null;
  ackRequired: boolean;
  createdBy: UserSummary | null;
  state: AnnouncementState;
  read: boolean;
  acknowledged: boolean;
  stats: { audience: number; read: number; acknowledged: number } | null;
  canManage: boolean;
  version: number;
}

export interface AnnouncementUnread {
  unread: number;
  awaitingAcknowledgement: number;
}

export interface SaveAnnouncementInput {
  version?: number;
  title: string;
  body: string;
  targetDepartmentId: number | null;
  priority: AnnouncementPriority;
  publishAt: string | null;
  expiresAt: string | null;
  ackRequired: boolean;
}

export const announcementKeys = {
  all: ['announcements'] as const,
  list: (state: AnnouncementState, page: number) => [...announcementKeys.all, 'list', state, page] as const,
  unread: () => [...announcementKeys.all, 'unread'] as const,
};

export function useAnnouncements(state: AnnouncementState, page: number) {
  return useQuery({
    queryKey: announcementKeys.list(state, page),
    queryFn: async () => (await api.get<PageResponse<Announcement>>('/announcements', { params: { state, page, size: 20 } })).data,
    placeholderData: keepPreviousData,
  });
}

export function useAnnouncementUnread() {
  return useQuery({
    queryKey: announcementKeys.unread(),
    queryFn: async () => (await api.get<AnnouncementUnread>('/announcements/unread-count')).data,
    refetchInterval: 120_000,
  });
}

function useAnnouncementMutation<V, R>(mutationFn: (variables: V) => Promise<R>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: announcementKeys.all }),
  });
}

export function useSaveAnnouncement(id: number | null) {
  return useAnnouncementMutation(async (input: SaveAnnouncementInput) =>
    (id === null ? await api.post<Announcement>('/announcements', input) : await api.put<Announcement>(`/announcements/${id}`, input)).data,
  );
}

export function useDeleteAnnouncement() {
  return useAnnouncementMutation(async (id: number) => {
    await api.delete(`/announcements/${id}`);
  });
}

export function useMarkAnnouncementRead() {
  return useAnnouncementMutation(async (id: number) => {
    await api.post(`/announcements/${id}/read`);
  });
}

export function useAcknowledgeAnnouncement() {
  return useAnnouncementMutation(async (id: number) => {
    await api.post(`/announcements/${id}/acknowledge`);
  });
}
