import type { AuditLogItem } from './api';
import { typeLabel } from './audit-details';

/** "Task #128" with its name ("TSK-000128") and the record it belongs to underneath ("in Task #12"). */
export function RecordName({ entry }: { entry: AuditLogItem }) {
  if (!entry.entityType) return <span className="text-muted-foreground">—</span>;
  return (
    <span className="block min-w-0">
      <span className="block truncate whitespace-nowrap">
        {entry.entityTypeLabel ?? entry.entityType}
        {entry.entityId !== null && <span className="text-muted-foreground"> #{entry.entityId}</span>}
      </span>
      {entry.entityLabel && (
        <span className="block truncate text-xs text-muted-foreground" title={entry.entityLabel}>
          {entry.entityLabel}
        </span>
      )}
      {entry.parentType && (
        <span className="block truncate text-xs text-muted-foreground">
          in {typeLabel(entry.parentType)} #{entry.parentId}
        </span>
      )}
    </span>
  );
}
