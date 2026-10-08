import { z } from 'zod';

import type { EmailCampaignStatus, EmailCampaignType, EmailRates } from './api';

export const CAMPAIGN_TYPE_LABELS: Record<EmailCampaignType, string> = {
  NEWSLETTER: 'Newsletter',
  LEAD_GENERATION: 'Lead generation',
  PRODUCT_PROMOTION: 'Product promotion',
  EVENT: 'Event',
  RECRUITMENT: 'Recruitment',
  OTHER: 'Other',
};

export const CAMPAIGN_STATUS_LABELS: Record<EmailCampaignStatus, string> = {
  DRAFT: 'Draft',
  SCHEDULED: 'Scheduled',
  SENT: 'Sent',
  CANCELLED: 'Cancelled',
};

/** Labels and formulas for the rates the server computes (brief section 38). */
export const RATE_LABELS: Record<keyof EmailRates, { label: string; formula: string }> = {
  deliveryRate: { label: 'Delivery rate', formula: 'Delivered ÷ sent' },
  openRate: { label: 'Open rate', formula: 'Unique opens ÷ delivered' },
  clickRate: { label: 'Click rate', formula: 'Unique clicks ÷ delivered' },
  clickToOpenRate: { label: 'Click-to-open rate', formula: 'Unique clicks ÷ unique opens' },
  leadConversionRate: { label: 'Lead conversion', formula: 'Leads ÷ delivered' },
  bounceRate: { label: 'Bounce rate', formula: 'Bounced ÷ sent' },
  unsubscribeRate: { label: 'Unsubscribe rate', formula: 'Unsubscribed ÷ delivered' },
};

/** Count fields of the form, in display order; the API names leads `leadsGenerated`. */
export const COUNT_FIELDS = [
  { name: 'emailsSent', label: 'Emails sent' },
  { name: 'delivered', label: 'Delivered' },
  { name: 'bounced', label: 'Bounced' },
  { name: 'opened', label: 'Opened (total)' },
  { name: 'uniqueOpens', label: 'Unique opens' },
  { name: 'clicked', label: 'Clicked (total)' },
  { name: 'uniqueClicks', label: 'Unique clicks' },
  { name: 'unsubscribed', label: 'Unsubscribed' },
  { name: 'leadsGenerated', label: 'Leads generated' },
] as const;

export type CountField = (typeof COUNT_FIELDS)[number]['name'];

const MAX_COUNT = 100_000_000;

/** A whole count typed into a form ("25,000" allowed); empty means 0. */
export const countField = z
  .string()
  .trim()
  .refine((v) => v === '' || /^\d+$/.test(v.replace(/,/g, '')), 'Enter a whole number')
  .refine((v) => v === '' || Number(v.replace(/,/g, '')) <= MAX_COUNT, 'Too large');

export function parseCount(value: string | undefined): number {
  const trimmed = (value ?? '').trim().replace(/,/g, '');
  return trimmed === '' ? 0 : Number(trimmed);
}

/**
 * The same consistency rules as EmailCounts.problems() on the server: [field, message] pairs for every problem.
 */
export function countProblems(c: Record<CountField, number>): [CountField, string][] {
  const problems: [CountField, string][] = [];
  if (c.delivered > c.emailsSent) problems.push(['delivered', 'Cannot be more than the emails sent']);
  if (c.bounced > c.emailsSent) problems.push(['bounced', 'Cannot be more than the emails sent']);
  if (c.uniqueOpens > c.opened) problems.push(['uniqueOpens', 'Cannot be more than the total opens']);
  else if (c.uniqueOpens > c.delivered) problems.push(['uniqueOpens', 'Cannot be more than the emails delivered']);
  if (c.uniqueClicks > c.clicked) problems.push(['uniqueClicks', 'Cannot be more than the total clicks']);
  else if (c.uniqueClicks > c.delivered) problems.push(['uniqueClicks', 'Cannot be more than the emails delivered']);
  if (c.unsubscribed > c.delivered) problems.push(['unsubscribed', 'Cannot be more than the emails delivered']);
  return problems;
}
