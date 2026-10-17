import { Ban, CalendarClock, CircleCheck, CirclePause, CirclePlay, CircleStop, type LucideIcon } from 'lucide-react';

import type { BadgeProps } from '@/components/ui/badge';

import type { Contract, ContractStatus, VendorCategory, VendorStatus } from './api';

type Tone = NonNullable<BadgeProps['tone']>;

interface StatusMeta {
  label: string;
  tone: Tone;
  icon: LucideIcon;
}

export const CATEGORY_LABELS: Record<VendorCategory, string> = {
  SOFTWARE: 'Software',
  HARDWARE: 'Hardware',
  RECRUITMENT_AGENCY: 'Recruitment agency',
  MARKETING: 'Marketing',
  SERVICES: 'Services',
  OTHER: 'Other',
};

/** Every status is shown with its label and an icon, never by colour alone. */
export const VENDOR_STATUS_META: Record<VendorStatus, StatusMeta> = {
  ACTIVE: { label: 'Active', tone: 'success', icon: CircleCheck },
  INACTIVE: { label: 'Inactive', tone: 'neutral', icon: CirclePause },
  BLACKLISTED: { label: 'Blacklisted', tone: 'danger', icon: Ban },
};

export const CONTRACT_STATUS_META: Record<ContractStatus, StatusMeta> = {
  UPCOMING: { label: 'Upcoming', tone: 'primary', icon: CalendarClock },
  ACTIVE: { label: 'Active', tone: 'success', icon: CirclePlay },
  EXPIRED: { label: 'Expired', tone: 'neutral', icon: CircleStop },
};

/** What a hidden contract value reads as (no FINANCE_VIEW), on screen as in the server's export. */
export const HIDDEN_VALUE = 'Hidden';

/** "AUD 18,500.00"; "—" without a value. Hidden values are handled by the caller. */
export function formatMoney(value: number | null, currency: string): string {
  if (value === null) return '—';
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency, currencyDisplay: 'code', maximumFractionDigits: 2 }).format(value);
  } catch {
    // An unusual code the browser does not know.
    return `${currency} ${value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }
}

export function contractValue(contract: Pick<Contract, 'value' | 'valueHidden' | 'currency'>): string {
  return contract.valueHidden ? HIDDEN_VALUE : formatMoney(contract.value, contract.currency);
}

/** "4 / 5"; "Not rated". */
export function ratingLabel(rating: number | null): string {
  return rating === null ? 'Not rated' : `${rating} / 5`;
}

function days(count: number): string {
  return count === 1 ? '1 day' : `${count} days`;
}

/**
 * From the server's `daysUntilRenewal`, never the browser clock. A contract without a renewal date ends rather than
 * renews (unless it renews automatically).
 */
export function renewalLabel(contract: Pick<Contract, 'daysUntilRenewal' | 'renewalDate' | 'autoRenew'>): string {
  const left = contract.daysUntilRenewal;
  if (left === null) return 'Nothing due';
  const ends = contract.renewalDate === null && !contract.autoRenew;
  if (left === 0) return ends ? 'Ends today' : 'Renews today';
  if (left === 1) return ends ? 'Ends tomorrow' : 'Renews tomorrow';
  return `${ends ? 'Ends' : 'Renews'} in ${days(left)}`;
}

/** Within the 7-day reminder: danger; within 30 days: warning; otherwise neutral. */
export function renewalTone(daysUntilRenewal: number | null): Tone {
  if (daysUntilRenewal === null) return 'neutral';
  if (daysUntilRenewal <= 7) return 'danger';
  return daysUntilRenewal <= 30 ? 'warning' : 'neutral';
}

/**
 * Days left to give notice: the server's days until renewal less the notice period (both from the server); null
 * without a notice period or nothing due. Negative once the notice deadline has passed.
 */
export function daysUntilNotice(contract: Pick<Contract, 'daysUntilRenewal' | 'noticePeriodDays'>): number | null {
  if (contract.daysUntilRenewal === null || contract.noticePeriodDays === null) return null;
  return contract.daysUntilRenewal - contract.noticePeriodDays;
}

export function noticeLabel(contract: Pick<Contract, 'daysUntilRenewal' | 'noticePeriodDays'>): string | null {
  const left = daysUntilNotice(contract);
  if (left === null) return null;
  if (left < 0) return 'Notice deadline passed';
  if (left === 0) return 'Give notice today';
  return `Give notice within ${days(left)}`;
}

/**
 * The last day to give notice for dates typed into the form: the renewal date (or else the end date) less the notice
 * period. Pure date arithmetic on the entered dates; null while incomplete.
 */
export function noticeByFor(renewalDate: string, endDate: string, noticeDays: string): string | null {
  const renewsOn = renewalDate || endDate;
  if (!renewsOn || noticeDays === '' || !/^\d+$/.test(noticeDays)) return null;
  const [year, month, day] = renewsOn.split('-').map(Number);
  const date = new Date(Date.UTC(year ?? 1970, (month ?? 1) - 1, day ?? 1));
  date.setUTCDate(date.getUTCDate() - Number(noticeDays));
  return date.toISOString().slice(0, 10);
}

/** An absolute http(s) address, safe to link to. */
export function safeWebsite(website: string | null): string | null {
  if (!website) return null;
  try {
    const url = new URL(website);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null;
  } catch {
    return null;
  }
}

/** How soon a renewal is, by the reminder lead times (7 and 30 days). */
export function renewalGroups(contracts: Contract[]): { key: string; title: string; contracts: Contract[] }[] {
  const sorted = [...contracts].sort((a, b) => (a.daysUntilRenewal ?? Infinity) - (b.daysUntilRenewal ?? Infinity) || a.id - b.id);
  const groups = [
    { key: 'week', title: 'Within 7 days', contracts: sorted.filter((c) => (c.daysUntilRenewal ?? Infinity) <= 7) },
    { key: 'month', title: 'In 8 to 30 days', contracts: sorted.filter((c) => (c.daysUntilRenewal ?? Infinity) > 7 && (c.daysUntilRenewal ?? Infinity) <= 30) },
    { key: 'later', title: 'Later', contracts: sorted.filter((c) => (c.daysUntilRenewal ?? Infinity) > 30) },
  ];
  return groups.filter((g) => g.contracts.length > 0);
}
