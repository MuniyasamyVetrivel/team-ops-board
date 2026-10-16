import { CalendarRange, Pencil, Plus } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { Detail, Muted } from '@/features/celebrations/Details';
import { DetailsWithHistory } from '@/features/history/DetailsWithHistory';

import { useAllocations, useEngagement, type Engagement } from './api';
import { AllocationFormDialog } from './AllocationFormDialog';
import { BenchErrorState, BillableBadge, ConsultantCell, PeriodStatusBadge } from './BenchBadges';
import { dateRange } from './bench-meta';
import { allocationHref, clientHref } from './bench-paths';
import { useBenchTool } from './bench-tool';
import { EngagementFormDialog } from './EngagementFormDialog';

/** A client engagement: its dates, account manager and the consultants allocated to it; with its History. */
export function EngagementSheet({ engagementId, onClose }: { engagementId: number | null; onClose: () => void }) {
  return (
    <Dialog open={engagementId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="sm:max-w-xl">{engagementId !== null && <EngagementBody engagementId={engagementId} onClose={onClose} />}</SheetContent>
    </Dialog>
  );
}

function EngagementBody({ engagementId, onClose }: { engagementId: number; onClose: () => void }) {
  const { canEdit } = useBenchTool();
  const engagement = useEngagement(engagementId);
  const [editing, setEditing] = useState(false);

  if (engagement.isPending) {
    return (
      <div className="space-y-4 p-6" role="status" aria-label="Loading the engagement">
        <Skeleton className="h-12 w-2/3" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (engagement.isError) return <BenchErrorState error={engagement.error} title="Couldn't load the engagement" onRetry={() => void engagement.refetch()} />;

  const e = engagement.data;
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <DialogTitle>{e.name}</DialogTitle>
        <DialogDescription>
          <Link to={clientHref(e.client.id)} className="hover:underline">
            {e.client.name}
          </Link>
        </DialogDescription>
        <div className="flex flex-wrap items-center gap-2">
          <PeriodStatusBadge status={e.status} />
          {canEdit && (
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit engagement
            </Button>
          )}
        </div>
      </DialogHeader>
      <DetailsWithHistory type="CLIENT_ENGAGEMENT" id={e.id}>
        <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 pb-6">
          <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Detail label="Dates">{dateRange(e.startDate, e.endDate)}</Detail>
            <Detail label="Location">{e.location ?? <Muted>None</Muted>}</Detail>
            <Detail label="Account manager">{e.accountManager ? <UserCell name={e.accountManager.fullName} user={e.accountManager} detail={e.accountManager.email} /> : <Muted>None</Muted>}</Detail>
          </dl>
          {e.notes && <Detail label="Notes">{<span className="whitespace-pre-wrap">{e.notes}</span>}</Detail>}
          <EngagementAllocations engagement={e} canEdit={canEdit} />
        </div>
      </DetailsWithHistory>
      <EngagementFormDialog open={editing} onOpenChange={setEditing} engagement={e} onDeleted={onClose} />
    </div>
  );
}

function EngagementAllocations({ engagement, canEdit }: { engagement: Engagement; canEdit: boolean }) {
  const allocations = useAllocations({ engagementId: engagement.id, size: 100, sort: 'start,desc' });
  const [adding, setAdding] = useState(false);

  return (
    <section aria-label="Allocations" className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-medium">Consultants on this engagement</h3>
        {canEdit && engagement.status !== 'ENDED' && (
          <Button variant="outline" size="sm" onClick={() => setAdding(true)}>
            <Plus aria-hidden />
            Allocate
          </Button>
        )}
      </div>
      {allocations.isPending ? (
        <Skeleton className="h-24" />
      ) : allocations.isError ? (
        <BenchErrorState error={allocations.error} title="Couldn't load allocations" onRetry={() => void allocations.refetch()} />
      ) : allocations.data.content.length === 0 ? (
        <EmptyState icon={CalendarRange} title="Nobody is allocated yet" className="rounded-lg border border-dashed py-8" />
      ) : (
        <ul className="divide-y rounded-lg border">
          {allocations.data.content.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center gap-x-3 gap-y-2 px-4 py-3">
              <div className="min-w-0 flex-1">
                <ConsultantCell consultantId={a.consultant.id} name={a.consultant.fullName} user={a.consultant.user} detail={`${a.role} · ${dateRange(a.startDate, a.endDate)}`} />
              </div>
              <Link to={allocationHref(a.id)} className="text-sm font-semibold tabular-nums hover:underline">
                {a.allocationPercent}%
              </Link>
              <PeriodStatusBadge status={a.status} />
              <BillableBadge billable={a.billable} />
            </li>
          ))}
        </ul>
      )}
      {canEdit && <AllocationFormDialog open={adding} onOpenChange={setAdding} engagementId={engagement.id} />}
    </section>
  );
}
