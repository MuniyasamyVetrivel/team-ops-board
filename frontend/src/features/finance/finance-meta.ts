import { CircleCheck, CircleSlash, FlaskConical, type LucideIcon } from 'lucide-react';

import type { BadgeProps } from '@/components/ui/badge';
import { formatMoney } from '@/features/vendors/vendor-meta';

import type { BillingCycle, ExpenseCategory, Licence, LicenceStatus } from './api';

type Tone = NonNullable<BadgeProps['tone']>;

export { formatMoney };

/** The base currency every expense, budget and dashboard figure is in (FinanceModule.BASE_CURRENCY). */
export const BASE_CURRENCY = 'AUD';

/** Mirrors LicenceRules.UNDER_USED_PERCENT. */
export const UNDER_USED_PERCENT = 70;

export const CATEGORY_LABELS: Record<ExpenseCategory, string> = {
  SOFTWARE: 'Software',
  HARDWARE: 'Hardware',
  RECRUITMENT: 'Recruitment',
  MARKETING: 'Marketing',
  TRAVEL: 'Travel',
  TRAINING: 'Training',
  OFFICE: 'Office',
  OTHER: 'Other',
};

export const BILLING_LABELS: Record<BillingCycle, string> = {
  MONTHLY: 'Monthly',
  YEARLY: 'Yearly',
  ONE_OFF: 'One-off',
};

/** Every status is shown with its label and an icon, never by colour alone. */
export const LICENCE_STATUS_META: Record<LicenceStatus, { label: string; tone: Tone; icon: LucideIcon }> = {
  ACTIVE: { label: 'Active', tone: 'success', icon: CircleCheck },
  TRIAL: { label: 'Trial', tone: 'primary', icon: FlaskConical },
  CANCELLED: { label: 'Cancelled', tone: 'neutral', icon: CircleSlash },
};

/** "AUD 1,250.00"; "—" without an amount. */
export function formatAud(value: number | null | undefined): string {
  return formatMoney(value ?? null, BASE_CURRENCY);
}

/** A dashboard axis value: "AUD 12k", "AUD 1.2M". */
export function formatAudShort(value: number): string {
  return `${BASE_CURRENCY} ${new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(value)}`;
}

/** "62.5%"; "—" when the server sent none (division by zero). */
export function formatPercent(value: number | null | undefined): string {
  return value == null ? '—' : `${Number(value.toFixed(1)).toLocaleString()}%`;
}

/** A rate to AUD as stored (up to 8 decimals, trailing zeros dropped). */
export function formatRate(value: number): string {
  return value.toLocaleString(undefined, { maximumFractionDigits: 8 });
}

/** "Oct 2026" for a year and month (1–12). */
export function monthLabel(year: number, month: number, style: 'short' | 'long' = 'short'): string {
  return new Date(Date.UTC(year, month - 1, 1)).toLocaleDateString(undefined, { month: style, year: 'numeric', timeZone: 'UTC' });
}

/** The month before or after (year, month), e.g. for the month switchers. */
export function shiftMonth(year: number, month: number, by: number): { year: number; month: number } {
  const index = year * 12 + (month - 1) + by;
  return { year: Math.floor(index / 12), month: (index % 12) + 1 };
}

/** The year and month of a business date ("2026-10-19"), never the browser clock. */
export function monthOf(date: string): { year: number; month: number } {
  const [year, month] = date.split('-').map(Number);
  return { year: year ?? 1970, month: month ?? 1 };
}

function days(count: number): string {
  return count === 1 ? '1 day' : `${count} days`;
}

/**
 * From the server's `daysUntilRenewal`, never the browser clock (null for cancelled licences and past renewals). A
 * trial's renewal date is the day it ends.
 */
export function licenceRenewalLabel(licence: Pick<Licence, 'daysUntilRenewal' | 'status'>): string | null {
  const left = licence.daysUntilRenewal;
  if (left === null) return null;
  const verb = licence.status === 'TRIAL' ? 'Trial ends' : 'Renews';
  if (left === 0) return `${verb} today`;
  if (left === 1) return `${verb} tomorrow`;
  return `${verb} in ${days(left)}`;
}

/** Within the 7-day reminder: danger; within 30 days: warning; otherwise neutral. */
export function renewalTone(daysUntilRenewal: number | null): Tone {
  if (daysUntilRenewal === null) return 'neutral';
  if (daysUntilRenewal <= 7) return 'danger';
  return daysUntilRenewal <= 30 ? 'warning' : 'neutral';
}

/** "4 of 10 seats" / "4 seats" without paid seats. */
export function seatsLabel(licence: Pick<Licence, 'seatsUsed' | 'seatsPaid'>): string {
  if (licence.seatsPaid === null) return licence.seatsUsed === 1 ? '1 seat used' : `${licence.seatsUsed} seats used`;
  return `${licence.seatsUsed} of ${licence.seatsPaid} seats`;
}

/** What the billing cycle means for the cost: "AUD 120.00 / month". */
export function costLabel(licence: Pick<Licence, 'cost' | 'currency' | 'billingCycle'>): string {
  if (licence.cost === null) return '—';
  const money = formatMoney(licence.cost, licence.currency);
  if (licence.billingCycle === 'MONTHLY') return `${money} / month`;
  if (licence.billingCycle === 'YEARLY') return `${money} / year`;
  return `${money} once`;
}

/**
 * Mirrors LicenceRules.checkPaymentMethod: a payment method is a label, and any run of more than 4 digits (spaces and
 * dashes inside the run count as part of it) could be a card number.
 */
export function looksLikeCardNumber(label: string): boolean {
  for (const run of label.match(/\d(?:[\s-]*\d)*/g) ?? []) {
    if (run.replace(/[\s-]/g, '').length > 4) return true;
  }
  return false;
}

/** "Corporate Amex ···· 4242"; null without either. */
export function paymentLabel(licence: Pick<Licence, 'paymentMethod' | 'cardLast4'>): string | null {
  if (!licence.paymentMethod && !licence.cardLast4) return null;
  return [licence.paymentMethod, licence.cardLast4 ? `···· ${licence.cardLast4}` : null].filter(Boolean).join(' ');
}

/** Parses a typed amount ("1,250.50") into a number; null when empty. */
export function parseAmount(raw: string): number | null {
  const trimmed = raw.trim().replace(/,/g, '');
  return trimmed === '' ? null : Number(trimmed);
}
