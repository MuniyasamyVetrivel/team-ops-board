import { describe, expect, it } from 'vitest';

import { attachmentName } from './api';
import { bannerMessage, copyBadge, formatDuration } from './backup-rules';

describe('copyBadge', () => {
  it('names every state of a copy, never colour alone', () => {
    expect(copyBadge('SUCCESS', null)).toEqual({ label: 'Stored', tone: 'success', kind: 'stored' });
    expect(copyBadge('FAILED', null)).toEqual({ label: 'Failed', tone: 'danger', kind: 'failed' });
    expect(copyBadge('RUNNING', null)).toEqual({ label: 'Running', tone: 'primary', kind: 'running' });
    expect(copyBadge('SUCCESS', '2026-10-16T02:00:00Z')).toEqual({ label: 'Removed (retention)', tone: 'neutral', kind: 'removed' });
    expect(copyBadge(null, null)).toEqual({ label: 'Not configured', tone: 'neutral', kind: 'none' });
  });
});

describe('formatDuration', () => {
  it('shows seconds, minutes and hours', () => {
    expect(formatDuration('2026-10-16T02:00:00Z', '2026-10-16T02:00:45Z')).toBe('45 s');
    expect(formatDuration('2026-10-16T02:00:00Z', '2026-10-16T02:03:20Z')).toBe('3 min 20 s');
    expect(formatDuration('2026-10-16T02:00:00Z', '2026-10-16T02:05:00Z')).toBe('5 min');
    expect(formatDuration('2026-10-16T02:00:00Z', '2026-10-16T03:05:00Z')).toBe('1 h 05 min');
    expect(formatDuration('2026-10-16T02:00:00Z', null)).toBe('—');
  });
});

describe('bannerMessage', () => {
  it('shows the first problem and counts the rest', () => {
    expect(bannerMessage([{ message: 'The last backup failed' }])).toBe('The last backup failed');
    expect(bannerMessage([{ message: 'A' }, { message: 'B' }, { message: 'C' }])).toBe('A (+2 more)');
    expect(bannerMessage([])).toBe('');
  });
});

describe('attachmentName', () => {
  it('reads the download name from Content-Disposition', () => {
    expect(attachmentName('attachment; filename="team-ops-export-20261016-101500.zip"', 'x.zip')).toBe('team-ops-export-20261016-101500.zip');
    expect(attachmentName("attachment; filename*=UTF-8''team-ops%20export.zip", 'x.zip')).toBe('team-ops export.zip');
    expect(attachmentName(undefined, 'fallback.zip')).toBe('fallback.zip');
  });
});
