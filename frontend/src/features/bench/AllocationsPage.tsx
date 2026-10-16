import { CalendarRange, Plus } from 'lucide-react';
import { useState } from 'react';
import { useSearchParams } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useIdParam } from '@/lib/use-id-param';
import { useInitialSearch } from '@/lib/use-initial-search';
import { cn } from '@/lib/utils';

import { PERIOD_STATUSES, useAllocations, useClients, type PeriodStatus } from './api';
import { AllocationFormDialog } from './AllocationFormDialog';
import { AllocationSheet } from './AllocationSheet';
import { BenchErrorState, BillableBadge, ConsultantCell, PeriodStatusBadge } from './BenchBadges';
import { dateRange, PERIOD_STATUS_META, rollOffLabel, rollOffTone } from './bench-meta';
import { useBenchTool } from './bench-tool';
import { BenchSection } from './BenchSection';

const SORTS = [
  { value: 'start,desc', label: 'Newest start' },
  { value: 'end,asc', label: 'Ending soonest' },
  { value: 'consultant,asc', label: 'Consultant' },
  { value: 'percent,desc', label: 'Highest %' },
  { value: 'updated,desc', label: 'Recently updated' },
];

function initialOption<T extends string>(value: string | null, options: readonly T[]): T | '' {
  return value !== null && (options as readonly string[]).includes(value) ? (value as T) : '';
}

/** Every allocation: consultant, engagement, dates, percentage and billable. Opens one from ?allocation=12 (roll-off reminders). */
export default function AllocationsPage() {
  const { canEdit } = useBenchTool();
  const [params] = useSearchParams();
  const [openId, setOpenId] = useIdParam('allocation');
  const [adding, setAdding] = useState(false);
  const [search, setSearch] = useState(useInitialSearch());
  const [status, setStatus] = useState<PeriodStatus | ''>(initialOption(params.get('status'), PERIOD_STATUSES));
  const [billable, setBillable] = useState<'' | 'true' | 'false'>('');
  const [clientId, setClientId] = useState('');
  const [sort, setSort] = useState(initialOption(params.get('sort'), SORTS.map((s) => s.value)) || SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(search.trim());
  const clients = useClients({ size: 100, sort: 'name,asc' });
  const allocations = useAllocations({
    search: debounced,
    status: status || undefined,
    billable: billable === '' ? undefined : billable === 'true',
    clientId: clientId ? Number(clientId) : undefined,
    sort,
    page,
    size: 25,
  });
  const filtered = Boolean(debounced || status || billable || clientId);

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <div className="space-y-4">
      <BenchSection
        title="Allocations"
        description="Who is on which engagement, for how much of their time and until when."
        actions={
          canEdit && (
            <Button onClick={() => setAdding(true)}>
              <Plus aria-hidden />
              New allocation
            </Button>
          )
        }
      />
      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-40 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
          <SearchInput placeholder="Search consultant, role or engagement" aria-label="Search allocations" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
          <Select aria-label="Status" value={status} onChange={(e) => filter(setStatus)(e.target.value as PeriodStatus | '')}>
            <option value="">All statuses</option>
            {PERIOD_STATUSES.map((s) => (
              <option key={s} value={s}>
                {PERIOD_STATUS_META[s].label}
              </option>
            ))}
          </Select>
          <Select aria-label="Billable" value={billable} onChange={(e) => filter(setBillable)(e.target.value as '' | 'true' | 'false')}>
            <option value="">Billable and non-billable</option>
            <option value="true">Billable</option>
            <option value="false">Non-billable</option>
          </Select>
          <Select aria-label="Client" value={clientId} onChange={(e) => filter(setClientId)(e.target.value)}>
            <option value="">All clients</option>
            {clients.data?.content.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </Select>
          <Select aria-label="Sort allocations" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>

        {allocations.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading allocations">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : allocations.isError ? (
          <BenchErrorState error={allocations.error} title="Couldn't load allocations" onRetry={() => void allocations.refetch()} />
        ) : allocations.data.content.length === 0 ? (
          <EmptyState
            icon={CalendarRange}
            title={filtered ? 'No allocations match these filters' : 'No allocations yet'}
            description={!filtered && canEdit ? 'Allocate a consultant to a client engagement to start tracking utilisation.' : undefined}
          />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Consultant</TableHead>
                  <TableHead>Engagement</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead className="text-right">Allocation</TableHead>
                  <TableHead>Dates</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Billable</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={cn(allocations.isPlaceholderData && 'opacity-60')}>
                {allocations.data.content.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell className="max-w-56">
                      <ConsultantCell consultantId={a.consultant.id} name={a.consultant.fullName} user={a.consultant.user} detail={a.consultant.employeeCode} />
                    </TableCell>
                    <TableCell className="max-w-56">
                      <button type="button" className="block max-w-full text-left" onClick={() => setOpenId(a.id)}>
                        <span className="block truncate text-sm font-medium hover:underline">{a.engagement.name}</span>
                        <span className="block truncate text-xs text-muted-foreground">{a.engagement.client.name}</span>
                      </button>
                    </TableCell>
                    <TableCell className="max-w-48 truncate text-sm">{a.role}</TableCell>
                    <TableCell className="text-right font-semibold tabular-nums">{a.allocationPercent}%</TableCell>
                    <TableCell className="text-sm whitespace-nowrap">
                      {dateRange(a.startDate, a.endDate)}
                      {a.status === 'ACTIVE' && a.daysUntilEnd !== null && a.daysUntilEnd <= 30 && (
                        <Badge tone={rollOffTone(a.daysUntilEnd)} className="mt-1 flex w-fit">
                          {rollOffLabel(a.daysUntilEnd)}
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      <PeriodStatusBadge status={a.status} />
                    </TableCell>
                    <TableCell>
                      <BillableBadge billable={a.billable} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination {...allocations.data} onPageChange={setPage} />
          </>
        )}
      </Card>
      <AllocationSheet allocationId={openId} onClose={() => setOpenId(null)} />
      {canEdit && <AllocationFormDialog open={adding} onOpenChange={setAdding} onSaved={setOpenId} />}
    </div>
  );
}
