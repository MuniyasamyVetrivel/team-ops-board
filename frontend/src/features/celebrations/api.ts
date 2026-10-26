import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { CalendarResponse } from '@/features/calendar/api';
import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

/** Mirrors com.teamops.celebration.entity.OccasionCategory: the calendar's celebration and marketing categories. */
export type OccasionCategory = 'BIRTHDAY' | 'WORK_ANNIVERSARY' | 'IMPORTANT_DAY' | 'EVENT' | 'WEBINAR' | 'EVENT_MARKETING' | 'EMAIL_MARKETING';
/** What a LinkedIn post or attachment belongs to (birthdays and anniversaries belong to the employee). */
export type OccasionKind = 'BIRTHDAY' | 'WORK_ANNIVERSARY' | 'IMPORTANT_DAY' | 'EVENT';
export type LinkedInPostStatus = 'NOT_STARTED' | 'DRAFTED' | 'SCHEDULED' | 'POSTED';
export type EmployeeStatus = 'ACTIVE' | 'LEFT';
export type ImportantDayCategory = 'FESTIVAL' | 'NATIONAL_DAY' | 'INDUSTRY_DAY' | 'COMPANY_MILESTONE' | 'OTHER';
export type Region = 'AUSTRALIA' | 'INDIA' | 'GLOBAL';
export type DayRecurrence = 'YEARLY' | 'ONE_OFF';
export type MarketingEventType = 'UPCOMING_EVENT' | 'WEBINAR' | 'EVENT_MARKETING' | 'EMAIL_MARKETING';
export type MarketingEventStatus = 'PLANNED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';

export const LINKEDIN_STATUSES: LinkedInPostStatus[] = ['NOT_STARTED', 'DRAFTED', 'SCHEDULED', 'POSTED'];
export const DAY_CATEGORIES: ImportantDayCategory[] = ['FESTIVAL', 'NATIONAL_DAY', 'INDUSTRY_DAY', 'COMPANY_MILESTONE', 'OTHER'];
export const REGIONS: Region[] = ['AUSTRALIA', 'INDIA', 'GLOBAL'];
export const EVENT_TYPES: MarketingEventType[] = ['UPCOMING_EVENT', 'WEBINAR', 'EVENT_MARKETING', 'EMAIL_MARKETING'];
export const EVENT_STATUSES: MarketingEventStatus[] = ['PLANNED', 'CONFIRMED', 'COMPLETED', 'CANCELLED'];

/** Mirrors CelebrationDtos.LinkedInPostDto. `saved` is false until someone decides; `needed` is then the kind's default. */
export interface LinkedInPost {
  needed: boolean;
  saved: boolean;
  status: LinkedInPostStatus;
  owner: UserSummary | null;
  postDate: string | null;
  postLink: string | null;
  version: number | null;
}

/** Mirrors CelebrationDtos.OccasionInfo (birthdays, anniversaries, important days, marketing events, email campaigns). */
export interface OccasionInfo {
  category: OccasionCategory;
  /** Null for a scheduled email campaign (no LinkedIn tracking). */
  subjectType: OccasionKind | null;
  subjectId: number;
  detail: string | null;
  /** Completed years on a work anniversary. */
  years: number | null;
  dayCategory: ImportantDayCategory | null;
  region: Region | null;
  eventType: MarketingEventType | null;
  eventStatus: MarketingEventStatus | null;
  location: string | null;
  linkedIn: LinkedInPost | null;
}

/** Mirrors CelebrationDtos.Attachment. */
export interface OccasionAttachment {
  fileId: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  addedBy: UserSummary | null;
  addedAt: string;
}

/** Mirrors CelebrationDtos.EmployeeDto. `dateOfBirth` (with the year) only for Super Admins and CELEBRATIONS_EDIT. */
export interface Employee {
  id: number;
  employeeCode: string;
  firstName: string;
  lastName: string;
  fullName: string;
  designation: string;
  reportingManager: string;
  department: { id: number; name: string; code: string } | null;
  workEmail: string | null;
  officeLocation: string | null;
  hasPhoto: boolean;
  user: UserSummary | null;
  status: EmployeeStatus;
  birthMonth: number;
  birthDay: number;
  dateOfBirth: string | null;
  dateOfJoining: string | null;
  nextBirthday: string;
  nextAnniversary: string | null;
  nextAnniversaryYears: number | null;
  version: number;
  canEdit: boolean;
}

/** Mirrors CelebrationDtos.SaveEmployee; `version` only when updating. */
export interface SaveEmployeeInput {
  version?: number;
  employeeCode: string;
  firstName: string | null;
  lastName: string | null;
  dateOfBirth: string;
  designation: string | null;
  reportingManager: string | null;
  dateOfJoining: string | null;
  departmentId: number | null;
  workEmail: string | null;
  officeLocation: string | null;
  userId: number | null;
  status: EmployeeStatus;
}

/** Mirrors CelebrationDtos.ImportantDayDto. */
export interface ImportantDay {
  id: number;
  name: string;
  date: string;
  category: ImportantDayCategory;
  region: Region;
  recurrence: DayRecurrence;
  description: string | null;
  marketingNotes: string | null;
  nextOccurrence: string;
  attachmentCount: number;
  /** Post tracking of `nextOccurrence`. */
  linkedIn: LinkedInPost;
  version: number;
  canEdit: boolean;
}

export interface SaveImportantDayInput {
  version?: number;
  name: string;
  date: string;
  category: ImportantDayCategory;
  region: Region;
  recurrence: DayRecurrence;
  description: string | null;
  marketingNotes: string | null;
}

/** Mirrors CelebrationDtos.MarketingEventDto. `emailCampaign` only for CAMPAIGN_VIEW holders. */
export interface MarketingEvent {
  id: number;
  title: string;
  eventType: MarketingEventType;
  category: OccasionCategory;
  status: MarketingEventStatus;
  startAt: string;
  endAt: string | null;
  startDate: string;
  location: string | null;
  onlineLink: string | null;
  registrationLink: string | null;
  description: string | null;
  owner: UserSummary;
  emailCampaignId: number | null;
  emailCampaign: { id: number; name: string; status: string; campaignDate: string } | null;
  attachmentCount: number;
  linkedIn: LinkedInPost;
  version: number;
  canEdit: boolean;
}

export interface SaveMarketingEventInput {
  version?: number;
  title: string;
  eventType: MarketingEventType;
  status: MarketingEventStatus;
  startAt: string;
  endAt: string | null;
  location: string | null;
  onlineLink: string | null;
  registrationLink: string | null;
  description: string | null;
  ownerId: number;
  emailCampaignId: number | null;
}

/** Mirrors CelebrationDtos.LinkedInPostRow. */
export interface LinkedInPostRow {
  key: string;
  subjectType: OccasionKind;
  subjectId: number;
  category: OccasionCategory;
  title: string;
  occurrenceDate: string;
  region: Region | null;
  linkedIn: LinkedInPost;
}

/** Mirrors CelebrationDtos.SaveLinkedInPost: `version` from the saved post, null before the first save. */
export interface SaveLinkedInPostInput {
  subjectType: OccasionKind;
  subjectId: number;
  occurrenceDate: string;
  postNeeded: boolean;
  ownerId: number | null;
  status: LinkedInPostStatus;
  postDate: string | null;
  postLink: string | null;
  version: number | null;
}

export interface EmployeeQuery {
  search?: string;
  status?: EmployeeStatus;
  departmentId?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ImportantDayQuery {
  search?: string;
  category?: ImportantDayCategory[];
  region?: Region[];
  recurrence?: DayRecurrence;
  year?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface MarketingEventQuery {
  search?: string;
  type?: MarketingEventType[];
  status?: MarketingEventStatus[];
  from?: string;
  to?: string;
  ownerId?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export interface LinkedInPostQuery {
  from?: string;
  to?: string;
  status?: LinkedInPostStatus[];
  needed?: boolean;
  kind?: OccasionKind[];
  page?: number;
  size?: number;
}

/** CSV import types (through the marketing import endpoints, with CELEBRATIONS_EDIT). */
export const EMPLOYEE_IMPORT = 'employees';
export const IMPORTANT_DAY_IMPORT = 'important-days';

export const celebrationKeys = {
  all: ['celebrations'] as const,
  employees: (query: EmployeeQuery) => [...celebrationKeys.all, 'employees', query] as const,
  employee: (id: number) => [...celebrationKeys.all, 'employee', id] as const,
  photo: (id: number, version: number) => [...celebrationKeys.all, 'photo', id, version] as const,
  days: (query: ImportantDayQuery) => [...celebrationKeys.all, 'days', query] as const,
  day: (id: number) => [...celebrationKeys.all, 'day', id] as const,
  events: (query: MarketingEventQuery) => [...celebrationKeys.all, 'events', query] as const,
  event: (id: number) => [...celebrationKeys.all, 'event', id] as const,
  files: (kind: 'important-days' | 'events', id: number) => [...celebrationKeys.all, 'files', kind, id] as const,
  upcoming: (days: number) => [...celebrationKeys.all, 'upcoming', days] as const,
  linkedIn: (query: LinkedInPostQuery) => [...celebrationKeys.all, 'linkedin', query] as const,
};

const get = <T,>(url: string, params?: object) =>
  api.get<T>(url, params ? { params: cleanParams(params), paramsSerializer: serializeParams } : undefined).then((response) => response.data);

export function useEmployees(query: EmployeeQuery) {
  return useQuery({ queryKey: celebrationKeys.employees(query), queryFn: () => get<PageResponse<Employee>>('/celebrations/employees', query), placeholderData: keepPreviousData });
}

export function useEmployee(id: number | null) {
  return useQuery({ queryKey: celebrationKeys.employee(id ?? 0), queryFn: () => get<Employee>(`/celebrations/employees/${id}`), enabled: id !== null });
}

/** The photo as a blob (fetched with the bearer token); only asked for when the employee has one. */
export function useEmployeePhoto(employee: Pick<Employee, 'id' | 'hasPhoto' | 'version'>) {
  return useQuery({
    queryKey: celebrationKeys.photo(employee.id, employee.version),
    queryFn: async () => (await api.get<Blob>(`/celebrations/employees/${employee.id}/photo`, { responseType: 'blob' })).data,
    enabled: employee.hasPhoto,
    staleTime: Infinity,
  });
}

export function useImportantDays(query: ImportantDayQuery) {
  return useQuery({ queryKey: celebrationKeys.days(query), queryFn: () => get<PageResponse<ImportantDay>>('/celebrations/important-days', query), placeholderData: keepPreviousData });
}

export function useImportantDay(id: number | null) {
  return useQuery({ queryKey: celebrationKeys.day(id ?? 0), queryFn: () => get<ImportantDay>(`/celebrations/important-days/${id}`), enabled: id !== null });
}

export function useMarketingEvents(query: MarketingEventQuery) {
  return useQuery({ queryKey: celebrationKeys.events(query), queryFn: () => get<PageResponse<MarketingEvent>>('/celebrations/events', query), placeholderData: keepPreviousData });
}

export function useMarketingEvent(id: number | null) {
  return useQuery({ queryKey: celebrationKeys.event(id ?? 0), queryFn: () => get<MarketingEvent>(`/celebrations/events/${id}`), enabled: id !== null });
}

/** "Coming up": birthdays, anniversaries, important days and events from today through the next `days` days. */
export function useUpcoming(days = 14, enabled = true) {
  return useQuery({ queryKey: celebrationKeys.upcoming(days), queryFn: () => get<CalendarResponse>('/celebrations/upcoming', { days }), enabled });
}

export function useLinkedInPosts(query: LinkedInPostQuery) {
  return useQuery({ queryKey: celebrationKeys.linkedIn(query), queryFn: () => get<PageResponse<LinkedInPostRow>>('/celebrations/linkedin-posts', query), placeholderData: keepPreviousData });
}

/**
 * Any change can move the calendar, "Coming up" and the LinkedIn list, so all celebration and calendar queries are
 * refreshed; a returned record replaces its cached detail.
 */
function useCelebrationMutation<V, R>(mutationFn: (variables: V) => Promise<R>, detailKey?: (result: R) => readonly unknown[]) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (result) => {
      if (detailKey) queryClient.setQueryData(detailKey(result), result);
      void queryClient.invalidateQueries({ queryKey: celebrationKeys.all });
      void queryClient.invalidateQueries({ queryKey: ['calendar'] });
    },
  });
}

export function useSaveEmployee(id: number | null) {
  return useCelebrationMutation(
    async (input: SaveEmployeeInput) => (id === null ? await api.post<Employee>('/celebrations/employees', input) : await api.put<Employee>(`/celebrations/employees/${id}`, input)).data,
    (employee) => celebrationKeys.employee(employee.id),
  );
}

export function useDeleteEmployee() {
  return useCelebrationMutation(async (id: number) => {
    await api.delete(`/celebrations/employees/${id}`);
  });
}

export function useEmployeePhotoMutations(id: number) {
  const detail = (employee: Employee) => celebrationKeys.employee(employee.id);
  return {
    upload: useCelebrationMutation(async (file: File) => {
      const form = new FormData();
      form.append('file', file);
      return (await api.put<Employee>(`/celebrations/employees/${id}/photo`, form)).data;
    }, detail),
    remove: useCelebrationMutation(async () => (await api.delete<Employee>(`/celebrations/employees/${id}/photo`)).data, detail),
  };
}

export function useSaveImportantDay(id: number | null) {
  return useCelebrationMutation(
    async (input: SaveImportantDayInput) =>
      (id === null ? await api.post<ImportantDay>('/celebrations/important-days', input) : await api.put<ImportantDay>(`/celebrations/important-days/${id}`, input)).data,
    (day) => celebrationKeys.day(day.id),
  );
}

export function useDeleteImportantDay() {
  return useCelebrationMutation(async (id: number) => {
    await api.delete(`/celebrations/important-days/${id}`);
  });
}

export function useSaveMarketingEvent(id: number | null) {
  return useCelebrationMutation(
    async (input: SaveMarketingEventInput) =>
      (id === null ? await api.post<MarketingEvent>('/celebrations/events', input) : await api.put<MarketingEvent>(`/celebrations/events/${id}`, input)).data,
    (event) => celebrationKeys.event(event.id),
  );
}

export function useDeleteMarketingEvent() {
  return useCelebrationMutation(async (id: number) => {
    await api.delete(`/celebrations/events/${id}`);
  });
}

export function useSaveLinkedInPost() {
  return useCelebrationMutation(async (input: SaveLinkedInPostInput) => (await api.put<LinkedInPost>('/celebrations/linkedin-posts', input)).data);
}

/** Files of an important day (creatives) or an event. */
export function useOccasionFiles(kind: 'important-days' | 'events', id: number) {
  return useQuery({ queryKey: celebrationKeys.files(kind, id), queryFn: () => get<OccasionAttachment[]>(`/celebrations/${kind}/${id}/attachments`) });
}

export function useOccasionFileMutations(kind: 'important-days' | 'events', id: number) {
  const queryClient = useQueryClient();
  const onSuccess = (files: OccasionAttachment[]) => {
    queryClient.setQueryData(celebrationKeys.files(kind, id), files);
    void queryClient.invalidateQueries({ queryKey: [...celebrationKeys.all, kind === 'events' ? 'events' : 'days'] });
    void queryClient.invalidateQueries({ queryKey: kind === 'events' ? celebrationKeys.event(id) : celebrationKeys.day(id) });
  };
  return {
    upload: useMutation({
      mutationFn: async (file: File) => {
        const form = new FormData();
        form.append('file', file);
        return (await api.post<OccasionAttachment[]>(`/celebrations/${kind}/${id}/attachments`, form)).data;
      },
      onSuccess,
    }),
    remove: useMutation({
      mutationFn: async (fileId: number) => (await api.delete<OccasionAttachment[]>(`/celebrations/${kind}/${id}/attachments/${fileId}`)).data,
      onSuccess,
    }),
  };
}

export function downloadOccasionFile(kind: 'important-days' | 'events', id: number, fileId: number, fileName: string): Promise<void> {
  return downloadFile(`/celebrations/${kind}/${id}/attachments/${fileId}`, fileName);
}

/** CSV in the import's columns for the filters on screen (full birth dates: editors only). */
export function exportEmployees(query: Omit<EmployeeQuery, 'page' | 'size' | 'sort'>): Promise<void> {
  const search = serializeParams(cleanParams(query));
  return downloadFile(`/celebrations/employees/export${search ? `?${search}` : ''}`, 'employees.csv');
}

export function exportImportantDays(query: Omit<ImportantDayQuery, 'page' | 'size' | 'sort'>): Promise<void> {
  const search = serializeParams(cleanParams(query));
  return downloadFile(`/celebrations/important-days/export${search ? `?${search}` : ''}`, 'important-days.csv');
}
