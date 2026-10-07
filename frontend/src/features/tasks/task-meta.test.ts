import { describe, expect, it } from 'vitest';

import { dueLabel, formatHours, historyValue, parseLocalDate } from './task-meta';

describe('parseLocalDate', () => {
  it('keeps the calendar date regardless of the browser time zone', () => {
    const date = parseLocalDate('2026-10-07');
    expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2026, 9, 7]);
  });
});

describe('dueLabel', () => {
  it('uses the server due state rather than the browser clock', () => {
    expect(dueLabel('2026-10-03', 'OVERDUE')).toMatch(/^Overdue · /);
    expect(dueLabel('2026-10-07', 'DUE_TODAY')).toBe('Due today');
    expect(dueLabel(null, 'NONE')).toBe('No due date');
  });
});

describe('formatting helpers', () => {
  it('formats hours and history values', () => {
    expect(formatHours(null)).toBe('—');
    expect(formatHours(7.5)).toBe('7.5 h');
    expect(historyValue('IN_PROGRESS')).toBe('In progress');
    expect(historyValue('URGENT')).toBe('Urgent');
    expect(historyValue(null)).toBe('—');
    expect(historyValue('2026-10-07')).toBe('2026-10-07');
  });
});
