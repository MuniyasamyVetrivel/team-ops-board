import { describe, expect, it } from 'vitest';

import { moveTo, stageProblem, touchesLockedDate, type StageDates } from './backlink-meta';

const TODAY = '2026-10-09';
const NONE: StageDates = { submittedDate: null, approvedDate: null, liveDate: null, rejectedDate: null, lostDate: null };
const dates = (d: Partial<StageDates>): StageDates => ({ ...NONE, ...d });
const LINK = 'https://dzone.com/articles/sap-testing';

describe('stageProblem', () => {
  it('accepts each status with the dates of its stages', () => {
    expect(stageProblem('PROSPECTED', NONE, '', TODAY)).toBeNull();
    expect(stageProblem('APPROVED', dates({ submittedDate: '2026-10-01', approvedDate: '2026-10-03' }), '', TODAY)).toBeNull();
    // Approval is optional on the way to live.
    expect(stageProblem('LIVE', dates({ submittedDate: '2026-10-01', liveDate: '2026-10-06' }), LINK, TODAY)).toBeNull();
    expect(stageProblem('LOST', dates({ submittedDate: '2026-10-01', liveDate: '2026-10-03', lostDate: '2026-10-06' }), LINK, TODAY)).toBeNull();
  });

  it('names the missing or disallowed date', () => {
    expect(stageProblem('APPROVED', dates({ submittedDate: '2026-10-01' }), '', TODAY)).toEqual({ field: 'approvedDate', message: 'An approved backlink needs its approved date' });
    expect(stageProblem('SUBMITTED', dates({ submittedDate: '2026-10-01', liveDate: '2026-10-03' }), '', TODAY)?.field).toBe('liveDate');
  });

  it('keeps stages in order, out of the future, and needs the URL once live', () => {
    expect(stageProblem('LIVE', dates({ submittedDate: '2026-10-01', approvedDate: '2026-10-06', liveDate: '2026-10-03' }), LINK, TODAY)?.message).toBe('The live date cannot be before the approved date');
    expect(stageProblem('SUBMITTED', dates({ submittedDate: '2026-10-10' }), '', TODAY)?.message).toMatch(/future/);
    expect(stageProblem('LIVE', dates({ submittedDate: '2026-10-01', liveDate: '2026-10-03' }), ' ', TODAY)?.field).toBe('linkUrl');
  });
});

describe('moveTo', () => {
  it('dates the new stages and clears the ones no longer allowed', () => {
    expect(moveTo('LIVE', dates({ submittedDate: '2026-10-01' }), TODAY)).toEqual(dates({ submittedDate: '2026-10-01', liveDate: TODAY }));
    expect(moveTo('LIVE', NONE, TODAY)).toEqual(dates({ submittedDate: TODAY, liveDate: TODAY }));
    expect(moveTo('APPROVED', dates({ submittedDate: '2026-10-01', approvedDate: '2026-10-03', liveDate: '2026-10-06' }), TODAY)).toEqual(
      dates({ submittedDate: '2026-10-01', approvedDate: '2026-10-03' }),
    );
  });
});

describe('touchesLockedDate', () => {
  const old = dates({ submittedDate: '2026-06-05', liveDate: '2026-06-12' });
  const locked = ['submittedDate', 'liveDate'] as const;

  it('lets a link from a closed month be lost today, not taken back to submitted', () => {
    expect(touchesLockedDate('LOST', old, [...locked], TODAY)).toBe(false);
    expect(touchesLockedDate('SUBMITTED', old, [...locked], TODAY)).toBe(true);
  });
});
