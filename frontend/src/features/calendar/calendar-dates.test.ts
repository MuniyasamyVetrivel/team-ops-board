import { describe, expect, it } from 'vitest';

import { addDays, covers, daysBetween, shift, startOfWeek, viewRange } from './calendar-dates';

describe('calendar dates', () => {
  it('weeks start on Monday', () => {
    expect(startOfWeek('2026-10-08')).toBe('2026-10-05'); // Thursday
    expect(startOfWeek('2026-10-05')).toBe('2026-10-05'); // Monday
    expect(startOfWeek('2026-10-11')).toBe('2026-10-05'); // Sunday
  });

  it('month views cover whole weeks around the month', () => {
    // October 2026 starts on a Thursday and ends on a Saturday.
    expect(viewRange('MONTH', '2026-10-17')).toEqual({ from: '2026-09-28', to: '2026-11-01' });
    expect(daysBetween('2026-09-28', '2026-11-01')).toHaveLength(35);
  });

  it('week and agenda ranges', () => {
    expect(viewRange('WEEK', '2026-10-08')).toEqual({ from: '2026-10-05', to: '2026-10-11' });
    expect(viewRange('AGENDA', '2026-10-08')).toEqual({ from: '2026-10-08', to: '2026-11-06' });
  });

  it('moves by month, week or agenda length and handles month ends', () => {
    expect(shift('MONTH', '2026-01-31', 1)).toBe('2026-02-01');
    expect(shift('MONTH', '2026-01-15', -1)).toBe('2025-12-01');
    expect(shift('WEEK', '2026-10-08', 1)).toBe('2026-10-15');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
  });

  it('multi-day items cover each day in their inclusive range', () => {
    expect(covers('2026-10-11', '2026-10-12', '2026-10-12')).toBe(true);
    expect(covers('2026-10-11', '2026-10-12', '2026-10-13')).toBe(false);
    expect(covers('2026-10-11', '2026-10-11', '2026-10-10')).toBe(false);
  });
});
