import { Pencil, Timer } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { Detail, Muted } from '@/features/celebrations/Details';
import { DetailsWithHistory } from '@/features/history/DetailsWithHistory';
import { formatDateTime } from '@/lib/format';

import { useAllocation } from './api';
import { AllocationFormDialog } from './AllocationFormDialog';
import { BenchErrorState, BillableBadge, ConsultantCell, PeriodStatusBadge } from './BenchBadges';
import { dateRange, rollOffLabel, rollOffTone } from './bench-meta';
import { engagementHref } from './bench-paths';
import { useBenchTool } from './bench-tool';

/** One allocation: who, where, when, how much and whether it is billable; with its History. */
export function AllocationSheet({ allocationId, onClose }: { allocationId: number | null; onClose: () => void }) {
  return (
    <Dialog open={allocationId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="sm:max-w-lg">{allocationId !== null && <AllocationBody allocationId={allocationId} onClose={onClose} />}</SheetContent>
    </Dialog>
  );
}

function AllocationBody({ allocationId, onClose }: { allocationId: number; onClose: () => void }) {
  const { canEdit } = useBenchTool();
  const allocation = useAllocation(allocationId);
  const [editing, setEditing] = useState(false);

  if (allocation.isPending) {
    return (
      <div className="space-y-4 p-6" role="status" aria-label="Loading the allocation">
        <Skeleton className="h-12 w-2/3" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (allocation.isError) return <BenchErrorState error={allocation.error} title="Couldn't load the allocation" onRetry={() => void allocation.refetch()} />;

  const a = allocation.data;
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <DialogTitle>{a.role}</DialogTitle>
        <DialogDescription>
          {a.engagement.client.name} · {a.engagement.name}
        </DialogDescription>
        <div className="flex flex-wrap gap-2">
          <PeriodStatusBadge status={a.status} />
          <BillableBadge billable={a.billable} />
          {a.status === 'ACTIVE' && a.daysUntilEnd !== null && a.daysUntilEnd <= 30 && (
            <Badge tone={rollOffTone(a.daysUntilEnd)}>
              <Timer aria-hidden />
              {rollOffLabel(a.daysUntilEnd)}
            </Badge>
          )}
        </div>
        {canEdit && (
          <div>
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit allocation
            </Button>
          </div>
        )}
      </DialogHeader>
      <DetailsWithHistory type="ALLOCATION" id={a.id}>
        <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 pb-6">
          <div className="rounded-xl border p-4">
            <ConsultantCell consultantId={a.consultant.id} name={a.consultant.fullName} user={a.consultant.user} detail={a.consultant.employeeCode} />
          </div>
          <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Detail label="Allocation">
              <span className="font-semibold tabular-nums">{a.allocationPercent}%</span>
            </Detail>
            <Detail label="Dates">{dateRange(a.startDate, a.endDate)}</Detail>
            <Detail label="Engagement">
              <Link to={engagementHref(a.engagement.id)} className="text-primary hover:underline">
                {a.engagement.name}
              </Link>
            </Detail>
            <Detail label="Client">{a.engagement.client.name}</Detail>
            <Detail label="Last updated">{formatDateTime(a.updatedAt)}</Detail>
          </dl>
          <Detail label="Notes">{a.notes ? <span className="whitespace-pre-wrap">{a.notes}</span> : <Muted>None</Muted>}</Detail>
        </div>
      </DetailsWithHistory>
      <AllocationFormDialog open={editing} onOpenChange={setEditing} allocation={a} onDeleted={onClose} />
    </div>
  );
}
