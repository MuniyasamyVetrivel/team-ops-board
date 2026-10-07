import { useQuery } from '@tanstack/react-query';

import type { DueState, TaskPriority, TaskStatus } from '@/features/tasks/types';
import { api } from '@/lib/api/client';
import type { UserSummary } from '@/lib/api/types';

export type CalendarEventType = 'TEAM_EVENT' | 'MEETING' | 'IMPORTANT_DATE' | 'LEAVE';
export type CalendarItemKind = 'EVENT' | 'TASK_DEADLINE';

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
};

export function useCalendar(query: CalendarQuery, enabled = true) {
  return useQuery({
    queryKey: calendarKeys.range(query),
    queryFn: async () => (await api.get<CalendarResponse>('/calendar', { params: query })).data,
    enabled,
  });
}
