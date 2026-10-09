import { ExternalLink, Lock, Pencil } from 'lucide-react';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { toast } from 'sonner';

import { AttachmentList } from '@/components/common/AttachmentList';
import { ErrorState } from '@/components/common/ErrorState';
import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { parseLocalDate } from '@/features/tasks/task-meta';
import { errorMessage } from '@/lib/api/errors';

import { formatCount } from '../marketing-format';
import {
  CONTENT_STATUSES,
  downloadContentAttachment,
  useChangeContentStatus,
  useContentAttachmentMutations,
  useContentAttachments,
  useContentItem,
  type ContentItem,
  type ContentStatus,
} from './api';
import { ContentStatusBadge } from './ContentBadges';
import { ContentFormDialog } from './ContentFormDialog';
import { CONTENT_STATUS_LABELS, CONTENT_TYPE_LABELS, isLive } from './content-meta';

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
const day = (iso: string | null) => (iso ? dayFormat.format(parseLocalDate(iso)) : null);

interface ContentSheetProps {
  itemId: number | null;
  onOpenChange: (open: boolean) => void;
  canEdit: boolean;
}

/** A content item: its stage (moved in one click), publishing dates, SEO target, figures and attachments. */
export function ContentSheet({ itemId, onOpenChange, canEdit }: ContentSheetProps) {
  return (
    <Dialog open={itemId !== null} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-xl">{itemId !== null && <ContentBody itemId={itemId} canEdit={canEdit} onClose={() => onOpenChange(false)} />}</SheetContent>
    </Dialog>
  );
}

function ContentBody({ itemId, canEdit, onClose }: { itemId: number; canEdit: boolean; onClose: () => void }) {
  const item = useContentItem(itemId);
  const [editing, setEditing] = useState(false);

  if (item.isPending) {
    return (
      <div className="space-y-4 p-6" role="status" aria-label="Loading the content item">
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-10" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (item.isError) {
    return <ErrorState error={item.error} title="Couldn't load the content item" onRetry={() => void item.refetch()} />;
  }

  const c = item.data;
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <div className="flex flex-wrap items-center gap-2">
          <DialogTitle>{c.title}</DialogTitle>
          <ContentStatusBadge status={c.status} />
        </div>
        <DialogDescription className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span>{CONTENT_TYPE_LABELS[c.contentType]}</span>
          {c.url && (
            <>
              <span aria-hidden>·</span>
              <a href={c.url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 hover:underline">
                Open
                <ExternalLink className="size-3.5" aria-hidden />
              </a>
            </>
          )}
        </DialogDescription>
        {canEdit && (
          <div>
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit content
            </Button>
          </div>
        )}
      </DialogHeader>
      <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 pb-6">
        {canEdit && <StatusPicker item={c} />}

        <section aria-labelledby="content-details" className="space-y-2">
          <h3 id="content-details" className="text-sm font-semibold">
            Details
          </h3>
          <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Detail label="Planned for">{day(c.plannedDate) ?? <Muted>Not planned</Muted>}</Detail>
            <Detail label="Published on">
              {day(c.publicationDate) ?? <Muted>Not published</Muted>}
              {c.publicationLocked && (
                <span className="ml-1 inline-flex items-center gap-1 text-xs text-muted-foreground" title="Published in a closed month">
                  <Lock className="size-3.5" aria-hidden />
                  Month closed
                </span>
              )}
            </Detail>
            {c.refreshedDate && <Detail label="Refreshed on">{day(c.refreshedDate)}</Detail>}
            <Detail label="Author">{c.author ? <UserCell name={c.author.fullName} /> : <Muted>No author</Muted>}</Detail>
            <Detail label="Owner">{c.owner ? <UserCell name={c.owner.fullName} /> : <Muted>No owner</Muted>}</Detail>
            <Detail label="Target keyword">{c.targetKeywordText ?? <Muted>None</Muted>}</Detail>
            <Detail label="Target page">{c.targetPage ? c.targetPage.title : <Muted>None</Muted>}</Detail>
          </dl>
        </section>

        <section aria-label="Results" className="grid grid-cols-3 gap-3">
          <Figure label="Leads" value={c.leads} />
          <Figure label="Organic traffic" value={c.organicTraffic} />
          <Figure label="CTA clicks" value={c.ctaClicks} />
        </section>

        {c.notes && <p className="rounded-lg border bg-muted/30 px-3 py-2 text-sm whitespace-pre-line">{c.notes}</p>}

        <section aria-labelledby="content-files" className="space-y-2">
          <h3 id="content-files" className="text-sm font-semibold">
            Attachments
          </h3>
          <Attachments item={c} canEdit={canEdit} />
        </section>
      </div>
      <ContentFormDialog open={editing} onOpenChange={setEditing} item={c} onDeleted={onClose} />
    </div>
  );
}

/**
 * One click per stage, dated today: publishing sets the publication date, updating the refresh, earlier stages clear
 * both. Stages the item cannot move to (closed month, leads naming it) are disabled.
 */
function StatusPicker({ item }: { item: ContentItem }) {
  const change = useChangeContentStatus(item.id);

  async function onPick(status: ContentStatus) {
    try {
      await change.mutateAsync({ version: item.version, status });
      toast.success(`Moved to ${CONTENT_STATUS_LABELS[status].toLowerCase()}`);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  const blocked = (s: ContentStatus) => (item.publicationLocked && isLive(item.status) !== isLive(s)) || (item.leads > 0 && !isLive(s));
  return (
    <div className="space-y-1.5">
      <div role="group" aria-label="Change status" className="flex flex-wrap gap-1.5">
        {CONTENT_STATUSES.map((s) => (
          <Button key={s} type="button" size="sm" variant={s === item.status ? 'default' : 'outline'} aria-pressed={s === item.status} disabled={change.isPending || s === item.status || blocked(s)} onClick={() => void onPick(s)}>
            {CONTENT_STATUS_LABELS[s]}
          </Button>
        ))}
      </div>
      {item.leads > 0 && <p className="text-xs text-muted-foreground">Leads name this content, so it stays published.</p>}
    </div>
  );
}

function Attachments({ item, canEdit }: { item: ContentItem; canEdit: boolean }) {
  const files = useContentAttachments(item.id);
  const { upload, remove } = useContentAttachmentMutations(item.id);
  if (files.isError) return <ErrorState error={files.error} title="Couldn't load the attachments" onRetry={() => void files.refetch()} />;
  return (
    <AttachmentList
      files={files.data}
      canEdit={canEdit}
      uploading={upload.isPending}
      onUpload={(file) => upload.mutateAsync(file)}
      onRemove={(fileId) => remove.mutateAsync(fileId)}
      onDownload={(file) => downloadContentAttachment(item.id, file.fileId, file.fileName)}
    />
  );
}

function Figure({ label, value }: { label: string; value: number | null }) {
  return (
    <div className="rounded-lg border p-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-lg font-semibold tabular-nums">{formatCount(value)}</p>
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

function Muted({ children }: { children: ReactNode }) {
  return <span className="text-muted-foreground">{children}</span>;
}
