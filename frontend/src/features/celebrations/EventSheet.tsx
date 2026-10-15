import { ExternalLink, Pencil } from 'lucide-react';
import { useState } from 'react';

import { ErrorState } from '@/components/common/ErrorState';
import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { formatDateTime } from '@/lib/format';
import { DetailsWithHistory } from '@/features/history/DetailsWithHistory';

import { useMarketingEvent } from './api';
import { EventStatusBadge, OccasionBadge } from './CelebrationBadges';
import { formatOccasionDate, formatShortDate, formatTime } from './celebration-meta';
import { Detail, Muted } from './Details';
import { EventFormDialog } from './EventFormDialog';
import { LinkedInPostDialog, type PostTarget } from './LinkedInPostDialog';
import { Files, PostSection } from './OccasionSections';

/** A marketing event: when and where, links, owner, linked email campaign, LinkedIn post and attachments. */
export function EventSheet({ eventId, onClose }: { eventId: number | null; onClose: () => void }) {
  return (
    <Dialog open={eventId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="sm:max-w-lg">{eventId !== null && <EventBody eventId={eventId} onClose={onClose} />}</SheetContent>
    </Dialog>
  );
}

function EventBody({ eventId, onClose }: { eventId: number; onClose: () => void }) {
  const event = useMarketingEvent(eventId);
  const [editing, setEditing] = useState(false);
  const [posting, setPosting] = useState<PostTarget | null>(null);

  if (event.isPending) {
    return (
      <div className="space-y-4 p-6" role="status" aria-label="Loading the event">
        <Skeleton className="h-10 w-2/3" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (event.isError) return <ErrorState error={event.error} title="Couldn't load the event" onRetry={() => void event.refetch()} />;

  const e = event.data;
  const cancelled = e.status === 'CANCELLED';
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <div className="flex flex-wrap items-center gap-2">
          <OccasionBadge category={e.category} />
          <EventStatusBadge status={e.status} />
        </div>
        <DialogTitle>{e.title}</DialogTitle>
        <DialogDescription>
          {formatOccasionDate(e.startDate)} · {formatTime(e.startAt)}
        </DialogDescription>
        {e.canEdit && (
          <div>
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit event
            </Button>
          </div>
        )}
      </DialogHeader>
      <DetailsWithHistory type="MARKETING_EVENT" id={e.id}>
        <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 pb-6">
          <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Detail label="Owner">
              <UserCell name={e.owner.fullName} />
            </Detail>
            <Detail label="Location">{e.location ?? <Muted>None</Muted>}</Detail>
            <Detail label="Online link">{e.onlineLink ? <ExternalAnchor href={e.onlineLink}>Join online</ExternalAnchor> : <Muted>None</Muted>}</Detail>
            <Detail label="Registration">{e.registrationLink ? <ExternalAnchor href={e.registrationLink}>Register</ExternalAnchor> : <Muted>None</Muted>}</Detail>
            {e.endAt && <Detail label="Ends">{formatDateTime(e.endAt)}</Detail>}
            {e.emailCampaignId !== null && (
              <Detail label="Email campaign">{e.emailCampaign ? `${e.emailCampaign.name} · ${formatShortDate(e.emailCampaign.campaignDate)}` : <Muted>Linked</Muted>}</Detail>
            )}
          </dl>
          {e.description && <p className="rounded-lg border bg-muted/30 px-3 py-2 text-sm whitespace-pre-line">{e.description}</p>}
          {!cancelled && (
            <PostSection
              post={e.linkedIn}
              date={e.startDate}
              canEdit={e.canEdit}
              onEdit={() => setPosting({ subjectType: 'EVENT', subjectId: e.id, occurrenceDate: e.startDate, title: e.title, post: e.linkedIn })}
            />
          )}
          <section aria-labelledby="event-files" className="space-y-2">
            <h3 id="event-files" className="text-sm font-semibold">
              Attachments
            </h3>
            <Files kind="events" id={e.id} canEdit={e.canEdit} />
          </section>
        </div>
      </DetailsWithHistory>
      <EventFormDialog open={editing} onOpenChange={setEditing} event={e} onDeleted={onClose} />
      <LinkedInPostDialog target={posting} onClose={() => setPosting(null)} />
    </div>
  );
}

function ExternalAnchor({ href, children }: { href: string; children: string }) {
  return (
    <a href={href} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-primary hover:underline">
      {children}
      <ExternalLink className="size-3.5" aria-hidden />
    </a>
  );
}
