import { LifeBuoy, Plus } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { PRIORITY_LABELS } from '@/features/tasks/task-meta';
import { PRIORITIES } from '@/features/tasks/types';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useInitialSearch } from '@/lib/use-initial-search';

import { useTicketCategories, useTickets } from './api';
import { CreateTicketDialog } from './CreateTicketDialog';
import { TicketDrawer } from './TicketDrawer';
import { TicketTable } from './TicketTable';
import { TICKET_STATUS_LABELS } from './ticket-meta';
import { ALL_TICKET_STATUSES, OPEN_TICKET_STATUSES, type TicketPriority, type TicketStatus, type TicketView } from './types';
import { useTicketParam } from './use-ticket-param';

const PAGE_SIZE = 25;

const STATUS_OPTIONS: { value: string; label: string; statuses: TicketStatus[] }[] = [
  { value: 'open', label: 'Open', statuses: OPEN_TICKET_STATUSES },
  { value: 'all', label: 'All statuses', statuses: [] },
  ...ALL_TICKET_STATUSES.map((s) => ({ value: s, label: TICKET_STATUS_LABELS[s], statuses: [s] })),
];

const VIEW_OPTIONS: { value: TicketView; label: string }[] = [
  { value: 'ALL', label: 'All I can see' },
  { value: 'ASSIGNED_TO_ME', label: 'Assigned to me' },
  { value: 'UNASSIGNED', label: 'Unassigned' },
  { value: 'REQUESTED_BY_ME', label: 'Raised by me' },
];

const SORTS = [
  { value: 'due,asc', label: 'Resolution due' },
  { value: 'priority,desc', label: 'Priority' },
  { value: 'created,desc', label: 'Newest' },
  { value: 'updated,desc', label: 'Recently updated' },
];

/** The help desk queue: every ticket the viewer's role lets them see. */
export default function TicketsPage() {
  const { user } = useAuth();
  const departments = useDepartments();
  const categories = useTicketCategories();
  const [ticketId, setTicketId] = useTicketParam();
  const [creating, setCreating] = useState(false);
  const initialSearch = useInitialSearch();
  const [search, setSearch] = useState(initialSearch);
  const [statusPreset, setStatusPreset] = useState('open');
  const [priority, setPriority] = useState<TicketPriority | ''>('');
  const [view, setView] = useState<TicketView>('ALL');
  const [categoryId, setCategoryId] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebouncedValue(search.trim());

  const tickets = useTickets({
    search: debouncedSearch,
    status: STATUS_OPTIONS.find((o) => o.value === statusPreset)?.statuses,
    priority: priority ? [priority] : undefined,
    view,
    categoryId: categoryId ? Number(categoryId) : undefined,
    departmentId: departmentId ? Number(departmentId) : undefined,
    sort,
    page,
    size: PAGE_SIZE,
  });

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  const filtered = Boolean(search || statusPreset !== 'open' || priority || view !== 'ALL' || categoryId || departmentId);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Tickets"
        description="Internal help desk requests, with SLA countdowns."
        actions={
          hasPermission(user, 'TICKET_CREATE') && (
            <Button onClick={() => setCreating(true)}>
              <Plus aria-hidden />
              New ticket
            </Button>
          )
        }
      />
      <Card>
        <div className="grid gap-3 border-b p-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-[1fr_repeat(6,minmax(0,9.5rem))]">
          <SearchInput placeholder="Search subject or code" aria-label="Search tickets" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
          <Select aria-label="Status" value={statusPreset} onChange={(e) => filter(setStatusPreset)(e.target.value)}>
            {STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
          <Select aria-label="Priority" value={priority} onChange={(e) => filter(setPriority)(e.target.value as TicketPriority | '')}>
            <option value="">Any priority</option>
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {PRIORITY_LABELS[p]}
              </option>
            ))}
          </Select>
          <Select aria-label="View" value={view} onChange={(e) => filter(setView)(e.target.value as TicketView)}>
            {VIEW_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
          <Select aria-label="Category" value={categoryId} onChange={(e) => filter(setCategoryId)(e.target.value)}>
            <option value="">All categories</option>
            {categories.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </Select>
          <Select aria-label="Team" value={departmentId} onChange={(e) => filter(setDepartmentId)(e.target.value)}>
            <option value="">All teams</option>
            {departments.data?.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </Select>
          <Select aria-label="Sort" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>

        {tickets.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading tickets">
            {Array.from({ length: 8 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : tickets.isError ? (
          <ErrorState error={tickets.error} title="Couldn't load tickets" onRetry={() => void tickets.refetch()} />
        ) : tickets.data.content.length === 0 ? (
          <EmptyState
            icon={LifeBuoy}
            title={filtered ? 'No tickets match these filters' : 'No open tickets'}
            description={filtered ? 'Try a different search or clear some filters.' : 'All caught up. New requests will appear here.'}
          />
        ) : (
          <>
            <TicketTable tickets={tickets.data.content} onOpen={(t) => setTicketId(t.id)} dimmed={tickets.isPlaceholderData} />
            <Pagination {...tickets.data} onPageChange={setPage} />
          </>
        )}
      </Card>

      <CreateTicketDialog open={creating} onOpenChange={setCreating} onCreated={(t) => setTicketId(t.id)} />
      <TicketDrawer ticketId={ticketId} onClose={() => setTicketId(null)} />
    </div>
  );
}
