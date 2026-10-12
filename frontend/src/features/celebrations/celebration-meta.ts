import {
  Award,
  Cake,
  CalendarCheck,
  CalendarClock,
  CalendarHeart,
  CalendarX,
  CircleCheck,
  CircleDashed,
  Clock,
  Mail,
  Megaphone,
  MonitorPlay,
  PenLine,
  Sparkles,
  type LucideIcon,
} from 'lucide-react';

import type { BadgeProps } from '@/components/ui/badge';
import { parseLocalDate } from '@/features/tasks/task-meta';

import type {
  DayRecurrence,
  EmployeeStatus,
  ImportantDayCategory,
  LinkedInPostStatus,
  MarketingEventStatus,
  MarketingEventType,
  OccasionCategory,
  OccasionInfo,
  OccasionKind,
  Region,
} from './api';

type Tone = NonNullable<BadgeProps['tone']>;

/** Calendar order of the categories (filter chips and legend). */
export const OCCASION_CATEGORIES: OccasionCategory[] = ['BIRTHDAY', 'WORK_ANNIVERSARY', 'IMPORTANT_DAY', 'EVENT', 'WEBINAR', 'EVENT_MARKETING', 'EMAIL_MARKETING'];

/** Each category has its own icon, colour and label: colour is never the only signal. */
export const OCCASION_STYLE: Record<OccasionCategory, { label: string; icon: LucideIcon; className: string }> = {
  BIRTHDAY: { label: 'Birthday', icon: Cake, className: 'border-occasion-birthday/30 bg-occasion-birthday/10 text-occasion-birthday' },
  WORK_ANNIVERSARY: { label: 'Work anniversary', icon: Award, className: 'border-occasion-anniversary/30 bg-occasion-anniversary/10 text-occasion-anniversary' },
  IMPORTANT_DAY: { label: 'Important day', icon: Sparkles, className: 'border-occasion-day/30 bg-occasion-day/10 text-occasion-day' },
  EVENT: { label: 'Event', icon: CalendarHeart, className: 'border-occasion-event/30 bg-occasion-event/10 text-occasion-event' },
  WEBINAR: { label: 'Webinar', icon: MonitorPlay, className: 'border-occasion-webinar/30 bg-occasion-webinar/10 text-occasion-webinar' },
  EVENT_MARKETING: { label: 'Event marketing', icon: Megaphone, className: 'border-occasion-event-marketing/30 bg-occasion-event-marketing/10 text-occasion-event-marketing' },
  EMAIL_MARKETING: { label: 'Email marketing', icon: Mail, className: 'border-occasion-email/30 bg-occasion-email/10 text-occasion-email' },
};

export const EVENT_TYPE_CATEGORY: Record<MarketingEventType, OccasionCategory> = {
  UPCOMING_EVENT: 'EVENT',
  WEBINAR: 'WEBINAR',
  EVENT_MARKETING: 'EVENT_MARKETING',
  EMAIL_MARKETING: 'EMAIL_MARKETING',
};

export const EVENT_TYPE_LABELS: Record<MarketingEventType, string> = {
  UPCOMING_EVENT: 'Upcoming event',
  WEBINAR: 'Webinar',
  EVENT_MARKETING: 'Event marketing',
  EMAIL_MARKETING: 'Email marketing',
};

export const EVENT_STATUS_STYLE: Record<MarketingEventStatus, { label: string; tone: Tone; icon: LucideIcon }> = {
  PLANNED: { label: 'Planned', tone: 'neutral', icon: CalendarClock },
  CONFIRMED: { label: 'Confirmed', tone: 'primary', icon: CalendarCheck },
  COMPLETED: { label: 'Completed', tone: 'success', icon: CircleCheck },
  CANCELLED: { label: 'Cancelled', tone: 'danger', icon: CalendarX },
};

export const LINKEDIN_STYLE: Record<LinkedInPostStatus, { label: string; tone: Tone; icon: LucideIcon }> = {
  NOT_STARTED: { label: 'Not started', tone: 'warning', icon: CircleDashed },
  DRAFTED: { label: 'Drafted', tone: 'primary', icon: PenLine },
  SCHEDULED: { label: 'Scheduled', tone: 'highlight', icon: Clock },
  POSTED: { label: 'Posted', tone: 'success', icon: CircleCheck },
};

export const DAY_CATEGORY_LABELS: Record<ImportantDayCategory, string> = {
  FESTIVAL: 'Festival',
  NATIONAL_DAY: 'National day',
  INDUSTRY_DAY: 'Industry day',
  COMPANY_MILESTONE: 'Company milestone',
  OTHER: 'Other',
};

export const REGION_LABELS: Record<Region, string> = { AUSTRALIA: 'Australia', INDIA: 'India', GLOBAL: 'Global' };

export const RECURRENCE_LABELS: Record<DayRecurrence, string> = { YEARLY: 'Every year', ONE_OFF: 'One-off' };

export const EMPLOYEE_STATUS_LABELS: Record<EmployeeStatus, string> = { ACTIVE: 'Active', LEFT: 'Left' };

export const KIND_LABELS: Record<OccasionKind, string> = {
  BIRTHDAY: 'Birthdays',
  WORK_ANNIVERSARY: 'Work anniversaries',
  IMPORTANT_DAY: 'Important days',
  EVENT: 'Events',
};

const dayMonthFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'long' });
const dateFormat = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
const shortDateFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
const timeFormat = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' });

/** "14 October": a birthday without its year (the year is private). */
export function dayMonth(month: number, day: number): string {
  // 2000 is a leap year, so 29 February formats too.
  return dayMonthFormat.format(new Date(2000, month - 1, day));
}

/** A business-zone date ("2026-10-14") as "Wed, 14 Oct 2026". */
export function formatOccasionDate(iso: string): string {
  return dateFormat.format(parseLocalDate(iso));
}

export function formatShortDate(iso: string | null): string | null {
  return iso ? shortDateFormat.format(parseLocalDate(iso)) : null;
}

export function formatTime(instant: string): string {
  return timeFormat.format(new Date(instant));
}

/** "1 year", "5 years". */
export function yearsLabel(years: number): string {
  return `${years} ${years === 1 ? 'year' : 'years'}`;
}

/** Days from `today` to `date` as "Today", "Tomorrow" or "In 5 days". */
export function relativeDay(date: string, today: string): string {
  const days = Math.round((parseLocalDate(date).getTime() - parseLocalDate(today).getTime()) / 86_400_000);
  if (days === 0) return 'Today';
  if (days === 1) return 'Tomorrow';
  return days > 1 ? `In ${days} days` : `${-days} days ago`;
}

/** A full http(s) link, as the server accepts (CelebrationText.link). */
export function isHttpLink(value: string): boolean {
  try {
    const url = new URL(value);
    return (url.protocol === 'http:' || url.protocol === 'https:') && url.hostname !== '';
  } catch {
    return false;
  }
}

/** Where the record behind an occasion is managed. */
export function occasionHref(occasion: Pick<OccasionInfo, 'subjectType' | 'subjectId'>): string {
  switch (occasion.subjectType) {
    case 'BIRTHDAY':
    case 'WORK_ANNIVERSARY':
      return `/celebrations/employees?employee=${occasion.subjectId}`;
    case 'IMPORTANT_DAY':
      return `/celebrations/important-days?day=${occasion.subjectId}`;
    case 'EVENT':
      return `/celebrations/events?event=${occasion.subjectId}`;
    default:
      return '/digital-marketing/email-campaigns';
  }
}
