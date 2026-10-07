import { describe, expect, it } from 'vitest';

import { formatDate, formatRelative } from './format';

describe('formatRelative', () => {
  const now = new Date('2026-10-07T12:00:00Z');

  it('shows a dash when there is no value', () => {
    expect(formatRelative(null, now)).toBe('—');
    expect(formatDate(undefined)).toBe('—');
  });

  it('picks the largest sensible unit', () => {
    expect(formatRelative('2026-10-07T09:00:00Z', now)).toMatch(/3 hours ago/);
    expect(formatRelative('2026-10-06T12:00:00Z', now)).toMatch(/yesterday|1 day ago/);
    expect(formatRelative('2026-10-07T11:59:40Z', now)).toBe('just now');
  });
});
