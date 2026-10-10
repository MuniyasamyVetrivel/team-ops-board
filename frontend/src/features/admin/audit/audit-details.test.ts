import { describe, expect, it } from 'vitest';

import { describeDetails, displayValue, fieldLabel, summarizeDetails } from './audit-details';

describe('describeDetails', () => {
  it('turns field edits into before/after rows', () => {
    const details = describeDetails({
      changes: { status: { from: 'TODO', to: 'IN_PROGRESS' }, dueDate: { from: null, to: '2026-10-20' } },
      closedMonth: true,
    });
    expect(details.changes).toEqual([
      { field: 'Status', before: 'TODO', after: 'IN_PROGRESS' },
      { field: 'Due date', before: '—', after: '2026-10-20' },
    ]);
    expect(details.facts).toEqual([['Closed month', 'Yes']]);
  });

  it('keeps setting keys as they are', () => {
    expect(describeDetails({ changes: { 'workload.windowDays': { from: '14', to: '21' } } }).changes).toEqual([
      { field: 'workload.windowDays', before: '14', after: '21' },
    ]);
  });

  it('compares access before and after, listing only what changed', () => {
    const details = describeDetails({
      before: { roles: ['EMPLOYEE'], permissions: [] },
      after: { roles: ['EMPLOYEE'], permissions: ['REPORT_VIEW', 'REPORT_EXPORT'] },
    });
    expect(details.changes).toEqual([{ field: 'Permissions', before: '—', after: 'REPORT_VIEW, REPORT_EXPORT' }]);
  });

  it('lists added and removed role permissions', () => {
    const details = describeDetails({ role: 'EMPLOYEE', added: ['WORKLOAD_VIEW'], removed: [] });
    expect(details.added).toEqual(['WORKLOAD_VIEW']);
    expect(details.removed).toEqual([]);
    expect(details.facts).toEqual([['Role', 'EMPLOYEE']]);
  });

  it('shows other documents as facts and copes with nothing', () => {
    expect(describeDetails({ code: 'TRAVEL', steps: ['Department manager', 'Any Super Admin'] }).facts).toEqual([
      ['Code', 'TRAVEL'],
      ['Steps', 'Department manager, Any Super Admin'],
    ]);
    expect(describeDetails(null)).toEqual({ changes: [], added: [], removed: [], facts: [] });
  });
});

describe('summaries and values', () => {
  it('summarises an entry in one line', () => {
    expect(summarizeDetails({ changes: { status: { from: 'OPEN', to: 'RESOLVED' }, priority: { from: 'LOW', to: 'HIGH' } } })).toBe(
      'Status: OPEN → RESOLVED · +1 more',
    );
    expect(summarizeDetails({ role: 'EMPLOYEE', added: ['WORKLOAD_VIEW'], removed: ['KB_EDIT'] })).toBe('Added WORKLOAD_VIEW · Removed KB_EDIT');
    expect(summarizeDetails({ code: 'TRAVEL', name: 'Travel' })).toBe('Code: TRAVEL · Name: Travel');
    expect(summarizeDetails(null)).toBe('');
  });

  it('formats values and labels', () => {
    expect(displayValue(false)).toBe('No');
    expect(displayValue({ month: 9, year: 2026 })).toBe('Month: 9; Year: 2026');
    expect(fieldLabel('requiresAmount')).toBe('Requires amount');
    expect(fieldLabel('external_id')).toBe('External id');
  });
});
