import { z } from 'zod';

import { BASE_CURRENCY, looksLikeCardNumber, parseAmount } from './finance-meta';

const text = (max: number) => z.string().trim().max(max, `At most ${max} characters`);

/** Mirrors FinanceDtos.MAX_ASSIGNED_USERS. */
export const MAX_ASSIGNED_USERS = 1000;

/** Mirrors LicenceRules.MAX_SEATS. */
export const MAX_SEATS = 100_000;

/** Mirrors @DecimalMin("0.00") @Digits(integer = 12, fraction = 2). */
const money = (message = 'Enter an amount with up to two decimals') =>
  z
    .string()
    .trim()
    .refine((v) => v === '' || /^\d{1,12}(\.\d{1,2})?$/.test(v.replace(/,/g, '')), message);

const currency = z
  .string()
  .trim()
  .refine((v) => /^[A-Za-z]{3}$/.test(v), 'Use a three-letter code like INR or AUD');

/** Mirrors FinanceDtos.SaveLicence, LicenceRules.checkDates and LicenceRules.checkPaymentMethod. */
export const licenceSchema = z
  .object({
    name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
    vendorId: z.string(),
    ownerId: z.string(),
    departmentId: z.string(),
    seatsPaid: z
      .string()
      .trim()
      .refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) <= MAX_SEATS), `Enter 0 to ${MAX_SEATS.toLocaleString()} seats`),
    billingCycle: z.enum(['MONTHLY', 'YEARLY', 'ONE_OFF']),
    cost: money(),
    currency,
    startDate: z.string(),
    renewalDate: z.string(),
    paymentMethod: text(60).refine((v) => !looksLikeCardNumber(v), 'Never enter a card number. Use a label such as "Corporate Amex" and the last 4 digits below.'),
    cardLast4: z
      .string()
      .trim()
      .refine((v) => v === '' || /^\d{4}$/.test(v), 'Exactly the last 4 digits'),
    status: z.enum(['ACTIVE', 'TRIAL', 'CANCELLED']),
    assignedUserIds: z.array(z.number()).max(MAX_ASSIGNED_USERS, `At most ${MAX_ASSIGNED_USERS} users`),
    notes: text(5000),
  })
  .refine((v) => !v.renewalDate || !v.startDate || v.renewalDate >= v.startDate, { path: ['renewalDate'], message: 'The renewal date must be on or after the start date' });

export type LicenceValues = z.infer<typeof licenceSchema>;

/** Mirrors FinanceDtos.SaveExpense and ExpenseService (GST is part of the amount, so it can't exceed it). */
export const expenseSchema = z
  .object({
    expenseDate: z.string().min(1, 'Date is required'),
    category: z.enum(['SOFTWARE', 'HARDWARE', 'RECRUITMENT', 'MARKETING', 'TRAVEL', 'TRAINING', 'OFFICE', 'OTHER']),
    vendorId: z.string(),
    description: z.string().trim().min(1, 'Description is required').max(500, 'At most 500 characters'),
    amount: money().refine((v) => v !== '', 'Amount is required'),
    gst: money(),
    currency,
    departmentId: z.string(),
    approvalId: z.string(),
    notes: text(5000),
  })
  .refine((v) => v.gst === '' || v.amount === '' || (parseAmount(v.gst) ?? 0) <= (parseAmount(v.amount) ?? 0), {
    path: ['gst'],
    message: 'GST is part of the amount, so it cannot be more than the amount',
  });

export type ExpenseValues = z.infer<typeof expenseSchema>;

/** Mirrors FinanceDtos.SaveExchangeRate and ExchangeRateService (AUD is the base currency, always 1, never stored). */
export const rateSchema = z.object({
  currency: currency.refine((v) => v.toUpperCase() !== BASE_CURRENCY, `${BASE_CURRENCY} is the base currency: it is always 1 and needs no rate`),
  effectiveFrom: z.string().min(1, 'Choose the date it applies from'),
  rateToAud: z
    .string()
    .trim()
    .refine((v) => /^\d{1,10}(\.\d{1,8})?$/.test(v.replace(/,/g, '')), 'Enter a rate with up to 8 decimals')
    .refine((v) => {
      const rate = parseAmount(v);
      return rate === null || Number.isNaN(rate) || (rate >= 0.00000001 && rate <= 1_000_000);
    }, 'Enter a rate above 0 and up to 1,000,000'),
  source: text(150),
});

export type RateValues = z.infer<typeof rateSchema>;

/** Mirrors FinanceDtos.SaveBudget: a department's budget for a month (YYYY-MM), in AUD. */
export const budgetSchema = z.object({
  departmentId: z.string().min(1, 'Choose the department'),
  month: z
    .string()
    .regex(/^\d{4}-\d{2}$/, 'Choose the month')
    .refine((v) => {
      const [year, month] = v.split('-').map(Number);
      return (year ?? 0) >= 2000 && (year ?? 0) <= 2100 && (month ?? 0) >= 1 && (month ?? 0) <= 12;
    }, 'Choose a month between 2000 and 2100'),
  amount: money().refine((v) => v !== '', 'Amount is required'),
  notes: text(5000),
});

export type BudgetValues = z.infer<typeof budgetSchema>;
