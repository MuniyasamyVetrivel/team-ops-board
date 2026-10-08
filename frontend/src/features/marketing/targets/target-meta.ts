import { z } from 'zod';

import type { MetricFormat } from '../marketing-format';
import type { ActualSource, LeadSource, TargetUnit } from './api';

export const UNIT_LABELS: Record<TargetUnit, string> = {
  COUNT: 'Count',
  CURRENCY: 'Amount (₹)',
  PERCENT: 'Percentage',
};

export const UNIT_FORMATS: Record<TargetUnit, MetricFormat> = {
  COUNT: 'count',
  CURRENCY: 'currency',
  PERCENT: 'percent',
};

export const ACTUAL_SOURCE_LABELS: Record<ActualSource, string> = {
  MANUAL: 'Entered by hand',
  LEADS: 'All leads',
  LEADS_BY_SOURCE: 'Leads from one source',
  BACKLINKS_LIVE: 'Backlinks gone live',
  BLOGS_PUBLISHED: 'Blogs published',
  KEYWORDS_TOP10: 'Keywords in the top 10',
  EMAIL_CAMPAIGNS: 'Email campaigns sent',
  PAID_CAMPAIGNS: 'Paid campaigns running',
  LANDING_PAGES: 'Landing pages created',
};

export const LEAD_SOURCE_LABELS: Record<LeadSource, string> = {
  ORGANIC: 'Organic',
  EMAIL: 'Email',
  LINKEDIN: 'LinkedIn',
  PAID_CAMPAIGN: 'Paid campaign',
  BLOG: 'Blog',
  WEBSITE: 'Website',
  REFERRAL: 'Referral',
  OTHER: 'Other',
};

const DECIMAL = /^\d+(\.\d{1,2})?$/;
const MAX = 999_999_999_999.99;

/**
 * A target or actual typed into a form, checked like the backend (TargetRules.requireValid + the DTO limits): counts
 * are whole numbers, percentages at most 100, at most two decimals. `required` targets must be more than zero.
 */
export function valueField(unit: TargetUnit, { required }: { required: boolean }) {
  return z
    .string()
    .trim()
    .superRefine((value, ctx) => {
      if (value === '') {
        if (required) ctx.addIssue({ code: 'custom', message: 'Enter a value' });
        return;
      }
      const number = Number(value.replace(/,/g, ''));
      if (!DECIMAL.test(value.replace(/,/g, '')) || !Number.isFinite(number)) {
        ctx.addIssue({ code: 'custom', message: 'Enter a number with at most two decimals' });
      } else if (required && number <= 0) {
        ctx.addIssue({ code: 'custom', message: 'Must be more than 0' });
      } else if (unit === 'COUNT' && !Number.isInteger(number)) {
        ctx.addIssue({ code: 'custom', message: 'Enter a whole number' });
      } else if (unit === 'PERCENT' && number > 100) {
        ctx.addIssue({ code: 'custom', message: 'A percentage cannot exceed 100' });
      } else if (number > MAX) {
        ctx.addIssue({ code: 'custom', message: 'Too large' });
      }
    });
}

/** "1,25,000" or "" → number or null. */
export function parseValue(value: string): number | null {
  const trimmed = value.trim().replace(/,/g, '');
  return trimmed === '' ? null : Number(trimmed);
}
