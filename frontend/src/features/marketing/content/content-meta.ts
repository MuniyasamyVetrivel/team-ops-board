import { CalendarClock, CircleCheck, FileText, Lightbulb, PencilLine, RefreshCw, type LucideIcon } from 'lucide-react';

import type { BadgeProps } from '@/components/ui/badge';

import type { ContentStatus, ContentType } from './api';

export const CONTENT_STATUS_LABELS: Record<ContentStatus, string> = {
  IDEA: 'Idea',
  PLANNED: 'Planned',
  IN_PROGRESS: 'In progress',
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  UPDATED: 'Updated',
};

/** Every stage has an icon as well as a colour. */
export const CONTENT_STATUS_STYLE: Record<ContentStatus, { tone: BadgeProps['tone']; icon: LucideIcon }> = {
  IDEA: { tone: 'neutral', icon: Lightbulb },
  PLANNED: { tone: 'neutral', icon: CalendarClock },
  IN_PROGRESS: { tone: 'primary', icon: PencilLine },
  DRAFT: { tone: 'warning', icon: FileText },
  PUBLISHED: { tone: 'success', icon: CircleCheck },
  UPDATED: { tone: 'success', icon: RefreshCw },
};

export const CONTENT_TYPE_LABELS: Record<ContentType, string> = {
  BLOG: 'Blog post',
  CASE_STUDY: 'Case study',
  WHITEPAPER: 'Whitepaper',
  LANDING_PAGE: 'Landing page',
  OTHER: 'Other',
};

/** Live content (published or updated) counts in the month of its publication date. */
export function isLive(status: ContentStatus): boolean {
  return status === 'PUBLISHED' || status === 'UPDATED';
}

export interface ContentDates {
  status: ContentStatus;
  url: string;
  publicationDate: string;
  refreshedDate: string;
}

/**
 * Mirrors ContentRules.check for the form: the field and message of the first problem, or null. Live content needs
 * its URL and publication date (not in the future); an updated item its refreshed date, on or after publication;
 * content that is not live has neither date. Dates are ISO strings, so they compare as text.
 */
export function contentDateProblem(values: ContentDates, today: string): { field: keyof ContentDates; message: string } | null {
  const live = isLive(values.status);
  if (live && !values.publicationDate) return { field: 'publicationDate', message: 'Published content needs its publication date' };
  if (live && !values.url.trim()) return { field: 'url', message: 'Published content needs its URL' };
  if (!live && values.publicationDate) return { field: 'publicationDate', message: 'Only published content has a publication date; use the planned date' };
  if (values.status === 'UPDATED' && !values.refreshedDate) return { field: 'refreshedDate', message: 'Updated content needs the date it was refreshed' };
  if (values.status !== 'UPDATED' && values.refreshedDate) return { field: 'refreshedDate', message: 'Only updated content has a refreshed date' };
  if (values.publicationDate > today) return { field: 'publicationDate', message: 'The publication date cannot be in the future; keep it as a draft with a planned date' };
  if (values.refreshedDate > today) return { field: 'refreshedDate', message: 'The refreshed date cannot be in the future' };
  if (values.refreshedDate && values.publicationDate && values.refreshedDate < values.publicationDate) {
    return { field: 'refreshedDate', message: 'The refreshed date cannot be before the publication date' };
  }
  return null;
}
