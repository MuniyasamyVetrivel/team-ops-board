import { CircleCheck, History, Inbox, LifeBuoy, MessageCircleQuestion, Plus, UserCheck, type LucideIcon } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useMyTicketSummary, useTickets } from '@/features/tickets/api';
import { CreateTicketDialog } from '@/features/tickets/CreateTicketDialog';
import { TicketDrawer } from '@/features/tickets/TicketDrawer';
import { TicketTable } from '@/features/tickets/TicketTable';
import { OPEN_TICKET_STATUSES, type MyTicketSummary, type TicketQuery } from '@/features/tickets/types';
import { useTicketParam } from '@/features/tickets/use-ticket-param';
import { cn } from '@/lib/utils';

type Tab = 'raised' | 'waiting' | 'confirm' | 'assigned' | 'history';

interface TabDefinition {
  id: Tab;
  label: string;
  icon: LucideIcon;
  tone: string;
  count?: (s: MyTicketSummary) => number;
  query: TicketQuery;
  empty: string;
  /** Agents only. */
  agent?: boolean;
}

/** Each card is also a tab: "Waiting on you" lists exactly the tickets that need your reply. */
const TABS: TabDefinition[] = [
  { id: 'raised', label: 'Raised by me', icon: Inbox, tone: 'text-primary', count: (s) => s.requestedOpen, query: { view: 'REQUESTED_BY_ME', status: OPEN_TICKET_STATUSES, sort: 'created,desc' }, empty: 'You have no open tickets.' },
  { id: 'waiting', label: 'Waiting on me', icon: MessageCircleQuestion, tone: 'text-status-warning', count: (s) => s.waitingOnMe, query: { view: 'REQUESTED_BY_ME', status: ['WAITING_FOR_REQUESTER'], sort: 'updated,desc' }, empty: 'Nobody is waiting for your reply.' },
  { id: 'confirm', label: 'Resolved, to confirm', icon: CircleCheck, tone: 'text-status-success', count: (s) => s.resolvedToConfirm, query: { view: 'REQUESTED_BY_ME', status: ['RESOLVED'], sort: 'updated,desc' }, empty: 'Nothing to confirm.' },
  { id: 'assigned', label: 'Assigned to me', icon: UserCheck, tone: 'text-primary', count: (s) => s.assignedOpen, query: { view: 'ASSIGNED_TO_ME', status: OPEN_TICKET_STATUSES, sort: 'due,asc' }, empty: 'No tickets are assigned to you.', agent: true },
  { id: 'history', label: 'All my requests', icon: History, tone: 'text-muted-foreground', query: { view: 'REQUESTED_BY_ME', sort: 'created,desc' }, empty: "You haven't raised any tickets yet." },
];

const PAGE_SIZE = 20;

/** Tickets the signed-in user raised, plus (for agents) the ones assigned to them. */
export default function MyTicketsPage() {
  const { user } = useAuth();
  const summary = useMyTicketSummary();
  const [tab, setTab] = useState<Tab>('raised');
  const [page, setPage] = useState(0);
  const [ticketId, setTicketId] = useTicketParam();
  const [creating, setCreating] = useState(false);
  const tabs = TABS.filter((t) => !t.agent || hasPermission(user, 'TICKET_EDIT'));
  const active = tabs.find((t) => t.id === tab) ?? tabs[0]!;
  const tickets = useTickets({ ...active.query, page, size: PAGE_SIZE });

  return (
    <div className="space-y-6">
      <PageHeader
        title="My Tickets"
        description="Requests you raised and help desk work assigned to you."
        actions={
          hasPermission(user, 'TICKET_CREATE') && (
            <Button onClick={() => setCreating(true)}>
              <Plus aria-hidden />
              New ticket
            </Button>
          )
        }
      />

      <div className={cn('grid grid-cols-2 gap-3 sm:grid-cols-3', tabs.length > 4 ? 'xl:grid-cols-5' : 'xl:grid-cols-4')} role="tablist" aria-label="Ticket views">
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={active.id === t.id}
            onClick={() => {
              setTab(t.id);
              setPage(0);
            }}
            className={cn(
              'rounded-xl border bg-card p-4 text-left shadow-xs transition-colors hover:bg-muted/40 focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none',
              active.id === t.id && 'border-primary ring-1 ring-primary',
            )}
          >
            <span className="flex items-center gap-2 text-sm text-muted-foreground">
              <t.icon className={cn('size-4', t.tone)} aria-hidden />
              {t.label}
            </span>
            {t.count ? (
              summary.isPending ? (
                <Skeleton className="mt-2 h-8 w-10" />
              ) : (
                <span className={cn('mt-1 block text-3xl font-semibold tabular-nums', t.id === 'waiting' && (summary.data?.waitingOnMe ?? 0) > 0 && 'text-status-warning')}>
                  {summary.data ? t.count(summary.data) : '—'}
                </span>
              )
            ) : (
              <span className="mt-1 block text-sm text-muted-foreground">Open and closed</span>
            )}
          </button>
        ))}
      </div>

      <Card>
        <div className="flex items-center gap-2 border-b px-4 py-3">
          <active.icon className={cn('size-4', active.tone)} aria-hidden />
          <h2 className="text-sm font-semibold">{active.label}</h2>
        </div>
        {tickets.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading tickets">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : tickets.isError ? (
          <ErrorState error={tickets.error} title="Couldn't load your tickets" onRetry={() => void tickets.refetch()} />
        ) : tickets.data.content.length === 0 ? (
          <EmptyState icon={LifeBuoy} title={active.empty} />
        ) : (
          <>
            <TicketTable tickets={tickets.data.content} onOpen={(t) => setTicketId(t.id)} hideRequester={active.query.view === 'REQUESTED_BY_ME'} dimmed={tickets.isPlaceholderData} />
            <Pagination {...tickets.data} onPageChange={setPage} />
          </>
        )}
      </Card>

      <CreateTicketDialog open={creating} onOpenChange={setCreating} onCreated={(t) => setTicketId(t.id)} />
      <TicketDrawer ticketId={ticketId} onClose={() => setTicketId(null)} />
    </div>
  );
}
