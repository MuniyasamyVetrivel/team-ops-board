import { ExternalLink, Lock, Pencil } from 'lucide-react';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { toast } from 'sonner';

import { ErrorState } from '@/components/common/ErrorState';
import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { parseLocalDate } from '@/features/tasks/task-meta';
import { errorMessage } from '@/lib/api/errors';

import { useMarketingContext } from '../api';
import { BACKLINK_STATUSES, STAGES, useBacklink, useChangeBacklinkStatus, type Backlink, type BacklinkStatus } from './api';
import { BacklinkStatusBadge } from './BacklinkBadges';
import { BacklinkFormDialog } from './BacklinkFormDialog';
import { BACKLINK_STATUS_LABELS, BACKLINK_TYPE_LABELS, STAGE_FIELDS, STAGE_LABELS, touchesLockedDate, type StageDates } from './backlink-meta';

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

interface BacklinkSheetProps {
  backlinkId: number | null;
  onOpenChange: (open: boolean) => void;
  canEdit: boolean;
}

/** A backlink: its stages with their dates (moved in one click), where it links, and who owns it. */
export function BacklinkSheet({ backlinkId, onOpenChange, canEdit }: BacklinkSheetProps) {
  return (
    <Dialog open={backlinkId !== null} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-xl">{backlinkId !== null && <BacklinkBody backlinkId={backlinkId} canEdit={canEdit} onClose={() => onOpenChange(false)} />}</SheetContent>
    </Dialog>
  );
}

const datesOf = (b: Backlink): StageDates => ({ submittedDate: b.submittedDate, approvedDate: b.approvedDate, liveDate: b.liveDate, rejectedDate: b.rejectedDate, lostDate: b.lostDate });

function BacklinkBody({ backlinkId, canEdit, onClose }: { backlinkId: number; canEdit: boolean; onClose: () => void }) {
  const backlink = useBacklink(backlinkId);
  const [editing, setEditing] = useState(false);

  if (backlink.isPending) {
    return (
      <div className="space-y-4 p-6" role="status" aria-label="Loading the backlink">
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-10" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (backlink.isError) {
    return <ErrorState error={backlink.error} title="Couldn't load the backlink" onRetry={() => void backlink.refetch()} />;
  }

  const b = backlink.data;
  const locked = new Set(b.lockedDates);
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <div className="flex flex-wrap items-center gap-2">
          <DialogTitle>{b.referringDomain}</DialogTitle>
          <BacklinkStatusBadge status={b.status} />
        </div>
        <DialogDescription className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="font-mono text-xs">{b.code}</span>
          <span aria-hidden>·</span>
          <span>{BACKLINK_TYPE_LABELS[b.linkType]}</span>
          {b.domainAuthority !== null && (
            <>
              <span aria-hidden>·</span>
              <span>DA {b.domainAuthority}</span>
            </>
          )}
        </DialogDescription>
        {canEdit && (
          <div>
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit backlink
            </Button>
          </div>
        )}
      </DialogHeader>
      <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 pb-6">
        {canEdit && <StatusPicker backlink={b} />}

        <section aria-labelledby="backlink-stages" className="space-y-2">
          <h3 id="backlink-stages" className="text-sm font-semibold">
            Stages
          </h3>
          <ol className="grid gap-2 sm:grid-cols-5" aria-label="Stage dates">
            {STAGES.map((stage) => {
              const field = STAGE_FIELDS[stage];
              const date = b[field];
              return (
                <li key={stage} className="rounded-lg border px-3 py-2">
                  <p className="text-xs text-muted-foreground">{STAGE_LABELS[stage]}</p>
                  <p className="flex items-center gap-1 text-sm font-medium">
                    {date ? dayFormat.format(parseLocalDate(date)) : <span className="text-muted-foreground">—</span>}
                    {locked.has(field) && (
                      <span className="text-muted-foreground" title="Counted in a closed month">
                        <Lock className="size-3.5" aria-hidden />
                        <span className="sr-only">(month closed)</span>
                      </span>
                    )}
                  </p>
                </li>
              );
            })}
          </ol>
          <p className="text-xs text-muted-foreground">Each stage counts in the month of its own date.</p>
        </section>

        <section aria-labelledby="backlink-details" className="space-y-2">
          <h3 id="backlink-details" className="text-sm font-semibold">
            Details
          </h3>
          <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Detail label="Links to">
              {b.targetPage ? (
                <>
                  {b.targetPage.title}
                  <span className="block text-xs text-muted-foreground">{b.targetUrl}</span>
                </>
              ) : (
                b.targetUrl
              )}
            </Detail>
            <Detail label="Link URL">
              {b.linkUrl ? (
                <a href={b.linkUrl} target="_blank" rel="noreferrer" className="inline-flex max-w-full items-center gap-1 break-all hover:underline">
                  {b.linkUrl}
                  <ExternalLink className="size-3.5 shrink-0" aria-hidden />
                </a>
              ) : (
                <span className="text-muted-foreground">Not known yet</span>
              )}
            </Detail>
            <Detail label="Anchor text">{b.anchorText ?? <span className="text-muted-foreground">None</span>}</Detail>
            <Detail label="Owner">{b.owner ? <UserCell name={b.owner.fullName} /> : <span className="text-muted-foreground">No owner</span>}</Detail>
            <Detail label="Recorded from">{b.provider === 'CSV' ? 'CSV import' : 'Entered by hand'}</Detail>
          </dl>
        </section>

        {b.notes && <p className="rounded-lg border bg-muted/30 px-3 py-2 text-sm whitespace-pre-line">{b.notes}</p>}
      </div>
      <BacklinkFormDialog open={editing} onOpenChange={setEditing} backlink={b} onDeleted={onClose} />
    </div>
  );
}

/**
 * One click per status, dated today: the stages it needs are dated, later ones cleared. A move that would change a
 * date in a closed month is disabled (the server refuses it too).
 */
function StatusPicker({ backlink }: { backlink: Backlink }) {
  const change = useChangeBacklinkStatus(backlink.id);
  const today = useMarketingContext().data?.today;

  async function onPick(status: BacklinkStatus) {
    try {
      await change.mutateAsync({ version: backlink.version, status });
      toast.success(`${backlink.code} is now ${BACKLINK_STATUS_LABELS[status].toLowerCase()}`);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  const blocked = (s: BacklinkStatus) => today === undefined || touchesLockedDate(s, datesOf(backlink), backlink.lockedDates, today);
  return (
    <div role="group" aria-label="Change status" className="flex flex-wrap gap-1.5">
      {BACKLINK_STATUSES.map((s) => (
        <Button key={s} type="button" size="sm" variant={s === backlink.status ? 'default' : 'outline'} aria-pressed={s === backlink.status} disabled={change.isPending || s === backlink.status || blocked(s)} onClick={() => void onPick(s)}>
          {BACKLINK_STATUS_LABELS[s]}
        </Button>
      ))}
    </div>
  );
}

function Detail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-0.5">{children}</dd>
    </div>
  );
}
