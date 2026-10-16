import { Briefcase, Plus } from 'lucide-react';
import { useState } from 'react';
import { useSearchParams } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useIdParam } from '@/lib/use-id-param';
import { useInitialSearch } from '@/lib/use-initial-search';
import { cn } from '@/lib/utils';

import { PERIOD_STATUSES, useClients, useEngagements, type PeriodStatus } from './api';
import { BenchErrorState, PeriodStatusBadge } from './BenchBadges';
import { dateRange, PERIOD_STATUS_META } from './bench-meta';
import { useBenchTool } from './bench-tool';
import { BenchSection } from './BenchSection';
import { EngagementFormDialog } from './EngagementFormDialog';
import { EngagementSheet } from './EngagementSheet';

const SORTS = [
  { value: 'start,desc', label: 'Newest start' },
  { value: 'end,asc', label: 'Ending soonest' },
  { value: 'name,asc', label: 'Name' },
  { value: 'client,asc', label: 'Client' },
  { value: 'updated,desc', label: 'Recently updated' },
];

/** Client engagements (separate from internal Projects). Opens one from ?engagement=12; ?client=3 filters by client. */
export default function EngagementsPage() {
  const { canEdit } = useBenchTool();
  const [params] = useSearchParams();
  const [openId, setOpenId] = useIdParam('engagement');
  const [adding, setAdding] = useState(false);
  const [search, setSearch] = useState(useInitialSearch());
  const [clientId, setClientId] = useState(params.get('clientId') ?? '');
  const [status, setStatus] = useState<PeriodStatus | ''>('');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(search.trim());
  const clients = useClients({ size: 100, sort: 'name,asc' });
  const engagements = useEngagements({ search: debounced, clientId: clientId ? Number(clientId) : undefined, status: status || undefined, sort, page, size: 25 });
  const filtered = Boolean(debounced || clientId || status);

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <div className="space-y-4">
      <BenchSection
        title="Engagements"
        description="Client work consultants are allocated to, with dates and account managers."
        actions={
          canEdit && (
            <Button onClick={() => setAdding(true)}>
              <Plus aria-hidden />
              New engagement
            </Button>
          )
        }
      />
      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-40 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
          <SearchInput placeholder="Search engagement, client or location" aria-label="Search engagements" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
          <Select aria-label="Client" value={clientId} onChange={(e) => filter(setClientId)(e.target.value)}>
            <option value="">All clients</option>
            {clients.data?.content.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </Select>
          <Select aria-label="Status" value={status} onChange={(e) => filter(setStatus)(e.target.value as PeriodStatus | '')}>
            <option value="">All statuses</option>
            {PERIOD_STATUSES.map((s) => (
              <option key={s} value={s}>
                {PERIOD_STATUS_META[s].label}
              </option>
            ))}
          </Select>
          <Select aria-label="Sort engagements" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>

        {engagements.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading engagements">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : engagements.isError ? (
          <BenchErrorState error={engagements.error} title="Couldn't load engagements" onRetry={() => void engagements.refetch()} />
        ) : engagements.data.content.length === 0 ? (
          <EmptyState
            icon={Briefcase}
            title={filtered ? 'No engagements match these filters' : 'No engagements yet'}
            description={!filtered && canEdit ? 'Add a client first, then create an engagement for the work.' : undefined}
          />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Engagement</TableHead>
                  <TableHead>Dates</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Location</TableHead>
                  <TableHead>Account manager</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={cn(engagements.isPlaceholderData && 'opacity-60')}>
                {engagements.data.content.map((e) => (
                  <TableRow key={e.id}>
                    <TableCell className="max-w-xs">
                      <button type="button" className="block max-w-full text-left" onClick={() => setOpenId(e.id)}>
                        <span className="block truncate font-medium hover:underline">{e.name}</span>
                        <span className="block truncate text-xs text-muted-foreground">{e.client.name}</span>
                      </button>
                    </TableCell>
                    <TableCell className="text-sm whitespace-nowrap">{dateRange(e.startDate, e.endDate)}</TableCell>
                    <TableCell>
                      <PeriodStatusBadge status={e.status} />
                    </TableCell>
                    <TableCell className="text-sm">{e.location ?? <span className="text-muted-foreground">—</span>}</TableCell>
                    <TableCell>{e.accountManager ? <UserCell name={e.accountManager.fullName} user={e.accountManager} /> : <span className="text-sm text-muted-foreground">—</span>}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination {...engagements.data} onPageChange={setPage} />
          </>
        )}
      </Card>
      <EngagementSheet engagementId={openId} onClose={() => setOpenId(null)} />
      {canEdit && <EngagementFormDialog open={adding} onOpenChange={setAdding} clientId={clientId ? Number(clientId) : undefined} onSaved={setOpenId} />}
    </div>
  );
}
