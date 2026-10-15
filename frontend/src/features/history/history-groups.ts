import { isRecordAction, type AuditLogItem } from '@/features/admin/audit/api';
import { describeDetails, type AuditDetails, type DescribeOptions } from '@/features/admin/audit/audit-details';
import type { UserSummary } from '@/lib/api/types';

/**
 * One line of a record's timeline. A module's named event ("Task status changed") and the automatic record change
 * written for the same action (the field diff) arrive as two audit entries; they are shown as one item.
 */
export interface HistoryItem {
  key: string;
  /** The newest entry's time. */
  at: string;
  /** Null for the system (background jobs). */
  actor: UserSummary | null;
  title: string;
  kind: HistoryKind;
  /** For a child record (a comment, a checklist item): its type and name; null for the record itself. */
  subject: string | null;
  details: AuditDetails;
  entries: AuditLogItem[];
}

export type HistoryKind = 'created' | 'updated' | 'deleted' | 'restored' | 'purged' | 'event';

/** Entries of one action are written within the same request; this is generous for slow requests. */
export const GROUP_WINDOW_MS = 5000;

const KIND_BY_ACTION: Record<string, HistoryKind> = {
  RECORD_CREATED: 'created',
  RECORD_UPDATED: 'updated',
  RECORD_DELETED: 'deleted',
  RECORD_RESTORED: 'restored',
  RECORD_PURGED: 'purged',
};

interface Group {
  actorId: number | null;
  entity: string;
  time: number;
  automatic: AuditLogItem | null;
  named: AuditLogItem[];
}

const entityKey = (entry: AuditLogItem) => `${entry.entityType ?? ''}#${entry.entityId ?? ''}`;

/**
 * Groups entries (newest first, as the API returns them) into timeline items. An entry joins a group when it is by
 * the same person, about the same record and within {@link GROUP_WINDOW_MS}; a group holds at most one automatic
 * change and each named event once, so two separate edits are never merged.
 */
export function groupHistory(
  entries: AuditLogItem[],
  record: { type: string; id: number },
  options: DescribeOptions = {},
): HistoryItem[] {
  const groups: Group[] = [];
  for (const entry of entries) {
    const time = new Date(entry.createdAt).getTime();
    const automatic = isRecordAction(entry.action);
    const group = groups.find(
      (g) =>
        g.actorId === (entry.actor?.id ?? null) &&
        g.entity === entityKey(entry) &&
        Math.abs(g.time - time) <= GROUP_WINDOW_MS &&
        (automatic ? g.automatic === null : !g.named.some((n) => n.action === entry.action)),
    );
    const target = group ?? { actorId: entry.actor?.id ?? null, entity: entityKey(entry), time, automatic: null, named: [] };
    if (automatic) target.automatic = entry;
    else target.named.push(entry);
    if (!group) groups.push(target);
  }
  return groups.map((group) => toItem(group, record, options));
}

function toItem(group: Group, record: { type: string; id: number }, options: DescribeOptions): HistoryItem {
  const entries = [...(group.automatic ? [group.automatic] : []), ...group.named];
  const newest = entries.reduce((a, b) => (a.createdAt >= b.createdAt ? a : b));
  const automatic = group.automatic ? describeDetails(group.automatic.details, options) : null;
  const named = group.named.map((entry) => describeDetails(entry.details, options));
  const details: AuditDetails = {
    // The automatic change holds the full field diff; a named event's own changes are only used without one.
    changes: automatic && automatic.changes.length > 0 ? automatic.changes : named.flatMap((d) => d.changes),
    added: named.flatMap((d) => d.added),
    removed: named.flatMap((d) => d.removed),
    facts: automatic && automatic.changes.length > 0 ? [] : [...named.flatMap((d) => d.facts), ...(automatic?.facts ?? [])],
  };
  const first = entries[0]!;
  const isChild = first.entityType !== record.type || first.entityId !== record.id;
  return {
    key: entries.map((e) => e.id).join('-'),
    at: newest.createdAt,
    actor: first.actor,
    title: group.named.length > 0 ? group.named.map((e) => e.actionLabel).join(' · ') : first.actionLabel,
    kind: group.automatic ? (KIND_BY_ACTION[group.automatic.action] ?? 'event') : 'event',
    subject: isChild ? first.entityLabel : null,
    details,
    entries,
  };
}
