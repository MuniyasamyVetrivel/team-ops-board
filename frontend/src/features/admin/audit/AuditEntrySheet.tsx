import { ArrowRight, Globe, Monitor } from 'lucide-react';

import { UserCell } from '@/components/common/UserAvatar';
import { Badge } from '@/components/ui/badge';
import { Dialog, DialogBody, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDateTime } from '@/lib/format';

import type { AuditLogItem } from './api';
import { describeDetails } from './audit-details';

/** One audit entry: who did what to which record, the before/after values and where the request came from. */
export function AuditEntrySheet({ entry, onClose }: { entry: AuditLogItem | null; onClose: () => void }) {
  return (
    <Dialog open={entry !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="sm:max-w-xl">{entry && <EntryBody entry={entry} />}</SheetContent>
    </Dialog>
  );
}

function EntryBody({ entry }: { entry: AuditLogItem }) {
  const details = describeDetails(entry.details);
  const nothing = details.changes.length + details.added.length + details.removed.length + details.facts.length === 0;

  return (
    <>
      <DialogHeader>
        <div className="flex flex-wrap items-center gap-2">
          {entry.moduleLabel && <Badge tone="neutral">{entry.moduleLabel}</Badge>}
          <span className="font-mono text-xs text-muted-foreground">{entry.action}</span>
        </div>
        <DialogTitle>{entry.actionLabel}</DialogTitle>
        <DialogDescription>{formatDateTime(entry.createdAt)}</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-6 overflow-y-auto">
        <dl className="grid grid-cols-[8rem_minmax(0,1fr)] gap-x-4 gap-y-3 text-sm">
          <dt className="text-muted-foreground">By</dt>
          <dd>{entry.actor ? <UserCell name={entry.actor.fullName} detail={entry.actor.email} /> : <span className="text-muted-foreground">System</span>}</dd>
          <dt className="text-muted-foreground">Record</dt>
          <dd>
            {entry.entityType ? (
              <span>
                {entry.entityType}
                {entry.entityId !== null && <span className="text-muted-foreground"> #{entry.entityId}</span>}
              </span>
            ) : (
              '—'
            )}
          </dd>
        </dl>

        {details.changes.length > 0 && (
          <section aria-labelledby="audit-changes">
            <h3 id="audit-changes" className="mb-2 text-sm font-semibold">
              Changes
            </h3>
            <div className="overflow-hidden rounded-lg border">
              <Table aria-label="Before and after values">
                <TableHeader>
                  <TableRow>
                    <TableHead>Field</TableHead>
                    <TableHead>Before</TableHead>
                    <TableHead className="w-6" aria-hidden />
                    <TableHead>After</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {details.changes.map((change) => (
                    <TableRow key={change.field}>
                      <TableCell className="align-top font-medium">{change.field}</TableCell>
                      <TableCell className="align-top break-words text-muted-foreground line-through decoration-muted-foreground/40">{change.before}</TableCell>
                      <TableCell className="align-top">
                        <ArrowRight className="size-3.5 text-muted-foreground" aria-label="changed to" />
                      </TableCell>
                      <TableCell className="align-top break-words">{change.after}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </section>
        )}

        {(details.added.length > 0 || details.removed.length > 0) && (
          <section className="space-y-3">
            {details.added.length > 0 && (
              <div>
                <h3 className="mb-1.5 text-sm font-semibold">Added</h3>
                <ul className="flex flex-wrap gap-1.5" aria-label="Added">
                  {details.added.map((item) => (
                    <li key={item}>
                      <Badge tone="success">+ {item}</Badge>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {details.removed.length > 0 && (
              <div>
                <h3 className="mb-1.5 text-sm font-semibold">Removed</h3>
                <ul className="flex flex-wrap gap-1.5" aria-label="Removed">
                  {details.removed.map((item) => (
                    <li key={item}>
                      <Badge tone="danger">− {item}</Badge>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </section>
        )}

        {details.facts.length > 0 && (
          <section aria-labelledby="audit-facts">
            <h3 id="audit-facts" className="mb-2 text-sm font-semibold">
              Details
            </h3>
            <dl className="grid grid-cols-[10rem_minmax(0,1fr)] gap-x-4 gap-y-2 text-sm">
              {details.facts.map(([key, value]) => (
                <div key={key} className="contents">
                  <dt className="text-muted-foreground">{key}</dt>
                  <dd className="break-words">{value}</dd>
                </div>
              ))}
            </dl>
          </section>
        )}

        {nothing && <p className="text-sm text-muted-foreground">No further details were recorded for this action.</p>}

        <section className="space-y-1.5 border-t pt-4 text-xs text-muted-foreground">
          <p className="flex items-center gap-2">
            <Globe className="size-3.5" aria-hidden />
            IP address: {entry.ipAddress ?? '—'}
          </p>
          <p className="flex items-start gap-2 break-all">
            <Monitor className="mt-px size-3.5 shrink-0" aria-hidden />
            {entry.userAgent ?? 'No browser recorded'}
          </p>
        </section>
      </DialogBody>
    </>
  );
}
