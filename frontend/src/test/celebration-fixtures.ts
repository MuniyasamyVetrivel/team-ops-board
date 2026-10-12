import type { CalendarItem } from '@/features/calendar/api';
import type { Employee, ImportantDay, LinkedInPost, LinkedInPostRow, MarketingEvent, OccasionInfo } from '@/features/celebrations/api';
import type { PageResponse } from '@/lib/api/types';

import { makeUser, webEmployee } from './fixtures';

export const priyaUser = { id: 11, fullName: 'Priya Menon', email: 'priya.menon@teamops.local', jobTitle: 'Digital Marketing Manager', status: 'ACTIVE' as const };

/** A Digital Marketing user who manages celebrations. */
export const celebrationEditor = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'MARKETING_VIEW', 'CELEBRATIONS_EDIT'], { id: 11, fullName: 'Priya Menon' });

export function page<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: 25, totalElements: content.length, totalPages: 1 };
}

export function linkedInPost(overrides: Partial<LinkedInPost> = {}): LinkedInPost {
  return { needed: true, saved: false, status: 'NOT_STARTED', owner: null, postDate: null, postLink: null, version: null, ...overrides };
}

export function employee(overrides: Partial<Employee> = {}): Employee {
  return {
    id: 40,
    employeeCode: 'TRI-0101',
    firstName: 'Priya',
    lastName: 'Menon',
    fullName: 'Priya Menon',
    designation: 'Digital Marketing Manager',
    reportingManager: 'Rakesh',
    department: { id: 7, name: 'Digital Marketing', code: 'DM' },
    workEmail: 'priya.menon@tritusa.example',
    officeLocation: 'Chennai',
    hasPhoto: false,
    user: null,
    status: 'ACTIVE',
    birthMonth: 10,
    birthDay: 14,
    dateOfBirth: null,
    dateOfJoining: '2021-10-20',
    nextBirthday: '2026-10-14',
    nextAnniversary: '2026-10-20',
    nextAnniversaryYears: 5,
    version: 0,
    canEdit: false,
    ...overrides,
  };
}

export function importantDay(overrides: Partial<ImportantDay> = {}): ImportantDay {
  return {
    id: 4,
    name: 'Diwali',
    date: '2026-11-08',
    category: 'FESTIVAL',
    region: 'INDIA',
    recurrence: 'ONE_OFF',
    description: 'Festival of lights.',
    marketingNotes: 'Greeting creative and a LinkedIn post.',
    nextOccurrence: '2026-11-08',
    attachmentCount: 1,
    linkedIn: linkedInPost(),
    version: 0,
    canEdit: false,
    ...overrides,
  };
}

export function marketingEvent(overrides: Partial<MarketingEvent> = {}): MarketingEvent {
  return {
    id: 9,
    title: 'Webinar: Quality engineering for SAP S/4HANA',
    eventType: 'WEBINAR',
    category: 'WEBINAR',
    status: 'CONFIRMED',
    startAt: '2026-10-18T09:30:00Z',
    endAt: '2026-10-18T10:30:00Z',
    startDate: '2026-10-18',
    location: null,
    onlineLink: 'https://www.example.com/live',
    registrationLink: 'https://www.example.com/register',
    description: 'Live demo.',
    owner: priyaUser,
    emailCampaignId: null,
    emailCampaign: null,
    attachmentCount: 0,
    linkedIn: linkedInPost({ saved: true, status: 'DRAFTED', version: 0, owner: priyaUser }),
    version: 0,
    canEdit: false,
    ...overrides,
  };
}

export function linkedInRow(overrides: Partial<LinkedInPostRow> = {}): LinkedInPostRow {
  return {
    key: 'IMPORTANT_DAY-4-2026-11-08',
    subjectType: 'IMPORTANT_DAY',
    subjectId: 4,
    category: 'IMPORTANT_DAY',
    title: 'Diwali',
    occurrenceDate: '2026-11-08',
    region: 'INDIA',
    linkedIn: linkedInPost(),
    ...overrides,
  };
}

function occasion(overrides: Partial<OccasionInfo>): OccasionInfo {
  return {
    category: 'BIRTHDAY',
    subjectType: 'BIRTHDAY',
    subjectId: 40,
    detail: 'Digital Marketing Manager',
    years: null,
    dayCategory: null,
    region: null,
    eventType: null,
    eventStatus: null,
    location: null,
    linkedIn: linkedInPost({ needed: false }),
    ...overrides,
  };
}

/** A calendar item for a birthday (or, with overrides, another occasion) on `date`. */
export function occasionItem(date: string, overrides: Partial<CalendarItem> = {}, info: Partial<OccasionInfo> = {}): CalendarItem {
  return {
    key: `BIRTHDAY-40-${date}`,
    kind: 'BIRTHDAY',
    id: 40,
    title: "Priya Menon's birthday",
    eventType: null,
    allDay: true,
    startDate: date,
    endDate: date,
    startAt: null,
    endAt: null,
    department: { id: 7, name: 'Digital Marketing', code: 'DM' },
    user: null,
    task: null,
    reference: null,
    parentId: null,
    occasion: occasion(info),
    ...overrides,
  };
}
