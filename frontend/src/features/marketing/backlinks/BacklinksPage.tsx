import { Download, FileUp, Link2, Lock, Plus } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { parseLocalDate } from '@/features/tasks/task-meta';
import { errorMessage } from '@/lib/api/errors';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { cn } from '@/lib/utils';

import { useImportDefinitions } from '../api';
import { CsvImportDialog } from '../components/CsvImportDialog';
import { MarketingFilterBar } from '../components/MarketingFilterBar';
import type { MarketingFilters } from '../filter-memory';
import { formatCount, periodLabel } from '../marketing-format';
import { useMarketingFilters } from '../use-marketing-filters';
import {
  BACKLINK_IMPORT_TYPE,
  BACKLINK_STATUSES,
  BACKLINK_TYPES,
  exportBacklinks,
  STAGES,
  useBacklinks,
  useBacklinkSummary,
  type Backlink,
  type BacklinkQuery,
  type BacklinkStatus,
  type BacklinkType,
  type Stage,
} from './api';
import { BacklinkStatusBadge } from './BacklinkBadges';
import { BacklinkFormDialog } from './BacklinkFormDialog';
import { BacklinkHistoryPanel } from './BacklinkHistoryPanel';
import { BACKLINK_STATUS_LABELS, BACKLINK_STATUS_STYLE, BACKLINK_TYPE_LABELS, STAGE_FIELDS, STAGE_LABELS } from './backlink-meta';
import { BacklinkSheet } from './BacklinkSheet';
import { BacklinkSummary } from './BacklinkSummary';

const SORTS = [
  { value: 'updated,desc', label: 'Recently updated' },
  { value: 'live,desc', label: 'Newest live' },
  { value: 'submitted,desc', label: 'Newest submitted' },
  { value: 'authority,desc', label: 'Highest authority' },
  { value: 'domain,asc', label: 'Domain' },
];

/** ALL: any month; ANY: a stage dated in the month; a stage: that stage dated in the month. */
type MonthScope = 'ALL' | 'ANY' | Stage;

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' });

/** The latest stage a backlink reached, with its date, for the table. */
function latestStage(b: Backlink): { stage: Stage; date: string } | null {
  for (const stage of [...STAGES].reverse()) {
    const date = b[STAGE_FIELDS[stage]];
    if (date) return { stage, date };
  }
  return null;
}

/** Backlinks (brief sections 45–46): the month's progress against the target, its history and the backlink table. */
export default function BacklinksPage() {
  const { user } = useAuth();
  const { filters } = useMarketingFilters();
  const [adding, setAdding] = useState(false);
  const [importing, setImporting] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);
  const canEdit = hasPermission(user, 'BACKLINK_EDIT');
  // Offered by the server only to users who may run it (BACKLINK_EDIT).
  const backlinkImport = useImportDefinitions().data?.find((d) => d.type === BACKLINK_IMPORT_TYPE);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Backlinks"
        description={filters ? `Monthly backlink activity against the target · ${periodLabel(filters.month, filters.year)}` : 'Monthly backlink activity against the target'}
        actions={
          canEdit && (
            <div className="flex flex-wrap gap-2">
              {backlinkImport && (
                <Button variant="outline" onClick={() => setImporting(true)}>
                  <FileUp aria-hidden />
                  Import backlinks
                </Button>
              )}
              <Button onClick={() => setAdding(true)}>
                <Plus aria-hidden />
                New backlink
              </Button>
            </div>
          )
        }
      />
      <MarketingFilterBar />
      {filters ? (
        <>
          <BacklinkSummary filters={filters} />
          <BacklinkHistoryPanel filters={filters} />
          <BacklinkList filters={filters} canEdit={canEdit} onOpen={setOpenId} />
          <BacklinkSheet backlinkId={openId} onOpenChange={(open) => !open && setOpenId(null)} canEdit={canEdit} />
        </>
      ) : (
        <Skeleton className="h-96 rounded-xl" role="status" aria-label="Loading backlinks" />
      )}
      <BacklinkFormDialog open={adding} onOpenChange={setAdding} onCreated={setOpenId} />
      {backlinkImport && <CsvImportDialog definition={backlinkImport} open={importing} onOpenChange={setImporting} />}
    </div>
  );
}

function BacklinkList({ filters, canEdit, onOpen }: { filters: MarketingFilters; canEdit: boolean; onOpen: (id: number) => void }) {
  const [search, setSearch] = useState('');
  const [type, setType] = useState('');
  const [status, setStatus] = useState('');
  const [scope, setScope] = useState<MonthScope>('ALL');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState(false);
  const debounced = useDebouncedValue(search.trim());
  // Today's pipeline (not month-bound) comes with the monthly summary.
  const summary = useBacklinkSummary({ month: filters.month, year: filters.year, ownerId: filters.ownerId ?? undefined });

  const monthBound = scope !== 'ALL';
  const query: Omit<BacklinkQuery, 'page' | 'size' | 'sort'> = {
    search: debounced,
    linkType: type ? [type as BacklinkType] : undefined,
    status: status ? [status as BacklinkStatus] : undefined,
    ownerId: filters.ownerId ?? undefined,
    month: monthBound ? filters.month : undefined,
    year: monthBound ? filters.year : undefined,
    stage: scope !== 'ALL' && scope !== 'ANY' ? scope : undefined,
  };
  const backlinks = useBacklinks({ ...query, sort, page, size: 25 });
  const month = periodLabel(filters.month, filters.year);

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  async function onExport() {
    setExporting(true);
    try {
      await exportBacklinks(query);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  }

  const filtered = Boolean(search || type || status || monthBound);
  const counts = new Map((summary.data?.pipeline ?? []).map((p) => [p.status, p.backlinks]));

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-3.5">
        <div>
          <h2 className="font-semibold">Backlinks</h2>
          <p className="text-sm text-muted-foreground">Prospects through to live links. Choose a status to list it.</p>
        </div>
        <Button variant="outline" size="sm" disabled={exporting || !backlinks.data?.totalElements} onClick={() => void onExport()}>
          <Download aria-hidden />
          Export CSV
        </Button>
      </div>

      <div role="group" aria-label="Pipeline" className="grid grid-cols-2 gap-2 border-b p-4 sm:grid-cols-3 lg:grid-cols-6">
        {BACKLINK_STATUSES.map((s) => {
          const { icon: Icon } = BACKLINK_STATUS_STYLE[s];
          const active = status === s;
          return (
            <button
              key={s}
              type="button"
              aria-pressed={active}
              onClick={() => filter(setStatus)(active ? '' : s)}
              className={cn('rounded-lg border px-3 py-2 text-left transition-colors hover:bg-accent', active && 'border-primary bg-accent')}
            >
              <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <Icon className="size-3.5" aria-hidden />
                {BACKLINK_STATUS_LABELS[s]}
              </span>
              <span className="mt-0.5 block text-lg font-semibold tabular-nums">{summary.data ? formatCount(counts.get(s) ?? 0) : '…'}</span>
            </button>
          );
        })}
      </div>

      <div className="grid gap-3 border-b p-4 sm:grid-cols-2 lg:grid-cols-[1fr_repeat(4,minmax(0,11rem))]">
        <SearchInput placeholder="Search domain, URL, anchor or code" aria-label="Search backlinks" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
        <Select aria-label="Backlink type" value={type} onChange={(e) => filter(setType)(e.target.value)}>
          <option value="">All types</option>
          {BACKLINK_TYPES.map((t) => (
            <option key={t} value={t}>
              {BACKLINK_TYPE_LABELS[t]}
            </option>
          ))}
        </Select>
        <Select aria-label="Backlink status" value={status} onChange={(e) => filter(setStatus)(e.target.value)}>
          <option value="">All statuses</option>
          {BACKLINK_STATUSES.map((s) => (
            <option key={s} value={s}>
              {BACKLINK_STATUS_LABELS[s]}
            </option>
          ))}
        </Select>
        <Select aria-label="List month" value={scope} onChange={(e) => filter(setScope)(e.target.value as MonthScope)}>
          <option value="ALL">Any month</option>
          <option value="ANY">Active in {month}</option>
          {STAGES.map((s) => (
            <option key={s} value={s}>
              {STAGE_LABELS[s]} in {month}
            </option>
          ))}
        </Select>
        <Select aria-label="Sort backlinks" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
          {SORTS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </Select>
      </div>

      {backlinks.isPending ? (
        <div className="space-y-3 p-4" role="status" aria-label="Loading the backlink table">
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} className="h-12" />
          ))}
        </div>
      ) : backlinks.isError ? (
        <ErrorState error={backlinks.error} title="Couldn't load backlinks" onRetry={() => void backlinks.refetch()} />
      ) : backlinks.data.content.length === 0 ? (
        <EmptyState
          icon={Link2}
          title={filtered ? 'No backlinks match these filters' : 'No backlinks yet'}
          description={canEdit ? 'Add prospects by hand or import an outreach tracker, then move them through the stages.' : undefined}
        />
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Referring domain</TableHead>
                <TableHead>Links to</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Latest stage</TableHead>
                <TableHead className="text-right">DA</TableHead>
                <TableHead>Owner</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody className={cn(backlinks.isPlaceholderData && 'opacity-60')}>
              {backlinks.data.content.map((b) => {
                const latest = latestStage(b);
                return (
                  <TableRow key={b.id}>
                    <TableCell className="max-w-64">
                      <button type="button" className="block max-w-full truncate text-left font-medium hover:underline" onClick={() => onOpen(b.id)}>
                        {b.referringDomain}
                      </button>
                      <p className="truncate text-xs text-muted-foreground">
                        <span className="font-mono">{b.code}</span> · {BACKLINK_TYPE_LABELS[b.linkType]}
                        {b.anchorText && ` · “${b.anchorText}”`}
                      </p>
                    </TableCell>
                    <TableCell className="max-w-56 truncate text-sm" title={b.targetUrl}>
                      {b.targetPage?.title ?? b.targetUrl}
                    </TableCell>
                    <TableCell>
                      <BacklinkStatusBadge status={b.status} />
                    </TableCell>
                    <TableCell className="text-sm whitespace-nowrap">
                      {latest ? (
                        <span className="inline-flex items-center gap-1">
                          {STAGE_LABELS[latest.stage]} {dayFormat.format(parseLocalDate(latest.date))}
                          {b.lockedDates.length > 0 && (
                            <span className="text-muted-foreground" title="Has dates in a closed month">
                              <Lock className="size-3.5" aria-hidden />
                              <span className="sr-only">(month closed)</span>
                            </span>
                          )}
                        </span>
                      ) : (
                        <span className="text-muted-foreground">Not submitted</span>
                      )}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{b.domainAuthority ?? <span className="text-muted-foreground">—</span>}</TableCell>
                    <TableCell className="max-w-44">{b.owner ? <UserCell name={b.owner.fullName} /> : <span className="text-sm text-muted-foreground">No owner</span>}</TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
          <Pagination {...backlinks.data} onPageChange={setPage} />
        </>
      )}
    </Card>
  );
}
