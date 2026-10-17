import { Building, Plus } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useIdParam } from '@/lib/use-id-param';
import { useInitialSearch } from '@/lib/use-initial-search';
import { cn } from '@/lib/utils';

import { useClients, type ClientStatus } from './api';
import { BenchErrorState } from './BenchBadges';
import { formatDateTime } from '@/lib/format';
import { ToolSection } from '@/features/modules/ToolSection';
import { useBenchTool } from './bench-tool';
import { ClientFormDialog } from './ClientFormDialog';
import { ClientSheet, ClientStatusBadge } from './ClientSheet';

const SORTS = [
  { value: 'name,asc', label: 'Name' },
  { value: 'updated,desc', label: 'Recently updated' },
];

/** Clients that own engagements. Opens one from ?client=12. */
export default function ClientsPage() {
  const { canEdit } = useBenchTool();
  const [openId, setOpenId] = useIdParam('client');
  const [adding, setAdding] = useState(false);
  const [search, setSearch] = useState(useInitialSearch());
  const [status, setStatus] = useState<ClientStatus | ''>('');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(search.trim());
  const clients = useClients({ search: debounced, status: status || undefined, sort, page, size: 25 });
  const filtered = Boolean(debounced || status);

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <div className="space-y-4">
      <ToolSection
        title="Clients"
        description="The companies consultants work for."
        actions={
          canEdit && (
            <Button onClick={() => setAdding(true)}>
              <Plus aria-hidden />
              New client
            </Button>
          )
        }
      />
      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-40 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
          <SearchInput placeholder="Search name or location" aria-label="Search clients" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
          <Select aria-label="Status" value={status} onChange={(e) => filter(setStatus)(e.target.value as ClientStatus | '')}>
            <option value="">All statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
          </Select>
          <Select aria-label="Sort clients" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>

        {clients.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading clients">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : clients.isError ? (
          <BenchErrorState error={clients.error} title="Couldn't load clients" onRetry={() => void clients.refetch()} />
        ) : clients.data.content.length === 0 ? (
          <EmptyState icon={Building} title={filtered ? 'No clients match these filters' : 'No clients yet'} description={!filtered && canEdit ? 'Add the clients your consultants work for.' : undefined} />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Client</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Engagements</TableHead>
                  <TableHead>Last updated</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={cn(clients.isPlaceholderData && 'opacity-60')}>
                {clients.data.content.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell className="max-w-sm">
                      <button type="button" className="block max-w-full text-left" onClick={() => setOpenId(c.id)}>
                        <span className="block truncate font-medium hover:underline">{c.name}</span>
                        <span className="block truncate text-xs text-muted-foreground">{c.location ?? 'No location'}</span>
                      </button>
                    </TableCell>
                    <TableCell>
                      <ClientStatusBadge status={c.status} />
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{c.engagementCount.toLocaleString()}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">{formatDateTime(c.updatedAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination {...clients.data} onPageChange={setPage} />
          </>
        )}
      </Card>
      <ClientSheet clientId={openId} onClose={() => setOpenId(null)} />
      {canEdit && <ClientFormDialog open={adding} onOpenChange={setAdding} onSaved={setOpenId} />}
    </div>
  );
}
