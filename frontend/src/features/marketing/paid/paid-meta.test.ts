import { describe, expect, it } from 'vitest';

import type { PaidCampaign, PaidMonth } from './api';
import { moneyField, parseNumber, recordableMonths } from './paid-meta';

const base = { startDate: '2026-08-15', endDate: null } as Pick<PaidCampaign, 'startDate' | 'endDate'>;
const recorded = (month: number, year: number) => ({ period: { month, year, label: '' } }) as PaidMonth;

describe('recordableMonths', () => {
  it('offers months from the start up to today that have no results, newest first', () => {
    expect(recordableMonths(base as PaidCampaign, [recorded(9, 2026)], '2026-10-09')).toEqual([
      { month: 10, year: 2026 },
      { month: 8, year: 2026 },
    ]);
  });

  it('stops at the end date and never offers a future month', () => {
    expect(recordableMonths({ ...base, endDate: '2026-09-10' } as PaidCampaign, [], '2026-10-09')).toEqual([
      { month: 9, year: 2026 },
      { month: 8, year: 2026 },
    ]);
    expect(recordableMonths({ ...base, startDate: '2026-11-01' } as PaidCampaign, [], '2026-10-09')).toEqual([]);
  });

  it('crosses year boundaries', () => {
    expect(recordableMonths({ startDate: '2025-12-20', endDate: null } as PaidCampaign, [], '2026-01-05')).toEqual([
      { month: 1, year: 2026 },
      { month: 12, year: 2025 },
    ]);
  });
});

describe('money input', () => {
  it('accepts Indian grouping and two decimals', () => {
    const field = moneyField('Required');
    expect(field.safeParse('42,000.50').success).toBe(true);
    expect(field.safeParse('1,50,000').success).toBe(true);
    expect(field.safeParse('12.345').success).toBe(false);
    expect(field.safeParse('').success).toBe(false);
    expect(parseNumber('42,000.50')).toBe(42000.5);
    expect(parseNumber('')).toBe(0);
  });
});
