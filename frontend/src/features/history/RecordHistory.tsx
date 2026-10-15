import { ArrowRight, Cog, History, KeyRound, LoaderCircle, Lock, Pencil, Plus, RotateCcw, Trash2, Zap, type LucideIcon } from 'lucide-react';
import { useMemo } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { UserAvatar } from '@/components/common/UserAvatar';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import type { ChangeRow } from '@/features/admin/audit/audit-details';
import { canSeeFinance } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { formatRelative, formatTimestamp } from '@/lib/format';
import { cn } from '@/lib/utils';

import { useRecordHistory, type HistoryRecordType } from './api';
import { groupHistory, type HistoryItem, type HistoryKind } from './history-groups';

const KIND: Record<HistoryKind, { icon: LucideIcon; label: string; tone: 'success' | 'primary' | 'danger' | 'warning' | 'neutral' }> = {
  created: { icon: Plus, label: 'Created', tone: 'success' },
  updated: { icon: Pencil, label: 'Edited', tone: 'primary' },
  deleted: { icon: Trash2, label: 'Deleted', tone: 'danger' },
  restored: { icon: RotateCcw, label: 'Restored', tone: 'warning' },
  purged: { icon: Trash2, label: 'Permanently deleted', tone: 'danger' },
  event: { icon: Zap, label: 'Event', tone: 'neutral' },
};

/**
 * A record's History tab: who changed what and when, newest first, with field-by-field before → after values. The
 * record's own changes and its children's (comments, checklist, attachments, steps) are shown; a module's named
 * event and the automatic change for the same action appear as one item. Money amounts are hidden from people
 * without Finance access.
 */
export function RecordHistory({ type, id, className }: { type: HistoryRecordType; id: number; className?: string }) {
  const { user } = useAuth();
  const history = useRecordHistory(type, id);
  const finance = canSeeFinance(user);
  const items = useMemo(
    () => groupHistory(history.data?.pages.flatMap((page) => page.content) ?? [], { type, id }, { canSeeFinance: finance }),
    [history.data, type, id, finance],
  );

  if (history.isPending) {
    return (
      <div className={cn('space-y-4', className)} role="status" aria-label="Loading history">
        {Array.from({ length: 4 }, (_, i) => (
          <div key={i} className="flex gap-3">
            <Skeleton className="size-8 rounded-full" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-4 w-1/2" />
              <Skeleton className="h-3 w-1/3" />
              <Skeleton className="h-10 w-full" />
            </div>
          </div>
        ))}
      </div>
    );
  }
  if (history.isError) {
    return (
      <div className={className}>
        <ErrorState error={history.error} title="Couldn't load the history" onRetry={() => void history.refetch()} />
      </div>
    );
  }
  if (items.length === 0) {
    return (
      <EmptyState
        icon={History}
        title="No history yet"
        description="Changes to this record will appear here, with who made them and when."
        className={cn('py-10', className)}
      />
    );
  }

  return (
    <div className={cn('space-y-4', className)}>
      <ol className="relative space-y-6" aria-label="History">
        <span className="absolute top-2 bottom-2 left-4 w-px bg-border" aria-hidden />
        {items.map((item) => (
          <HistoryEntry key={item.key} item={item} />
        ))}
      </ol>
      {history.hasNextPage && (
        <div className="flex justify-center">
          <Button variant="outline" size="sm" onClick={() => void history.fetchNextPage()} disabled={history.isFetchingNextPage}>
            {history.isFetchingNextPage && <LoaderCircle className="animate-spin" aria-hidden />}
            Show older changes
          </Button>
        </div>
      )}
    </div>
  );
}

function HistoryEntry({ item }: { item: HistoryItem }) {
  const kind = KIND[item.kind];
  const actorName = item.actor?.fullName ?? 'System';
  const { changes, added, removed, facts } = item.details;

  return (
    <li className="relative flex gap-3">
      {item.actor ? (
        <UserAvatar name={item.actor.fullName} className="relative ring-4 ring-background" />
      ) : (
        <span className="relative flex size-8 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground ring-4 ring-background" aria-hidden>
          <Cog className="size-4" />
        </span>
      )}
      <article className="min-w-0 flex-1 space-y-2" aria-label={`${item.title} by ${actorName}`}>
        <header className="space-y-0.5">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-sm font-semibold">{item.title}</h3>
            {item.kind !== 'event' && (
              <Badge tone={kind.tone}>
                <kind.icon aria-hidden />
                {kind.label}
              </Badge>
            )}
          </div>
          {item.subject && <p className="truncate text-sm text-muted-foreground">{item.subject}</p>}
          <p className="text-xs text-muted-foreground">
            <span className={cn('font-medium', item.actor ? 'text-foreground' : 'text-muted-foreground')}>{actorName}</span>
            {' · '}
            <time dateTime={item.at} title={formatRelative(item.at)}>
              {formatTimestamp(item.at)}
            </time>
          </p>
        </header>

        {changes.length > 0 && (
          <dl className="divide-y rounded-lg border text-sm" aria-label="Changes">
            {changes.map((change) => (
              <ChangeLine key={change.field} change={change} kind={item.kind} />
            ))}
          </dl>
        )}

        {(added.length > 0 || removed.length > 0) && (
          <ul className="flex flex-wrap gap-1.5" aria-label="Added and removed">
            {added.map((value) => (
              <li key={`+${value}`}>
                <Badge tone="success">+ {value}</Badge>
              </li>
            ))}
            {removed.map((value) => (
              <li key={`-${value}`}>
                <Badge tone="danger">− {value}</Badge>
              </li>
            ))}
          </ul>
        )}

        {facts.length > 0 && (
          <dl className="grid grid-cols-[minmax(0,9rem)_minmax(0,1fr)] gap-x-3 gap-y-1 text-xs">
            {facts.map(([key, value]) => (
              <div key={key} className="contents">
                <dt className="text-muted-foreground">{key}</dt>
                <dd className="break-words">{value}</dd>
              </div>
            ))}
          </dl>
        )}
      </article>
    </li>
  );
}

/** One field: "Status  TODO → IN_PROGRESS". A new record shows only its value, a deleted one only its last value. */
function ChangeLine({ change, kind }: { change: ChangeRow; kind: HistoryKind }) {
  return (
    <div className="grid grid-cols-[minmax(0,8rem)_minmax(0,1fr)] gap-3 px-3 py-2">
      <dt className="font-medium break-words">{change.field}</dt>
      <dd className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
        {change.masked === 'finance' ? (
          <span className="inline-flex items-center gap-1.5 text-muted-foreground">
            <Lock className="size-3.5" aria-hidden />
            Hidden <span className="text-xs">(finance amount — needs Finance access)</span>
          </span>
        ) : change.masked === 'secret' ? (
          <span className="inline-flex items-center gap-1.5 text-muted-foreground">
            <KeyRound className="size-3.5" aria-hidden />
            Changed (value not recorded)
          </span>
        ) : kind === 'created' ? (
          <span className="break-words">{change.after}</span>
        ) : kind === 'deleted' || kind === 'purged' ? (
          <span className="break-words text-muted-foreground">{change.before}</span>
        ) : (
          <>
            <span className="break-words text-muted-foreground line-through decoration-muted-foreground/40">
              <span className="sr-only">from </span>
              {change.before}
            </span>
            <ArrowRight className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
            <span className="break-words">
              <span className="sr-only">to </span>
              {change.after}
            </span>
          </>
        )}
      </dd>
    </div>
  );
}
