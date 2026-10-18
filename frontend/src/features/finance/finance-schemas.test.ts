import { describe, expect, it } from 'vitest';

import { budgetSchema, expenseSchema, licenceSchema, rateSchema } from './finance-schemas';

const validLicence = {
  name: 'Jira',
  vendorId: '',
  ownerId: '',
  departmentId: '',
  seatsPaid: '10',
  billingCycle: 'MONTHLY' as const,
  cost: '120.00',
  currency: 'usd',
  startDate: '2026-01-01',
  renewalDate: '2026-12-31',
  paymentMethod: 'Corporate Amex',
  cardLast4: '4242',
  status: 'ACTIVE' as const,
  assignedUserIds: [3, 4],
  notes: '',
};

const validExpense = {
  expenseDate: '2026-10-02',
  category: 'TRAVEL' as const,
  vendorId: '',
  description: 'Flights',
  amount: '100',
  gst: '9.09',
  currency: 'AUD',
  departmentId: '',
  approvalId: '',
  notes: '',
};

function messages(result: { success: boolean; error?: { issues: { path: PropertyKey[]; message: string }[] } }) {
  // The first message per field, as the form shows it.
  return Object.fromEntries((result.error?.issues ?? []).toReversed().map((i) => [String(i.path[0]), i.message]));
}

describe('finance-schemas', () => {
  it('accepts a valid licence and refuses card numbers, bad last 4 digits and a renewal before the start', () => {
    expect(licenceSchema.safeParse(validLicence).success).toBe(true);
    const bad = messages(licenceSchema.safeParse({ ...validLicence, paymentMethod: 'Visa 4111 1111 1111 1111', cardLast4: '42', renewalDate: '2025-12-31' }));
    expect(bad.paymentMethod).toMatch(/Never enter a card number/);
    expect(bad.cardLast4).toBe('Exactly the last 4 digits');
    expect(bad.renewalDate).toBe('The renewal date must be on or after the start date');
    expect(messages(licenceSchema.safeParse({ ...validLicence, seatsPaid: '100001', cost: '1.234' }))).toMatchObject({ seatsPaid: expect.any(String), cost: expect.any(String) });
  });

  it('requires an expense amount and refuses GST above it', () => {
    expect(expenseSchema.safeParse(validExpense).success).toBe(true);
    expect(messages(expenseSchema.safeParse({ ...validExpense, gst: '100.01' })).gst).toMatch(/cannot be more than the amount/);
    expect(messages(expenseSchema.safeParse({ ...validExpense, amount: '' })).amount).toBe('Amount is required');
    expect(messages(expenseSchema.safeParse({ ...validExpense, currency: 'AU' })).currency).toMatch(/three-letter/);
  });

  it('refuses a rate for AUD itself and rates out of range', () => {
    const rate = { currency: 'INR', effectiveFrom: '2026-10-01', rateToAud: '0.0182', source: '' };
    expect(rateSchema.safeParse(rate).success).toBe(true);
    expect(messages(rateSchema.safeParse({ ...rate, currency: 'aud' })).currency).toMatch(/base currency/);
    expect(messages(rateSchema.safeParse({ ...rate, rateToAud: '0' })).rateToAud).toMatch(/above 0/);
    expect(messages(rateSchema.safeParse({ ...rate, rateToAud: '0.123456789' })).rateToAud).toMatch(/8 decimals/);
  });

  it('needs a department, a month and an amount for a budget', () => {
    expect(budgetSchema.safeParse({ departmentId: '2', month: '2026-10', amount: '25000', notes: '' }).success).toBe(true);
    expect(messages(budgetSchema.safeParse({ departmentId: '', month: '', amount: '', notes: '' }))).toMatchObject({
      departmentId: 'Choose the department',
      month: 'Choose the month',
      amount: 'Amount is required',
    });
  });
});
