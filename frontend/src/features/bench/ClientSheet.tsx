import { Briefcase, CircleCheck, CirclePause, Pencil, Plus } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { Detail, Muted } from '@/features/celebrations/Details';
import { DetailsWithHistory } from '@/features/history/DetailsWithHistory';

import { useClient, useEngagements, type Client, type ClientStatus } from './api';
import { BenchErrorState, PeriodStatusBadge } from './BenchBadges';
import { CLIENT_STATUS_LABELS, dateRange } from './bench-meta';
import { engagementHref } from './bench-paths';
import { useBenchTool } from './bench-tool';
import { ClientFormDialog } from './ClientFormDialog';
import { EngagementFormDialog } from './EngagementFormDialog';

export function ClientStatusBadge({ status }: { status: ClientStatus }) {
  return status === 'ACTIVE' ? (
    <Badge tone="success">
      <CircleCheck aria-hidden />
      {CLIENT_STATUS_LABELS.ACTIVE}
    </Badge>
  ) : (
    <Badge tone="neutral">
      <CirclePause aria-hidden />
      {CLIENT_STATUS_LABELS.INACTIVE}
    </Badge>
  );
}

/** A client and its engagements; with its History. */
export function ClientSheet({ clientId, onClose }: { clientId: number | null; onClose: () => void }) {
  return (
    <Dialog open={clientId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="sm:max-w-lg">{clientId !== null && <ClientBody clientId={clientId} onClose={onClose} />}</SheetContent>
    </Dialog>
  );
}

function ClientBody({ clientId, onClose }: { clientId: number; onClose: () => void }) {
  const { canEdit } = useBenchTool();
  const client = useClient(clientId);
  const [editing, setEditing] = useState(false);

  if (client.isPending) {
    return (
      <div className="space-y-4 p-6" role="status" aria-label="Loading the client">
        <Skeleton className="h-12 w-2/3" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (client.isError) return <BenchErrorState error={client.error} title="Couldn't load the client" onRetry={() => void client.refetch()} />;

  const c = client.data;
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <DialogTitle>{c.name}</DialogTitle>
        <DialogDescription>{c.location ?? 'No location recorded'}</DialogDescription>
        <div className="flex flex-wrap items-center gap-2">
          <ClientStatusBadge status={c.status} />
          {canEdit && (
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit client
            </Button>
          )}
        </div>
      </DialogHeader>
      <DetailsWithHistory type="CLIENT" id={c.id}>
        <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 pb-6">
          <Detail label="Notes">{c.notes ? <span className="whitespace-pre-wrap">{c.notes}</span> : <Muted>None</Muted>}</Detail>
          <ClientEngagements client={c} canEdit={canEdit} />
        </div>
      </DetailsWithHistory>
      <ClientFormDialog open={editing} onOpenChange={setEditing} client={c} onDeleted={onClose} />
    </div>
  );
}

function ClientEngagements({ client, canEdit }: { client: Client; canEdit: boolean }) {
  const engagements = useEngagements({ clientId: client.id, size: 100, sort: 'start,desc' });
  const [adding, setAdding] = useState(false);

  return (
    <section aria-label="Engagements" className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-medium">Engagements</h3>
        {canEdit && client.status === 'ACTIVE' && (
          <Button variant="outline" size="sm" onClick={() => setAdding(true)}>
            <Plus aria-hidden />
            New engagement
          </Button>
        )}
      </div>
      {engagements.isPending ? (
        <Skeleton className="h-24" />
      ) : engagements.isError ? (
        <BenchErrorState error={engagements.error} title="Couldn't load engagements" onRetry={() => void engagements.refetch()} />
      ) : engagements.data.content.length === 0 ? (
        <EmptyState icon={Briefcase} title="No engagements yet" className="rounded-lg border border-dashed py-8" />
      ) : (
        <ul className="divide-y rounded-lg border">
          {engagements.data.content.map((e) => (
            <li key={e.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3">
              <Link to={engagementHref(e.id)} className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium hover:underline">{e.name}</span>
                <span className="block text-xs text-muted-foreground">{dateRange(e.startDate, e.endDate)}</span>
              </Link>
              <PeriodStatusBadge status={e.status} />
            </li>
          ))}
        </ul>
      )}
      {canEdit && <EngagementFormDialog open={adding} onOpenChange={setAdding} clientId={client.id} />}
    </section>
  );
}
