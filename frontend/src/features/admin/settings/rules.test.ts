import { describe, expect, it } from 'vitest';

import { suggestCode } from '@/features/approvals/approval-meta';
import { hitHref, statusLabel } from '@/features/search/api';

import { permissionDiff, toggleRolePermission } from './role-matrix';
import { groupSettings, settingValueError } from './setting-rules';

const windowDays = { label: 'Workload window', valueType: 'INTEGER' as const, min: 1, max: 90, unit: 'days' };
const hours = { label: 'Default task hours', valueType: 'DECIMAL' as const, min: 0, max: 40, unit: 'hours' };

describe('settingValueError (mirrors SettingDefinition)', () => {
  it('accepts values in range', () => {
    expect(settingValueError(windowDays, '21')).toBeNull();
    expect(settingValueError(windowDays, '14.0')).toBeNull();
    expect(settingValueError(hours, '4.5')).toBeNull();
    expect(settingValueError(hours, '0')).toBeNull();
  });

  it('rejects the rest with the server’s wording', () => {
    expect(settingValueError(windowDays, '')).toBe('Workload window is required');
    expect(settingValueError(windowDays, 'two')).toBe('Workload window must be a number');
    expect(settingValueError(windowDays, '7.5')).toBe('Workload window must be a whole number');
    expect(settingValueError(windowDays, '91')).toBe('Workload window must be between 1 and 90 days');
    expect(settingValueError(hours, '4.125')).toBe('Default task hours can have at most 2 decimal places');
  });

  it('groups settings in server order', () => {
    const base = { description: null, value: '1', unit: '', min: 0, max: 1, updatedBy: null, updatedAt: '', version: 0, valueType: 'INTEGER' as const };
    const groups = groupSettings([
      { ...base, key: 'a', group: 'Workload', label: 'A' },
      { ...base, key: 'b', group: 'People', label: 'B' },
      { ...base, key: 'c', group: 'Workload', label: 'C' },
    ]);
    expect(groups.map(([group, items]) => [group, items.map((i) => i.key)])).toEqual([
      ['Workload', ['a', 'c']],
      ['People', ['b']],
    ]);
  });
});

describe('role matrix (mirrors RolePermissionRules)', () => {
  it('brings the view permission along with an action', () => {
    expect([...toggleRolePermission(new Set(), 'REPORT_EXPORT', true)].sort()).toEqual(['REPORT_EXPORT', 'REPORT_VIEW']);
  });

  it('removes actions that need a view permission taken away', () => {
    const next = toggleRolePermission(new Set(['TASK_VIEW', 'TASK_EDIT', 'TASK_CREATE', 'KB_VIEW']), 'TASK_VIEW', false);
    expect([...next]).toEqual(['KB_VIEW']);
  });

  it('reports what saving adds and removes', () => {
    expect(permissionDiff(['TASK_VIEW', 'KB_EDIT', 'KB_VIEW'], new Set(['TASK_VIEW', 'KB_VIEW', 'WORKLOAD_VIEW']))).toEqual({
      added: ['WORKLOAD_VIEW'],
      removed: ['KB_EDIT'],
    });
  });
});

describe('approval type codes', () => {
  it('suggests a code from the name', () => {
    expect(suggestCode('Travel request')).toBe('TRAVEL_REQUEST');
    expect(suggestCode('  2026 conference & travel! ')).toBe('CONFERENCE_TRAVEL');
  });
});

describe('global search links', () => {
  it('opens each result where it lives', () => {
    const hit = { id: 7, code: null, title: 'Spring promo', subtitle: null, status: null, ref: null };
    expect(hitHref({ ...hit, type: 'TASK' })).toBe('/tasks?task=7');
    expect(hitHref({ ...hit, type: 'TICKET' })).toBe('/tickets?ticket=7');
    expect(hitHref({ ...hit, type: 'EMPLOYEE' })).toBe('/team/7');
    expect(hitHref({ ...hit, type: 'ARTICLE', ref: 'vpn-setup' })).toBe('/knowledge-base/vpn-setup');
    expect(hitHref({ ...hit, type: 'KEYWORD' })).toBe('/digital-marketing/seo?tab=keywords&keyword=7');
    expect(hitHref({ ...hit, type: 'LEAD' })).toBe('/digital-marketing/leads?lead=7');
    expect(hitHref({ ...hit, type: 'EMAIL_CAMPAIGN' })).toBe('/digital-marketing/email-campaigns?search=Spring%20promo');
    expect(statusLabel('WAITING_FOR_REQUESTER')).toBe('Waiting for requester');
  });
});
