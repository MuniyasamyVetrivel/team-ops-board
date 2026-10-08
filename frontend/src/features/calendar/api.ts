import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { DueState, TaskPriority, TaskStatus } from '@/features/tasks/types';
import { api } from '@/lib/api/client';
import type { UserSummary } from '@/lib/api/types';

export type CalendarEventType = 'TEAM_EVENT' | 'MEETING' | 'IMPORTANT_DATE' | 'LEAVE';
export type CalendarItemKind = 'EVENT' | 'TASK_DEADLINE' | 'MILESTONE' | 'APPROVAL_DUE';

/** Mirrors com.teamops.calendar.dto.CalendarDtos.CalendarItem. Dates are business-zone LocalDates. */
export interface CalendarItem {
  key: string;
  kind: CalendarItemKind;
  id: number;
  title: string;
  eventType: CalendarEventType | null;
  allDay: boolean;
  startDate: string;
  endDate: string;
  startAt: string | null;
  endAt: string | null;
  department: { id: number; name: string; code: string } | null;
  user: UserSummary | null;
  task: { code: string; status: TaskStatus; priority: TaskPriority; dueState: DueState } | null;
  /** Project code for milestones, approval or task code otherwise. */
  reference: string | null;
  /** The project, for milestones. */
  parentId: number | null;
}

/** Mirrors CalendarDtos.EventDetail. */
export interface CalendarEventDetail {
  id: number;
  title: string;
  description: string | null;
  eventType: CalendarEventType;
  allDay: boolean;
  startDate: string;
  endDate: string;
  startAt: string;
  endAt: string;
  department: { id: number; name: string; code: string } | null;
  user: UserSummary | null;
  createdBy: UserSummary | null;
  version: number;
  canEdit: boolean;
}

/** Mirrors CalendarDtos.SaveEvent: all-day events use dates, timed events use instants. */
export interface SaveEventInput {
  version?: number;
  title: string;
  description: string | null;
  eventType: CalendarEventType;
  allDay: boolean;
  startDate: string | null;
  endDate: string | null;
  startAt: string | null;
  endAt: string | null;
  departmentId: number | null;
  userId: number | null;
}

export interface CalendarResponse {
  today: string;
  from: string;
  to: string;
  items: CalendarItem[];
}

export interface CalendarQuery {
  from: string;
  to: string;
  mine?: boolean;
}

export const calendarKeys = {
  all: ['calendar'] as const,
  range: (query: CalendarQuery) => [...calendarKeys.all, query] as const,
  event: (id: number) => [...calendarKeys.all, 'event', id] as const,
};

export function useCalendar(query: CalendarQuery, enabled = true) {
  return useQuery({
    queryKey: calendarKeys.range(query),
    queryFn: async () => (await api.get<CalendarResponse>('/calendar', { params: query })).data,
    enabled,
  });
}

export function useCalendarEvent(id: number | null) {
  return useQuery({
    queryKey: calendarKeys.event(id ?? 0),
    queryFn: async () => (await api.get<CalendarEventDetail>(`/calendar/events/${id}`)).data,
    enabled: id !== null,
  });
}

function useEventMutation<V, R>(mutationFn: (variables: V) => Promise<R>) {
  const queryClient = useQueryClient();
  return useMutation({ mutationFn, onSuccess: () => void queryClient.invalidateQueries({ queryKey: calendarKeys.all }) });
}

export function useSaveEvent(id: number | null) {
  return useEventMutation(async (input: SaveEventInput) =>
    (id === null ? await api.post<CalendarEventDetail>('/calendar/events', input) : await api.put<CalendarEventDetail>(`/calendar/events/${id}`, input)).data,
  );
}

export function useDeleteEvent() {
  return useEventMutation(async (id: number) => {
    await api.delete(`/calendar/events/${id}`);
  });
}
