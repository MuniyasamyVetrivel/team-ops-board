import { Pencil } from 'lucide-react';
import { useState } from 'react';

import { ErrorState } from '@/components/common/ErrorState';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';

import { useImportantDay } from './api';
import { OccasionBadge } from './CelebrationBadges';
import { DAY_CATEGORY_LABELS, formatOccasionDate, formatShortDate, RECURRENCE_LABELS, REGION_LABELS } from './celebration-meta';
import { Detail, Muted } from './Details';
import { ImportantDayFormDialog } from './ImportantDayFormDialog';
import { LinkedInPostDialog, type PostTarget } from './LinkedInPostDialog';
import { Files, PostSection } from './OccasionSections';

/** An important day: its next occurrence and LinkedIn post, notes and creatives. */
export function ImportantDaySheet({ dayId, onClose }: { dayId: number | null; onClose: () => void }) {
  return (
    <Dialog open={dayId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="sm:max-w-lg">{dayId !== null && <DayBody dayId={dayId} onClose={onClose} />}</SheetContent>
    </Dialog>
  );
}

function DayBody({ dayId, onClose }: { dayId: number; onClose: () => void }) {
  const day = useImportantDay(dayId);
  const [editing, setEditing] = useState(false);
  const [posting, setPosting] = useState<PostTarget | null>(null);

  if (day.isPending) {
    return (
      <div className="space-y-4 p-6" role="status" aria-label="Loading the important day">
        <Skeleton className="h-10 w-2/3" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (day.isError) return <ErrorState error={day.error} title="Couldn't load the important day" onRetry={() => void day.refetch()} />;

  const d = day.data;
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <OccasionBadge category="IMPORTANT_DAY" className="self-start" />
        <DialogTitle>{d.name}</DialogTitle>
        <DialogDescription>
          {REGION_LABELS[d.region]} · {DAY_CATEGORY_LABELS[d.category]} · {RECURRENCE_LABELS[d.recurrence]}
        </DialogDescription>
        {d.canEdit && (
          <div>
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit important day
            </Button>
          </div>
        )}
      </DialogHeader>
      <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 pb-6">
        <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
          <Detail label={d.recurrence === 'YEARLY' ? 'Next on' : 'On'}>{formatOccasionDate(d.nextOccurrence)}</Detail>
          {d.recurrence === 'YEARLY' && <Detail label="Since">{formatShortDate(d.date)}</Detail>}
          <Detail label="Description">{d.description ?? <Muted>None</Muted>}</Detail>
        </dl>
        {d.marketingNotes && (
          <section aria-labelledby="day-notes" className="space-y-1">
            <h3 id="day-notes" className="text-sm font-semibold">
              Marketing notes
            </h3>
            <p className="rounded-lg border bg-muted/30 px-3 py-2 text-sm whitespace-pre-line">{d.marketingNotes}</p>
          </section>
        )}
        <PostSection
          post={d.linkedIn}
          date={d.nextOccurrence}
          canEdit={d.canEdit}
          onEdit={() => setPosting({ subjectType: 'IMPORTANT_DAY', subjectId: d.id, occurrenceDate: d.nextOccurrence, title: d.name, post: d.linkedIn })}
        />
        <section aria-labelledby="day-files" className="space-y-2">
          <h3 id="day-files" className="text-sm font-semibold">
            Creatives
          </h3>
          <Files kind="important-days" id={d.id} canEdit={d.canEdit} />
        </section>
      </div>
      <ImportantDayFormDialog open={editing} onOpenChange={setEditing} day={d} onDeleted={onClose} />
      <LinkedInPostDialog target={posting} onClose={() => setPosting(null)} />
    </div>
  );
}
