import { useQueryClient } from '@tanstack/react-query';
import { CircleCheck, RotateCcw } from 'lucide-react';
import type { ReactNode } from 'react';
import { toast } from 'sonner';

import { ErrorState } from '@/components/common/ErrorState';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { PRIORITY_LABELS } from '@/features/tasks/task-meta';
import { PRIORITIES } from '@/features/tasks/types';
import { useTeamDirectory } from '@/features/team/api';
import { errorMessage, toApiError } from '@/lib/api/errors';
import { formatDate, formatDateTime } from '@/lib/format';
import { cn } from '@/lib/utils';

import { ticketKeys, toUpdateTicketInput, useAssignTicket, useChangeTicketStatus, useTicket, useTicketCategories, useUpdateTicket } from './api';
import { SlaCountdown, SlaStateBadge, TicketStatusBadge } from './TicketBadges';
import { ConversationSection, TicketFilesSection, TicketHistorySection } from './TicketDrawerSections';
import { TICKET_STATUS_LABELS } from './ticket-meta';
import type { SlaStatus, TicketDetail, TicketPriority, TicketStatus, UpdateTicketInput } from './types';

interface TicketDrawerProps {
  ticketId: number | null;
  onClose: () => void;
}

export function TicketDrawer({ ticketId, onClose }: TicketDrawerProps) {
  const query = useTicket(ticketId);
  return (
    <Dialog open={ticketId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent aria-describedby={undefined} className="sm:max-w-3xl">
        {query.isPending ? (
          <div className="space-y-4 p-6" role="status" aria-label="Loading ticket">
            <DialogTitle className="sr-only">Loading ticket</DialogTitle>
            <Skeleton className="h-8 w-2/3" />
            <Skeleton className="h-40" />
            <Skeleton className="h-60" />
          </div>
        ) : query.isError ? (
          <div className="p-6">
            <DialogTitle className="sr-only">Ticket unavailable</DialogTitle>
            <ErrorState error={query.error} title="Couldn't open this ticket" onRetry={() => void query.refetch()} />
          </div>
        ) : (
          <TicketDrawerBody key={query.data.id} ticket={query.data} />
        )}
      </SheetContent>
    </Dialog>
  );
}

async function run(action: Promise<unknown>, success?: string) {
  try {
    await action;
    if (success) toast.success(success);
  } catch (error) {
    toast.error(errorMessage(error));
  }
}

function TicketDrawerBody({ ticket }: { ticket: TicketDetail }) {
  const { user } = useAuth();
  const changeStatus = useChangeTicketStatus(ticket.id);
  const isRequester = ticket.requester?.id === user?.id;
  const canConfirm = isRequester && ticket.status === 'RESOLVED' && ticket.permissions.allowedStatuses.includes('CLOSED');

  return (
    <>
      <div className="space-y-3 border-b px-6 py-5 pr-12">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="font-mono text-muted-foreground">{ticket.code}</span>
          <TicketStatusBadge status={ticket.status} />
          {ticket.status !== 'RESOLVED' && ticket.status !== 'CLOSED' && <SlaStateBadge state={ticket.sla.overall} />}
          {!ticket.permissions.canWork && <span className="text-xs text-muted-foreground">{isRequester ? 'You raised this ticket' : 'View only'}</span>}
        </div>
        <DialogTitle className="text-xl leading-snug">{ticket.subject}</DialogTitle>
        <DialogDescription className="sr-only">Ticket details for {ticket.subject}</DialogDescription>
        {canConfirm && (
          <div className="flex flex-wrap items-center gap-2 rounded-lg border border-status-success/30 bg-status-success/5 px-3 py-2.5 text-sm">
            <span className="flex-1">The team marked this as resolved. Is everything working?</span>
            <Button size="sm" disabled={changeStatus.isPending} onClick={() => void run(changeStatus.mutateAsync('CLOSED'), 'Thanks! Ticket closed')}>
              <CircleCheck aria-hidden />
              Yes, close it
            </Button>
            <Button size="sm" variant="outline" disabled={changeStatus.isPending} onClick={() => void run(changeStatus.mutateAsync('OPEN'), 'Ticket reopened')}>
              <RotateCcw aria-hidden />
              No, reopen
            </Button>
          </div>
        )}
      </div>

      <div className="flex-1 overflow-y-auto">
        <div className="space-y-6 px-6 py-5">
          <Properties ticket={ticket} />
          <SlaPanel ticket={ticket} />
          <section>
            <h3 className="mb-2 text-sm font-semibold">Description</h3>
            {ticket.description ? (
              <p className="text-sm whitespace-pre-wrap">{ticket.description}</p>
            ) : (
              <p className="text-sm text-muted-foreground">No description.</p>
            )}
          </section>
        </div>

        <Tabs defaultValue="conversation" className="pb-6">
          <TabsList className="overflow-x-auto">
            <TabsTrigger value="conversation">Conversation ({ticket.comments.length})</TabsTrigger>
            <TabsTrigger value="files">Files ({ticket.attachments.length})</TabsTrigger>
            <TabsTrigger value="history">History</TabsTrigger>
          </TabsList>
          <div className="px-6 pt-4">
            <TabsContent value="conversation">
              <ConversationSection ticket={ticket} />
            </TabsContent>
            <TabsContent value="files">
              <TicketFilesSection ticket={ticket} />
            </TabsContent>
            <TabsContent value="history">
              <TicketHistorySection ticket={ticket} />
            </TabsContent>
          </div>
        </Tabs>
      </div>
    </>
  );
}

function Property({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[7rem_1fr] items-center gap-3 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <div className="min-w-0">{children}</div>
    </div>
  );
}

/** Saves one field at a time with the ticket's version; a conflict reloads the ticket. */
function useFieldSaver(ticket: TicketDetail) {
  const update = useUpdateTicket(ticket.id);
  const queryClient = useQueryClient();
  return {
    pending: update.isPending,
    save: async (patch: Partial<Omit<UpdateTicketInput, 'version'>>) => {
      try {
        await update.mutateAsync(toUpdateTicketInput(ticket, patch));
      } catch (error) {
        const stale = toApiError(error)?.code === 'STALE_UPDATE';
        if (stale) void queryClient.invalidateQueries({ queryKey: ticketKeys.detail(ticket.id) });
        toast.error(stale ? 'Someone else just changed this ticket. It has been reloaded; please try again.' : errorMessage(error));
      }
    },
  };
}

function Properties({ ticket }: { ticket: TicketDetail }) {
  const { user } = useAuth();
  const { permissions } = ticket;
  const { save, pending } = useFieldSaver(ticket);
  const changeStatus = useChangeTicketStatus(ticket.id);
  const assign = useAssignTicket(ticket.id);
  const categories = useTicketCategories();
  const departments = useDepartments();
  // Agents are offered the handling department's people; the server re-checks every assignment.
  const people = useTeamDirectory({ departmentId: ticket.department.id, status: 'ACTIVE', size: 100, sort: 'name,asc' }, permissions.canAssign);
  const disabled = !permissions.canWork || pending;
  const statusOptions: TicketStatus[] = [ticket.status, ...permissions.allowedStatuses.filter((s) => s !== ticket.status)];
  const canChangeStatus = permissions.canWork && permissions.allowedStatuses.length > 0;

  return (
    <div className="grid gap-x-8 gap-y-3 sm:grid-cols-2">
      <Property label="Status">
        <Select
          aria-label="Status"
          value={ticket.status}
          disabled={!canChangeStatus || changeStatus.isPending}
          onChange={(event) => {
            const next = event.target.value as TicketStatus;
            void run(changeStatus.mutateAsync(next), `Moved to ${TICKET_STATUS_LABELS[next]}`);
          }}
        >
          {statusOptions.map((s) => (
            <option key={s} value={s}>
              {TICKET_STATUS_LABELS[s]}
            </option>
          ))}
        </Select>
      </Property>
      <Property label="Assignee">
        {permissions.canWork ? (
          <Select
            aria-label="Assignee"
            value={ticket.assignee?.id ?? ''}
            disabled={assign.isPending}
            onChange={(event) => void run(assign.mutateAsync(event.target.value ? Number(event.target.value) : null))}
          >
            <option value="">Unassigned</option>
            {ticket.assignee && ticket.assignee.id !== user?.id && !people.data?.content.some((p) => p.id === ticket.assignee?.id) && (
              <option value={ticket.assignee.id}>{ticket.assignee.fullName}</option>
            )}
            {user && <option value={user.id}>Me ({user.fullName})</option>}
            {permissions.canAssign &&
              people.data?.content
                .filter((p) => p.id !== user?.id)
                .map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.fullName}
                  </option>
                ))}
          </Select>
        ) : (
          <span>{ticket.assignee?.fullName ?? <span className="text-muted-foreground">Not assigned yet</span>}</span>
        )}
      </Property>
      <Property label="Priority">
        <Select aria-label="Priority" value={ticket.priority} disabled={disabled} onChange={(event) => void save({ priority: event.target.value as TicketPriority })}>
          {PRIORITIES.map((p) => (
            <option key={p} value={p}>
              {PRIORITY_LABELS[p]}
            </option>
          ))}
        </Select>
      </Property>
      <Property label="Category">
        <Select aria-label="Category" value={ticket.category?.id ?? ''} disabled={disabled} onChange={(event) => void save({ categoryId: Number(event.target.value) })}>
          {!ticket.category && <option value="">No category</option>}
          {ticket.category && !categories.data?.some((c) => c.id === ticket.category?.id) && <option value={ticket.category.id}>{ticket.category.name}</option>}
          {categories.data?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </Select>
      </Property>
      <Property label="Team">
        <Select aria-label="Team" value={ticket.department.id} disabled={disabled} onChange={(event) => void save({ departmentId: Number(event.target.value) })}>
          {!departments.data?.some((d) => d.id === ticket.department.id) && <option value={ticket.department.id}>{ticket.department.name}</option>}
          {departments.data?.map((d) => (
            <option key={d.id} value={d.id} disabled={d.status !== 'ACTIVE'}>
              {d.name}
            </option>
          ))}
        </Select>
      </Property>
      <Property label="Requester">
        <span>{ticket.requester?.fullName ?? 'Former user'}</span>
      </Property>
      <Property label="Raised">
        <span className="text-muted-foreground">{formatDateTime(ticket.createdAt)}</span>
      </Property>
      {ticket.closedAt ? (
        <Property label="Closed">
          <span className="text-muted-foreground">{formatDate(ticket.closedAt)}</span>
        </Property>
      ) : (
        ticket.resolvedAt && (
          <Property label="Resolved">
            <span className="text-muted-foreground">{formatDateTime(ticket.resolvedAt)}</span>
          </Property>
        )
      )}
    </div>
  );
}

function SlaRow({ label, status, doneAt }: { label: string; status: SlaStatus; doneAt: string | null }) {
  const percent = Math.min(status.elapsedPercent, 100);
  const bar = status.met === true ? 'bg-status-success' : status.state === 'BREACHED' ? 'bg-status-danger' : status.state === 'WARNING' ? 'bg-status-warning' : 'bg-primary';
  return (
    <div className="space-y-1.5">
      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
        <span className="font-medium">{label}</span>
        <SlaCountdown status={status} />
      </div>
      <div
        className="h-1.5 overflow-hidden rounded-full bg-muted"
        role="meter"
        aria-label={`${label}: ${status.elapsedPercent}% of the target used`}
        aria-valuenow={status.elapsedPercent}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div className={cn('h-full rounded-full', bar)} style={{ width: `${percent}%` }} />
      </div>
      <p className="text-xs text-muted-foreground">
        {doneAt ? `Done ${formatDateTime(doneAt)} · due ${formatDateTime(status.dueAt)}` : `Due ${formatDateTime(status.dueAt)}`}
        {status.paused && ' · clock paused while waiting'}
      </p>
    </div>
  );
}

/** Both SLA deadlines, computed by the server. Due times were fixed when the ticket was raised. */
function SlaPanel({ ticket }: { ticket: TicketDetail }) {
  return (
    <section className="space-y-3 rounded-lg border p-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold">SLA</h3>
        {ticket.slaPolicy && <span className="text-xs text-muted-foreground">{ticket.slaPolicy} policy</span>}
      </div>
      <SlaRow label="First response" status={ticket.sla.firstResponse} doneAt={ticket.firstRespondedAt} />
      <SlaRow label="Resolution" status={ticket.sla.resolution} doneAt={ticket.resolvedAt} />
    </section>
  );
}
