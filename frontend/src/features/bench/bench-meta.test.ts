import { describe, expect, it } from 'vitest';

import { buildTimeline, dateRange, dayNumber, formatDays, formatFte, formatPercent, rollOffLabel, rollOffTone } from './bench-meta';

describe('bench formatting', () => {
  it('shows a percentage, and a dash where the server had nothing to divide by', () => {
    expect(formatPercent(70)).toBe('70%');
    expect(formatPercent(66.667)).toBe('66.7%');
    expect(formatPercent(null)).toBe('—');
  });

  it('shows FTE with two decimals', () => {
    expect(formatFte(2.5)).toBe('2.50');
    expect(formatFte(null)).toBe('—');
  });

  it('labels days and roll-offs from the server-computed day counts', () => {
    expect(formatDays(1)).toBe('1 day');
    expect(formatDays(59)).toBe('59 days');
    expect(rollOffLabel(0)).toBe('Ends today');
    expect(rollOffLabel(1)).toBe('Ends tomorrow');
    expect(rollOffLabel(19)).toBe('Ends in 19 days');
    expect(rollOffLabel(null)).toBe('No end date');
  });

  it('marks roll-offs inside the 14-day reminder more strongly than those inside 30 days', () => {
    expect(rollOffTone(14)).toBe('danger');
    expect(rollOffTone(15)).toBe('warning');
    expect(rollOffTone(null)).toBe('neutral');
  });

  it('describes open-ended periods', () => {
    expect(dateRange('2026-10-01', null)).toMatch(/^From .* \(no end date\)$/);
  });
});

describe('buildTimeline', () => {
  it('counts days in UTC, so daylight saving never shifts a date', () => {
    expect(dayNumber('2026-03-30') - dayNumber('2026-03-28')).toBe(2);
  });

  it('spans whole months from the earliest to the latest date, at least six months', () => {
    const timeline = buildTimeline([{ id: 1, startDate: '2026-09-15', endDate: '2026-10-14' }], '2026-10-01');

    expect(timeline.from).toBe('2026-09-01');
    expect(timeline.to).toBe('2027-03-01');
    expect(timeline.months).toHaveLength(6);
    expect(timeline.months[0]!.left).toBe(0);
  });

  it('places bars by date, includes the end day, and runs open-ended bars to the end', () => {
    // Sep 2026 – Feb 2027 is 181 days.
    const timeline = buildTimeline(
      [
        { id: 1, startDate: '2026-09-01', endDate: '2026-09-30' },
        { id: 2, startDate: '2026-10-01', endDate: null },
      ],
      '2026-10-01',
    );
    const [first, second] = timeline.bars;

    expect(first!.left).toBe(0);
    expect(first!.width).toBeCloseTo((30 / 181) * 100);
    expect(second!.openEnded).toBe(true);
    expect(second!.left + second!.width).toBeCloseTo(100);
    expect(timeline.today).toBeCloseTo((30 / 181) * 100);
  });

  it('extends past six months to the latest end date', () => {
    const timeline = buildTimeline([{ id: 1, startDate: '2026-01-10', endDate: '2027-02-03' }], '2026-10-17');

    expect(timeline.from).toBe('2026-01-01');
    expect(timeline.to).toBe('2027-03-01');
    expect(timeline.months).toHaveLength(14);
  });

  it('leaves out today when the server has not said what day it is', () => {
    expect(buildTimeline([{ id: 1, startDate: '2026-01-10', endDate: null }], null).today).toBeNull();
  });
});
