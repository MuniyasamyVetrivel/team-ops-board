import { describe, expect, it } from 'vitest';

import { contract } from '@/test/vendor-fixtures';

import { contractValue, daysUntilNotice, noticeByFor, noticeLabel, renewalGroups, renewalLabel, renewalTone, safeWebsite } from './vendor-meta';

describe('contract values', () => {
  it('show "Hidden" when the server hides them, and a dash without a value', () => {
    expect(contractValue(contract({ value: null, valueHidden: true }))).toBe('Hidden');
    expect(contractValue(contract({ value: null }))).toBe('—');
    expect(contractValue(contract({ value: 18500, currency: 'AUD' }))).toMatch(/AUD\s?18,500/);
  });
});

describe('renewals', () => {
  it('say whether the contract renews or ends, from the server-computed days', () => {
    expect(renewalLabel(contract({ daysUntilRenewal: 20 }))).toBe('Renews in 20 days');
    expect(renewalLabel(contract({ daysUntilRenewal: 0 }))).toBe('Renews today');
    expect(renewalLabel(contract({ daysUntilRenewal: 1, renewalDate: null, autoRenew: false }))).toBe('Ends tomorrow');
    expect(renewalLabel(contract({ daysUntilRenewal: null }))).toBe('Nothing due');
  });

  it('are urgent inside the 7-day reminder and pressing inside 30 days', () => {
    expect(renewalTone(7)).toBe('danger');
    expect(renewalTone(30)).toBe('warning');
    expect(renewalTone(31)).toBe('neutral');
  });

  it('count the days left to give notice from the server values', () => {
    expect(daysUntilNotice(contract({ daysUntilRenewal: 20, noticePeriodDays: 30 }))).toBe(-10);
    expect(noticeLabel(contract({ daysUntilRenewal: 20, noticePeriodDays: 30 }))).toBe('Notice deadline passed');
    expect(noticeLabel(contract({ daysUntilRenewal: 45, noticePeriodDays: 30 }))).toBe('Give notice within 15 days');
    expect(noticeLabel(contract({ noticePeriodDays: null }))).toBeNull();
  });

  it('work out the notice date for the dates typed into the form', () => {
    expect(noticeByFor('2027-03-01', '', '30')).toBe('2027-01-30');
    expect(noticeByFor('', '2027-02-28', '0')).toBe('2027-02-28');
    expect(noticeByFor('', '', '30')).toBeNull();
    expect(noticeByFor('2027-03-01', '', '')).toBeNull();
  });

  it('group by the reminder lead times, soonest first', () => {
    const groups = renewalGroups([
      contract({ id: 1, daysUntilRenewal: 25 }),
      contract({ id: 2, daysUntilRenewal: 3 }),
      contract({ id: 3, daysUntilRenewal: 60 }),
      contract({ id: 4, daysUntilRenewal: 7 }),
    ]);
    expect(groups.map((g) => [g.title, g.contracts.map((c) => c.id)])).toEqual([
      ['Within 7 days', [2, 4]],
      ['In 8 to 30 days', [1]],
      ['Later', [3]],
    ]);
  });
});

describe('safeWebsite', () => {
  it('only links http and https addresses', () => {
    expect(safeWebsite('https://www.canva.com')).toBe('https://www.canva.com/');
    expect(safeWebsite('javascript:alert(1)')).toBeNull();
    expect(safeWebsite('canva.com')).toBeNull();
  });
});
