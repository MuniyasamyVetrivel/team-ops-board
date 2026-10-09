import type { LeadSource } from '../targets/api';
import type { LeadOrigin, LeadStatus, LinkKind, LinkOption } from './api';

export const LEAD_STATUS_LABELS: Record<LeadStatus, string> = {
  NEW: 'New',
  CONTACTED: 'Contacted',
  QUALIFIED: 'Qualified',
  CONVERTED: 'Converted',
  LOST: 'Lost',
};

export const LEAD_ORIGIN_LABELS: Record<LeadOrigin, string> = {
  MANUAL: 'Entered by hand',
  CSV: 'CSV import',
  WEBSITE: 'Website form',
  CRM: 'CRM',
};

export const LINK_KIND_LABELS: Record<LinkKind, string> = {
  EMAIL_CAMPAIGN: 'Email campaign',
  PAID_CAMPAIGN: 'Paid campaign',
  CONTENT: 'Content',
};

/** What the link picker asks for, per kind. */
export const LINK_KIND_HINTS: Record<LinkKind, string> = {
  EMAIL_CAMPAIGN: 'Sent email campaigns',
  PAID_CAMPAIGN: 'Started paid campaigns',
  CONTENT: 'Published content',
};

/** Mirrors LeadRules.linkFor: email → email campaign, LinkedIn/paid → paid campaign, blog → content; others none. */
export function linkKindFor(source: LeadSource): LinkKind | null {
  switch (source) {
    case 'EMAIL':
      return 'EMAIL_CAMPAIGN';
    case 'LINKEDIN':
    case 'PAID_CAMPAIGN':
      return 'PAID_CAMPAIGN';
    case 'BLOG':
      return 'CONTENT';
    default:
      return null;
  }
}

/** A LinkedIn lead can only name a LinkedIn campaign (the server rejects others with INVALID_LINK). */
export function linkFits(source: LeadSource, option: Pick<LinkOption, 'kind' | 'detail'>): boolean {
  if (linkKindFor(source) !== option.kind) return false;
  return source !== 'LINKEDIN' || option.detail === 'LINKEDIN';
}

/** The input field each link kind fills in SaveLead. */
export function linkFields(kind: LinkKind | null, id: number | null) {
  return {
    emailCampaignId: kind === 'EMAIL_CAMPAIGN' ? id : null,
    paidCampaignId: kind === 'PAID_CAMPAIGN' ? id : null,
    contentItemId: kind === 'CONTENT' ? id : null,
  };
}

/**
 * Chart series: the five sources with their own lead targets, and the rest together, so each gets one of the six
 * categorical chart tokens.
 */
export const TREND_SERIES: { key: string; label: string; sources: LeadSource[]; color: string }[] = [
  { key: 'organic', label: 'Organic', sources: ['ORGANIC'], color: 'var(--chart-1)' },
  { key: 'email', label: 'Email', sources: ['EMAIL'], color: 'var(--chart-2)' },
  { key: 'linkedin', label: 'LinkedIn', sources: ['LINKEDIN'], color: 'var(--chart-3)' },
  { key: 'paid', label: 'Paid campaign', sources: ['PAID_CAMPAIGN'], color: 'var(--chart-4)' },
  { key: 'blog', label: 'Blog', sources: ['BLOG'], color: 'var(--chart-5)' },
  { key: 'other', label: 'Website, referral & other', sources: ['WEBSITE', 'REFERRAL', 'OTHER'], color: 'var(--chart-6)' },
];
