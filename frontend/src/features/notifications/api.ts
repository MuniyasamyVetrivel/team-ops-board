import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import type { PageResponse } from '@/lib/api/types';

export type NotificationType = 'TASK_ASSIGNED' | 'TASK_DUE_SOON' | 'TASK_OVERDUE' | 'TICKET_ASSIGNED' | 'TICKET_UPDATED' | 'TICKET_REPLY';

/** Mirrors com.teamops.notification.dto.NotificationResponse. */
export interface AppNotification {
  id: number;
  type: NotificationType;
  title: string;
  body: string | null;
  entityType: string | null;
  entityId: number | null;
  read: boolean;
  createdAt: string;
}

export interface UnreadCount {
  unread: number;
}

/** Where a notification leads; tasks open in the Tasks page drawer. */
export function notificationHref(notification: AppNotification): string | null {
  if (notification.entityId === null) return null;
  if (notification.entityType === 'TASK') return `/tasks?task=${notification.entityId}`;
  if (notification.entityType === 'TICKET') return `/tickets?ticket=${notification.entityId}`;
  return null;
}

export const notificationKeys = {
  all: ['notifications'] as const,
  list: (unreadOnly: boolean) => [...notificationKeys.all, 'list', unreadOnly] as const,
  unread: () => [...notificationKeys.all, 'unread-count'] as const,
};

/** Polled so the bell stays current without a page reload. */
export const UNREAD_POLL_MS = 60_000;

export function useUnreadCount() {
  return useQuery({
    queryKey: notificationKeys.unread(),
    queryFn: async () => (await api.get<UnreadCount>('/notifications/unread-count')).data,
    refetchInterval: UNREAD_POLL_MS,
  });
}

export function useNotifications(unreadOnly: boolean, enabled = true) {
  return useQuery({
    queryKey: notificationKeys.list(unreadOnly),
    queryFn: async () =>
      (await api.get<PageResponse<AppNotification>>('/notifications', { params: { unread: unreadOnly, size: 20 } })).data,
    placeholderData: keepPreviousData,
    enabled,
  });
}

function useNotificationMutation<V, R>(mutationFn: (variables: V) => Promise<R>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: notificationKeys.all }),
  });
}

export function useMarkNotificationRead() {
  return useNotificationMutation(async (id: number) => (await api.post<AppNotification>(`/notifications/${id}/read`)).data);
}

export function useMarkAllNotificationsRead() {
  return useNotificationMutation(async () => (await api.post<UnreadCount>('/notifications/read-all')).data);
}
