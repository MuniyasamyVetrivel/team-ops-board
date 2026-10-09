import { CircleCheck, CircleX, Link2, Link2Off, Search, Send, type LucideIcon } from 'lucide-react';

import type { BadgeProps } from '@/components/ui/badge';

import type { BacklinkStatus, BacklinkType, DateField, Stage } from './api';

export const BACKLINK_STATUS_LABELS: Record<BacklinkStatus, string> = {
  PROSPECTED: 'Prospected',
  SUBMITTED: 'Submitted',
  APPROVED: 'Approved',
  LIVE: 'Live',
  REJECTED: 'Rejected',
  LOST: 'Lost',
};

/** Every status has an icon as well as a colour. */
export const BACKLINK_STATUS_STYLE: Record<BacklinkStatus, { tone: BadgeProps['tone']; icon: LucideIcon }> = {
  PROSPECTED: { tone: 'neutral', icon: Search },
  SUBMITTED: { tone: 'primary', icon: Send },
  APPROVED: { tone: 'warning', icon: CircleCheck },
  LIVE: { tone: 'success', icon: Link2 },
  REJECTED: { tone: 'danger', icon: CircleX },
  LOST: { tone: 'danger', icon: Link2Off },
};

export const BACKLINK_TYPE_LABELS: Record<BacklinkType, string> = {
  GUEST_POST: 'Guest post',
  DIRECTORY: 'Directory',
  BUSINESS_LISTING: 'Business listing',
  PROFILE: 'Profile',
  FORUM: 'Forum',
  SOCIAL_BOOKMARK: 'Social bookmark',
  PRESS_RELEASE: 'Press release',
  RESOURCE_PAGE: 'Resource page',
  BLOG_COMMENT: 'Blog comment',
  OTHER: 'Other',
};

export const STAGE_LABELS: Record<Stage, string> = {
  SUBMITTED: 'Submitted',
  APPROVED: 'Approved',
  LIVE: 'Live',
  REJECTED: 'Rejected',
  LOST: 'Lost',
};

/** The date field of each stage, in stage order. */
export const STAGE_FIELDS: Record<Stage, DateField> = {
  SUBMITTED: 'submittedDate',
  APPROVED: 'approvedDate',
  LIVE: 'liveDate',
  REJECTED: 'rejectedDate',
  LOST: 'lostDate',
};

const STAGE_ORDER: Stage[] = ['SUBMITTED', 'APPROVED', 'LIVE', 'REJECTED', 'LOST'];

/** Mirrors BacklinkRules.required: the stages a status has been through. */
const REQUIRED: Record<BacklinkStatus, Stage[]> = {
  PROSPECTED: [],
  SUBMITTED: ['SUBMITTED'],
  APPROVED: ['SUBMITTED', 'APPROVED'],
  LIVE: ['SUBMITTED', 'LIVE'],
  REJECTED: ['SUBMITTED', 'REJECTED'],
  LOST: ['SUBMITTED', 'LIVE', 'LOST'],
};

/** Mirrors BacklinkRules.allowed: approval is optional for live, rejected and lost links. */
const ALLOWED: Record<BacklinkStatus, Stage[]> = {
  PROSPECTED: [],
  SUBMITTED: ['SUBMITTED'],
  APPROVED: ['SUBMITTED', 'APPROVED'],
  LIVE: ['SUBMITTED', 'APPROVED', 'LIVE'],
  REJECTED: ['SUBMITTED', 'APPROVED', 'REJECTED'],
  LOST: ['SUBMITTED', 'APPROVED', 'LIVE', 'LOST'],
};

export function stageRequired(status: BacklinkStatus, stage: Stage): boolean {
  return REQUIRED[status].includes(stage);
}

export function stageAllowed(status: BacklinkStatus, stage: Stage): boolean {
  return ALLOWED[status].includes(stage);
}

/** ISO dates ('' or null for none) by field. */
export type StageDates = Record<DateField, string | null>;

/** Mirrors BacklinkRules.moveTo: stages the status needs and has not reached are dated `date`; disallowed ones are cleared. */
export function moveTo(status: BacklinkStatus, current: StageDates, date: string): StageDates {
  const next = { ...current };
  for (const stage of STAGE_ORDER) {
    const field = STAGE_FIELDS[stage];
    if (!stageAllowed(status, stage)) next[field] = null;
    else if (!current[field] && stageRequired(status, stage)) next[field] = date;
  }
  return next;
}

/**
 * Mirrors BacklinkRules.check for the form: the field and message of the first problem, or null. Dates are ISO
 * strings, so they compare as text.
 */
export function stageProblem(status: BacklinkStatus, dates: StageDates, linkUrl: string, today: string): { field: DateField | 'linkUrl'; message: string } | null {
  const article = status === 'APPROVED' ? 'An' : 'A';
  const label = BACKLINK_STATUS_LABELS[status].toLowerCase();
  for (const stage of STAGE_ORDER) {
    const field = STAGE_FIELDS[stage];
    const value = dates[field];
    const verb = STAGE_LABELS[stage].toLowerCase();
    if (!value && stageRequired(status, stage)) return { field, message: `${article} ${label} backlink needs its ${verb} date` };
    if (value && !stageAllowed(status, stage)) return { field, message: `${article} ${label} backlink has no ${verb} date` };
    if (value && value > today) return { field, message: `The ${verb} date cannot be in the future` };
  }
  const order: [DateField, DateField, string, string][] = [
    ['submittedDate', 'approvedDate', 'approved', 'submitted'],
    ['submittedDate', 'liveDate', 'live', 'submitted'],
    ['approvedDate', 'liveDate', 'live', 'approved'],
    ['submittedDate', 'rejectedDate', 'rejected', 'submitted'],
    ['approvedDate', 'rejectedDate', 'rejected', 'approved'],
    ['liveDate', 'lostDate', 'lost', 'live'],
  ];
  for (const [earlier, later, laterVerb, earlierVerb] of order) {
    const a = dates[earlier];
    const b = dates[later];
    if (a && b && b < a) return { field: later, message: `The ${laterVerb} date cannot be before the ${earlierVerb} date` };
  }
  if ((status === 'LIVE' || status === 'LOST') && !linkUrl.trim()) return { field: 'linkUrl', message: 'A live backlink needs the URL of the page that links to us' };
  return null;
}

/** Whether moving to `status` today would set, move or clear a date in a closed month (the server refuses that). */
export function touchesLockedDate(status: BacklinkStatus, current: StageDates, locked: DateField[], today: string): boolean {
  const next = moveTo(status, current, today);
  return locked.some((field) => (next[field] ?? null) !== (current[field] ?? null));
}
