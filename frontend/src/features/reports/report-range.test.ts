import { describe, expect, it } from 'vitest';

import { addDays, previousRange, rangeFor, rangeLabel } from './report-range';

const TODAY = '2026-10-09';

describe('rangeFor', () => {
  it('builds the presets from the business date', () => {
    expect(rangeFor('LAST_30', TODAY)).toEqual({ from: '2026-09-10', to: TODAY });
    expect(rangeFor('LAST_90', TODAY)).toEqual({ from: '2026-07-12', to: TODAY });
    expect(rangeFor('THIS_MONTH', TODAY)).toEqual({ from: '2026-10-01', to: TODAY });
    expect(rangeFor('LAST_MONTH', TODAY)).toEqual({ from: '2026-09-01', to: '2026-09-30' });
    expect(rangeFor('LAST_MONTH', '2026-03-15')).toEqual({ from: '2026-02-01', to: '2026-02-28' });
  });
});

describe('previousRange', () => {
  it('compares a month with the month before (September → October)', () => {
    expect(previousRange({ from: '2026-10-01', to: '2026-10-31' })).toEqual({ from: '2026-09-01', to: '2026-09-30' });
    expect(previousRange({ from: '2026-09-01', to: '2026-09-30' })).toEqual({ from: '2026-08-01', to: '2026-08-31' });
    // Month to date: the same days of the month before.
    expect(previousRange({ from: '2026-10-01', to: TODAY })).toEqual({ from: '2026-09-01', to: '2026-09-09' });
    expect(previousRange({ from: '2026-03-01', to: '2026-03-31' })).toEqual({ from: '2026-02-01', to: '2026-02-28' });
    expect(previousRange({ from: '2026-01-01', to: '2026-01-31' })).toEqual({ from: '2025-12-01', to: '2025-12-31' });
  });

  it('compares any other range with the same number of days just before it', () => {
    expect(previousRange({ from: '2026-09-10', to: TODAY })).toEqual({ from: '2026-08-11', to: '2026-09-09' });
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  });
});

describe('rangeLabel', () => {
  it('names whole months and shows other ranges by day', () => {
    expect(rangeLabel({ from: '2026-09-01', to: '2026-09-30' })).toBe('September 2026');
    expect(rangeLabel({ from: '2026-09-10', to: TODAY })).toBe('10 Sep – 9 Oct 2026');
  });
});
