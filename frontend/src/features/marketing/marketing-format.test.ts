import { describe, expect, it } from 'vitest';

import { formatCount, formatDecimal, formatInr, formatMetric, formatPercent, percentChange, periodLabel, previousPeriod } from './marketing-format';

describe('marketing formats', () => {
  it('formats the reference values', () => {
    expect(formatPercent(35.42)).toBe('35.42%');
    expect(formatPercent(80)).toBe('80%');
    expect(formatInr(500)).toBe('₹500');
    expect(formatInr(42000)).toBe('₹42,000');
    expect(formatCount(125000)).toBe('1,25,000');
    expect(formatMetric(110, 'percent')).toBe('110%');
  });

  it('shows a dash for missing values and divisions by zero', () => {
    expect(formatPercent(null)).toBe('—');
    expect(formatInr(undefined)).toBe('—');
    expect(formatCount(null)).toBe('—');
    expect(percentChange(10, 0)).toBeNull();
    expect(percentChange(null, 5)).toBeNull();
  });

  it('computes the change from last month', () => {
    expect(percentChange(225, 200)).toBe(12.5);
    expect(percentChange(18, 22)).toBe(-18.2);
    expect(percentChange(5, 5)).toBe(0);
  });

  it('formats average positions with one decimal', () => {
    expect(formatDecimal(12.5)).toBe('12.5');
    expect(formatDecimal(7)).toBe('7');
    expect(formatDecimal(16.25)).toBe('16.3');
    expect(formatMetric(null, 'decimal')).toBe('—');
  });

  it('names periods and steps back across years', () => {
    expect(periodLabel(10, 2026)).toBe('October 2026');
    expect(previousPeriod(1, 2026)).toEqual({ month: 12, year: 2025 });
    expect(previousPeriod(10, 2026)).toEqual({ month: 9, year: 2026 });
  });
});
