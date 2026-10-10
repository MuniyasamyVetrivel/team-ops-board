import { Download, FilterX, LoaderCircle, ScrollText } from 'lucide-react';
import { useMemo, useState } from 'react';
import { toast } from 'sonner';

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
import { errorMessage } from '@/lib/api/errors';
import { formatDateTime, formatRelative } from '@/lib/format';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useInitialSearch } from '@/lib/use-initial-search';
import { cn } from '@/lib/utils';

import { exportAuditLogs, useAuditCatalog, useAuditLogs, type AuditLogItem, type AuditQuery } from './api';
import { summarizeDetails } from './audit-details';
import { AuditEntrySheet } from './AuditEntrySheet';

const PAGE_SIZE = 50;

interface Filters {
  event: string;
  module: string;
  action: string;
  actorId: string;
  entityType: string;
  from: string;
  to: string;
}

const NO_FILTERS: Filters = { event: '', module: '', action: '', actorId: '', entityType: '', from: '', to: '' };

/**
 * Audit log viewer (AUDIT_VIEW): every audited action, newest first, filtered by the brief's audit events, module,
 * action, person, record type, dates and free text, with before/after values per entry and CSV export.
 */
export default function AuditLogsPage() {
  const catalog = useAuditCatalog();
  const initialSearch = useInitialSearch();
  const [search, setSearch] = useState(initialSearch);
  const [filters, setFilters] = useState<Filters>(NO_FILTERS);
  const [sort, setSort] = useState('created,desc');
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState<AuditLogItem | null>(null);
  const [exporting, setExporting] = useState(false);
  const debounced = useDebouncedValue(search.trim());
  const rangeError = filters.from && filters.to && filters.from > filters.to ? 'The start date must be on or before the end date' : null;

  const query: AuditQuery = {
    search: debounced,
    event: filters.event || undefined,
    module: filters.module || undefined,
    action: filters.action ? [filters.action] : undefined,
    actorId: filters.actorId ? Number(filters.actorId) : undefined,
    entityType: filters.entityType || undefined,
    from: filters.from || undefined,
    // An end before the start is shown as an error instead of being sent.
    to: (!rangeError && filters.to) || undefined,
    sort,
    page,
    size: PAGE_SIZE,
  };
  const logs = useAuditLogs(query);

  const actions = useMemo(
    () => (catalog.data?.actions ?? []).filter((a) => !filters.module || a.module === filters.module),
    [catalog.data, filters.module],
  );
  const filtered = debounced !== '' || Object.values(filters).some((v) => v !== '');

  const set = (patch: Partial<Filters>) => {
    setFilters((prev) => ({ ...prev, ...patch }));
    setPage(0);
  };

  const download = async () => {
    setExporting(true);
    try {
      await exportAuditLogs(query);
    } catch (error) {
      toast.error(errorMessage(error, "Couldn't export the audit log"));
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Audit Logs"
        description="Who changed what, and when: sign-ins, tasks, tickets, targets, rankings, campaigns, leads, backlinks, permissions and settings."
        actions={
          <Button variant="outline" onClick={() => void download()} disabled={exporting || logs.data?.totalElements === 0 || rangeError !== null}>
            {exporting ? <LoaderCircle className="animate-spin" aria-hidden /> : <Download aria-hidden />}
            Export CSV
          </Button>
        }
      />

      <Card className="space-y-4 p-4">
        <div className="grid gap-4 sm:gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <SearchInput
            className="sm:col-span-2"
            placeholder="Search details, people or IP address…"
            aria-label="Search audit log"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
          <Select aria-label="Audit event" value={filters.event} onChange={(e) => set({ event: e.target.value })}>
            <option value="">All audit events</option>
            {catalog.data?.events.map((e) => (
              <option key={e.key} value={e.key}>
                {e.label}
              </option>
            ))}
          </Select>
          <Select aria-label="Person" value={filters.actorId} onChange={(e) => set({ actorId: e.target.value })}>
            <option value="">Everyone</option>
            {catalog.data?.actors.map((a) => (
              <option key={a.id} value={a.id}>
                {a.fullName}
              </option>
            ))}
          </Select>
          <Select aria-label="Module" value={filters.module} onChange={(e) => set({ module: e.target.value, action: '' })}>
            <option value="">All modules</option>
            {catalog.data?.modules.map((m) => (
              <option key={m.key} value={m.key}>
                {m.label}
              </option>
            ))}
          </Select>
          <Select aria-label="Action" value={filters.action} onChange={(e) => set({ action: e.target.value })}>
            <option value="">All actions</option>
            {actions.map((a) => (
              <option key={a.action} value={a.action}>
                {a.label}
              </option>
            ))}
          </Select>
          <Select aria-label="Record type" value={filters.entityType} onChange={(e) => set({ entityType: e.target.value })}>
            <option value="">All record types</option>
            {catalog.data?.entityTypes.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </Select>
          <Select aria-label="Sort" value={sort} onChange={(e) => setSort(e.target.value)}>
            <option value="created,desc">Newest first</option>
            <option value="created,asc">Oldest first</option>
            <option value="action,asc">Action A–Z</option>
          </Select>
        </div>
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1">
            <Label htmlFor="audit-from" className="text-xs text-muted-foreground">
              From
            </Label>
            <Input id="audit-from" type="date" className="w-40" value={filters.from} max={filters.to || undefined} onChange={(e) => set({ from: e.target.value })} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="audit-to" className="text-xs text-muted-foreground">
              To
            </Label>
            <Input id="audit-to" type="date" className="w-40" value={filters.to} min={filters.from || undefined} onChange={(e) => set({ to: e.target.value })} aria-invalid={rangeError ? true : undefined} />
          </div>
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
          {logs.data && (
            <p className="ml-auto text-sm text-muted-foreground" aria-live="polite">
              {logs.data.totalElements.toLocaleString()} {logs.data.totalElements === 1 ? 'entry' : 'entries'}
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
        {logs.isPending ? (
          <div className="space-y-2 p-4" role="status" aria-label="Loading audit log">
            {Array.from({ length: 8 }, (_, i) => (
              <Skeleton key={i} className="h-11 rounded-md" />
            ))}
          </div>
        ) : logs.isError ? (
          <div className="p-6">
            <ErrorState error={logs.error} title="Couldn't load the audit log" onRetry={() => void logs.refetch()} />
          </div>
        ) : logs.data.content.length === 0 ? (
          <EmptyState
            icon={ScrollText}
            title={filtered ? 'No entries match these filters' : 'Nothing has been audited yet'}
            description={filtered ? 'Try a wider date range or fewer filters.' : 'Sign-ins and changes appear here as they happen.'}
            className="py-14"
          />
        ) : (
          <>
            <Table aria-label="Audit log entries">
              <TableHeader>
                <TableRow>
                  <TableHead>When</TableHead>
                  <TableHead>Who</TableHead>
                  <TableHead>Action</TableHead>
                  <TableHead>Record</TableHead>
                  <TableHead className="w-full">Change</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={cn(logs.isPlaceholderData && 'opacity-60')}>
                {logs.data.content.map((entry) => (
                  <TableRow
                    key={entry.id}
                    className="cursor-pointer"
                    tabIndex={0}
                    aria-label={`${entry.actionLabel} by ${entry.actor?.fullName ?? 'System'}`}
                    onClick={() => setOpen(entry)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        setOpen(entry);
                      }
                    }}
                  >
                    <TableCell className="text-sm whitespace-nowrap" title={formatDateTime(entry.createdAt)}>
                      <span className="block">{formatDateTime(entry.createdAt)}</span>
                      <span className="text-xs text-muted-foreground">{formatRelative(entry.createdAt)}</span>
                    </TableCell>
                    <TableCell className="whitespace-nowrap">
                      {entry.actor ? <UserCell name={entry.actor.fullName} detail={entry.actor.email} /> : <span className="text-sm text-muted-foreground">System</span>}
                    </TableCell>
                    <TableCell className="whitespace-nowrap">
                      <span className="block text-sm font-medium">{entry.actionLabel}</span>
                      {entry.moduleLabel && <Badge tone="neutral" className="mt-1">{entry.moduleLabel}</Badge>}
                    </TableCell>
                    <TableCell className="text-sm whitespace-nowrap">
                      {entry.entityType ? (
                        <>
                          {entry.entityType}
                          {entry.entityId !== null && <span className="text-muted-foreground"> #{entry.entityId}</span>}
                        </>
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell className="max-w-md text-sm text-muted-foreground">
                      <span className="line-clamp-2">{summarizeDetails(entry.details) || '—'}</span>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination {...logs.data} onPageChange={setPage} />
          </>
        )}
      </Card>

      <AuditEntrySheet entry={open} onClose={() => setOpen(null)} />
    </div>
  );
}
