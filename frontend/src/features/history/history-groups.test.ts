import { describe, expect, it } from 'vitest';

import type { AuditLogItem } from '@/features/admin/audit/api';

import { groupHistory } from './history-groups';

const priya = { id: 7, fullName: 'Priya Menon', email: 'priya@teamops.local', jobTitle: null, status: 'ACTIVE' as const };
const karthik = { ...priya, id: 4, fullName: 'Karthik Raj' };

function entry(overrides: Partial<AuditLogItem>): AuditLogItem {
  return {
    id: 1,
    action: 'RECORD_UPDATED',
    actionLabel: 'Task edited',
    module: 'TASKS',
    moduleLabel: 'Tasks',
    actor: priya,
    entityType: 'TASK',
    entityTypeLabel: 'Task',
    entityId: 128,
    entityLabel: 'TSK-000128',
    parentType: null,
    parentId: null,
    details: null,
    ipAddress: null,
    userAgent: null,
    createdAt: '2026-10-16T05:00:00.000Z',
    ...overrides,
  };
}

const task = { type: 'TASK', id: 128 };

describe('groupHistory', () => {
  it('shows a named event and the automatic change for the same action as one item', () => {
    const items = groupHistory(
      [
        entry({ id: 2, details: { changes: { status: { from: 'TODO', to: 'IN_PROGRESS' } } }, createdAt: '2026-10-16T05:00:00.400Z' }),
        entry({ id: 1, action: 'TASK_STATUS_CHANGED', actionLabel: 'Task status changed', details: { from: 'TODO', to: 'IN_PROGRESS' } }),
      ],
      task,
    );
    expect(items).toHaveLength(1);
    expect(items[0]).toMatchObject({ title: 'Task status changed', kind: 'updated', actor: priya, subject: null, at: '2026-10-16T05:00:00.400Z' });
    expect(items[0]!.details.changes).toEqual([{ field: 'Status', before: 'TODO', after: 'IN_PROGRESS' }]);
    // The named event's own facts would repeat the diff.
    expect(items[0]!.details.facts).toEqual([]);
    expect(items[0]!.entries.map((e) => e.id)).toEqual([2, 1]);
  });

  it('keeps separate edits, other people and other records apart', () => {
    const items = groupHistory(
      [
        entry({ id: 4, createdAt: '2026-10-16T05:00:03Z' }),
        entry({ id: 3, createdAt: '2026-10-16T05:00:01Z' }),
        entry({ id: 2, actor: karthik, action: 'TASK_ASSIGNED', actionLabel: 'Task assigned' }),
        entry({ id: 1, action: 'RECORD_CREATED', actionLabel: 'Task comment created', entityType: 'TASK_COMMENT', entityId: 9, entityLabel: null, parentType: 'TASK', parentId: 128 }),
      ],
      task,
    );
    expect(items.map((i) => i.entries.map((e) => e.id))).toEqual([[4], [3], [2], [1]]);
    expect(items[3]).toMatchObject({ kind: 'created', title: 'Task comment created' });
  });

  it('labels a child record by its name and keeps a named event on its own', () => {
    const items = groupHistory(
      [
        entry({ id: 2, action: 'RECORD_CREATED', actionLabel: 'Approval step created', entityType: 'APPROVAL_STEP', entityId: 3, entityLabel: 'Finance review' }),
        entry({ id: 1, action: 'APPROVAL_DECIDED', actionLabel: 'Approval decided', entityType: 'APPROVAL', entityId: 5, details: { decision: 'APPROVED' }, actor: null, createdAt: '2026-10-15T05:00:00Z' }),
      ],
      { type: 'APPROVAL', id: 5 },
    );
    expect(items[0]).toMatchObject({ subject: 'Finance review', kind: 'created' });
    expect(items[1]).toMatchObject({ kind: 'event', actor: null, subject: null });
    expect(items[1]!.details.facts).toEqual([['Decision', 'APPROVED']]);
  });

  it('hides finance amounts for people without Finance access', () => {
    const [item] = groupHistory([entry({ details: { changes: { budget: { from: 1000, to: 1500, finance: true } } } })], task, { canSeeFinance: false });
    expect(item!.details.changes).toEqual([{ field: 'Budget', before: 'Hidden', after: 'Hidden', masked: 'finance' }]);
    const [visible] = groupHistory([entry({ details: { changes: { budget: { from: 1000, to: 1500, finance: true } } } })], task, { canSeeFinance: true });
    expect(visible!.details.changes).toEqual([{ field: 'Budget', before: '1000', after: '1500' }]);
  });
});
