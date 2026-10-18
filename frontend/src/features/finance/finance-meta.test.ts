import { describe, expect, it } from 'vitest';

import { licence } from '@/test/finance-fixtures';

import { costLabel, formatPercent, licenceRenewalLabel, looksLikeCardNumber, monthOf, parseAmount, paymentLabel, renewalTone, seatsLabel, shiftMonth } from './finance-meta';

describe('finance-meta', () => {
  it('refuses payment labels that could be card numbers, as LicenceRules does', () => {
    expect(looksLikeCardNumber('Corporate Amex')).toBe(false);
    expect(looksLikeCardNumber('Visa ending 4242')).toBe(false);
    expect(looksLikeCardNumber('Card 4242 4242')).toBe(true);
    expect(looksLikeCardNumber('4111-1111-1111-1111')).toBe(true);
    expect(looksLikeCardNumber('PO 12345')).toBe(true);
    expect(looksLikeCardNumber('Amex 12 34')).toBe(false);
  });

  it('words renewals from the server’s days, with trials ending', () => {
    expect(licenceRenewalLabel({ daysUntilRenewal: 18, status: 'ACTIVE' })).toBe('Renews in 18 days');
    expect(licenceRenewalLabel({ daysUntilRenewal: 1, status: 'ACTIVE' })).toBe('Renews tomorrow');
    expect(licenceRenewalLabel({ daysUntilRenewal: 0, status: 'TRIAL' })).toBe('Trial ends today');
    expect(licenceRenewalLabel({ daysUntilRenewal: null, status: 'CANCELLED' })).toBeNull();
    expect(renewalTone(7)).toBe('danger');
    expect(renewalTone(30)).toBe('warning');
    expect(renewalTone(31)).toBe('neutral');
  });

  it('labels seats, costs and payment methods', () => {
    expect(seatsLabel(licence())).toBe('6 of 10 seats');
    expect(seatsLabel(licence({ seatsPaid: null, seatsUsed: 1 }))).toBe('1 seat used');
    expect(costLabel(licence({ cost: null }))).toBe('—');
    expect(costLabel(licence({ billingCycle: 'YEARLY' }))).toMatch(/120\.00 \/ year$/);
    expect(costLabel(licence({ billingCycle: 'ONE_OFF' }))).toMatch(/120\.00 once$/);
    expect(paymentLabel(licence())).toBe('Corporate Amex ···· 4242');
    expect(paymentLabel(licence({ paymentMethod: null, cardLast4: null }))).toBeNull();
  });

  it('moves between months across year ends and reads a business date', () => {
    expect(shiftMonth(2026, 1, -1)).toEqual({ year: 2025, month: 12 });
    expect(shiftMonth(2026, 12, 1)).toEqual({ year: 2027, month: 1 });
    expect(shiftMonth(2026, 10, -12)).toEqual({ year: 2025, month: 10 });
    expect(monthOf('2026-10-19')).toEqual({ year: 2026, month: 10 });
  });

  it('shows a dash when the server sent no percentage, and parses typed amounts', () => {
    expect(formatPercent(null)).toBe('—');
    expect(formatPercent(62.5)).toBe('62.5%');
    expect(parseAmount('1,250.50')).toBe(1250.5);
    expect(parseAmount(' ')).toBeNull();
  });
});
