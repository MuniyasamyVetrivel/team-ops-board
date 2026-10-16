import { Clock, FilterX, RotateCcw, Trash2 } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { UserCell } from '@/components/common/UserAvatar';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDate, formatDateTime, formatRelative } from '@/lib/format';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useInitialSearch } from '@/lib/use-initial-search';
import { cn } from '@/lib/utils';

import { canPurgeRecycleBin } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';

import { useRecycleBin, useRecycleBinCatalog, type RecycleBinItem, type RecycleBinQuery } from './api';
import { autoDeleteLabel, itemName } from './bin-rules';
import { PurgeDialog, RestoreDialog } from './RecycleBinDialogs';

const PAGE_SIZE = 50;

interface Filters {
  module: string;
  deletedById: string;
  from: string;
  to: string;
}

const NO_FILTERS: Filters = { module: '', deletedById: '', from: '', to: '' };

/** When the item is deleted for good, with an icon and words so the urgency never rests on colour alone. */
function AutoDeleteBadge({ item }: { item: RecycleBinItem }) {
  const { label, tone } = autoDeleteLabel(item.daysUntilPurge);
  return (
    <Badge tone={tone} title={`Deleted permanently on ${formatDate(item.purgeOn)}`}>
      <Clock aria-hidden />
      {label}
    </Badge>
  );
}

/**
 * The Recycle Bin (RECYCLE_BIN_MANAGE, Super Admin only by default): every department's deleted items, newest first,
 * filtered by module, who deleted them, dates and name. Restore checks for clashes first; a permanent delete needs
 * RECYCLE_BIN_PURGE as well and is confirmed by typing the item's name. Items are deleted permanently after the
 * retention period in Admin Settings.
 */
export default function RecycleBinPage() {
  const { user } = useAuth();
  const canPurge = canPurgeRecycleBin(user);
  const catalog = useRecycleBinCatalog();
  const initialSearch = useInitialSearch();
  const [search, setSearch] = useState(initialSearch);
  const [filters, setFilters] = useState<Filters>(NO_FILTERS);
  const [sort, setSort] = useState('deleted,desc');
  const [page, setPage] = useState(0);
  const [restoring, setRestoring] = useState<RecycleBinItem | null>(null);
  const [purging, setPurging] = useState<RecycleBinItem | null>(null);
  const debounced = useDebouncedValue(search.trim());
  const rangeError = filters.from && filters.to && filters.from > filters.to ? 'The start date must be on or before the end date' : null;

  const query: RecycleBinQuery = {
    search: debounced || undefined,
    module: filters.module || undefined,
    deletedById: filters.deletedById ? Number(filters.deletedById) : undefined,
    from: filters.from || undefined,
    to: (!rangeError && filters.to) || undefined,
    sort,
    page,
    size: PAGE_SIZE,
  };
  const items = useRecycleBin(query);
  const filtered = debounced !== '' || Object.values(filters).some((v) => v !== '');
  const retention = catalog.data?.retentionDays;

  const set = (patch: Partial<Filters>) => {
    setFilters((prev) => ({ ...prev, ...patch }));
    setPage(0);
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Recycle Bin"
        description={
          retention === undefined
            ? 'Deleted items from every module. Restore them as they were, or delete them permanently.'
            : `Deleted items from every module. Restore them as they were, or delete them permanently. Items are deleted permanently after ${retention} ${retention === 1 ? 'day' : 'days'}.`
        }
      />

      <Card className="space-y-4 p-4">
        <div className="grid gap-4 sm:grid-cols-2 sm:gap-6 lg:grid-cols-4">
          <SearchInput
            className="sm:col-span-2"
            placeholder="Search by name or code…"
            aria-label="Search the recycle bin"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
          <Select aria-label="Module" value={filters.module} onChange={(e) => set({ module: e.target.value })}>
            <option value="">All modules</option>
            {catalog.data?.modules.map((m) => (
              <option key={m.key} value={m.key}>
                {m.label}
              </option>
            ))}
          </Select>
          <Select aria-label="Deleted by" value={filters.deletedById} onChange={(e) => set({ deletedById: e.target.value })}>
            <option value="">Deleted by anyone</option>
            {catalog.data?.deletedBy.map((u) => (
              <option key={u.id} value={u.id}>
                {u.fullName}
              </option>
            ))}
          </Select>
        </div>
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1">
            <Label htmlFor="bin-from" className="text-xs text-muted-foreground">
              Deleted from
            </Label>
            <Input id="bin-from" type="date" className="w-40" value={filters.from} max={filters.to || undefined} onChange={(e) => set({ from: e.target.value })} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="bin-to" className="text-xs text-muted-foreground">
              To
            </Label>
            <Input id="bin-to" type="date" className="w-40" value={filters.to} min={filters.from || undefined} onChange={(e) => set({ to: e.target.value })} aria-invalid={rangeError ? true : undefined} />
          </div>
          <Select aria-label="Sort" className="w-44" value={sort} onChange={(e) => setSort(e.target.value)}>
            <option value="deleted,desc">Newest first</option>
            <option value="deleted,asc">Oldest first</option>
            <option value="name,asc">Name A–Z</option>
            <option value="type,asc">Record type</option>
          </Select>
          {filtered && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setFilters(NO_FILTERS);
                setSearch('');
                setPage(0);
              }}
            >
              <FilterX aria-hidden />
              Clear filters
            </Button>
          )}
          {items.data && (
            <p className="ml-auto text-sm text-muted-foreground" aria-live="polite">
              {items.data.totalElements.toLocaleString()} {items.data.totalElements === 1 ? 'item' : 'items'}
            </p>
          )}
        </div>
        {rangeError && (
          <p className="text-sm text-destructive" role="alert">
            {rangeError}
          </p>
        )}
      </Card>

      <Card className="overflow-hidden">
        {items.isPending ? (
          <div className="space-y-2 p-4" role="status" aria-label="Loading the recycle bin">
            {Array.from({ length: 8 }, (_, i) => (
              <Skeleton key={i} className="h-11 rounded-md" />
            ))}
          </div>
        ) : items.isError ? (
          <div className="p-6">
            <ErrorState error={items.error} title="Couldn't load the recycle bin" onRetry={() => void items.refetch()} />
          </div>
        ) : items.data.content.length === 0 ? (
          <EmptyState
            icon={Trash2}
            title={filtered ? 'No deleted items match these filters' : 'The recycle bin is empty'}
            description={filtered ? 'Try a wider date range or fewer filters.' : 'Items deleted anywhere in the app appear here until they are restored or deleted permanently.'}
            className="py-14"
          />
        ) : (
          <>
            <Table aria-label="Deleted items">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-full">Item</TableHead>
                  <TableHead>Module</TableHead>
                  <TableHead>Deleted by</TableHead>
                  <TableHead>Deleted at</TableHead>
                  <TableHead>Auto-delete</TableHead>
                  <TableHead className="text-right">
                    <span className="sr-only">Actions</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={cn(items.isPlaceholderData && 'opacity-60')}>
                {items.data.content.map((item) => {
                  const name = itemName(item);
                  return (
                    <TableRow key={item.id}>
                      <TableCell className="max-w-72">
                        <span className="block truncate font-medium" title={name}>
                          {name}
                        </span>
                        <span className="text-xs text-muted-foreground">
                          {item.entityTypeLabel}
                          {item.department && ` · ${item.department.name}`}
                        </span>
                      </TableCell>
                      <TableCell>
                        <Badge tone="neutral">{item.moduleLabel}</Badge>
                      </TableCell>
                      <TableCell className="whitespace-nowrap">
                        {item.deletedBy ? <UserCell name={item.deletedBy.fullName} detail={item.deletedBy.email} /> : <span className="text-sm text-muted-foreground">System</span>}
                      </TableCell>
                      <TableCell className="text-sm whitespace-nowrap">
                        <span className="block">{formatDateTime(item.deletedAt)}</span>
                        <span className="text-xs text-muted-foreground">{formatRelative(item.deletedAt)}</span>
                      </TableCell>
                      <TableCell>
                        <AutoDeleteBadge item={item} />
                      </TableCell>
                      <TableCell className="text-right whitespace-nowrap">
                        <div className="flex justify-end gap-1">
                          <Button variant="outline" size="sm" onClick={() => setRestoring(item)} aria-label={`Restore ${name}`}>
                            <RotateCcw aria-hidden />
                            Restore
                          </Button>
                          {canPurge && (
                            <Button variant="ghost" size="sm" className="text-destructive" onClick={() => setPurging(item)} aria-label={`Delete ${name} permanently`}>
                              <Trash2 aria-hidden />
                              Delete
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
            <Pagination {...items.data} onPageChange={setPage} />
          </>
        )}
      </Card>

      <RestoreDialog item={restoring} onClose={() => setRestoring(null)} />
      {canPurge && <PurgeDialog item={purging} onClose={() => setPurging(null)} />}
    </div>
  );
}
