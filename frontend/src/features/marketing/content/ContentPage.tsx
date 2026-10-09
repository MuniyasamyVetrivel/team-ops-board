import { Download, Lock, Paperclip, Plus, SquarePen } from 'lucide-react';
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

import { MarketingFilterBar } from '../components/MarketingFilterBar';
import type { MarketingFilters } from '../filter-memory';
import { formatCount, periodLabel } from '../marketing-format';
import { useMarketingFilters } from '../use-marketing-filters';
import { CONTENT_STATUSES, CONTENT_TYPES, exportContent, useContentItems, useContentSummary, type ContentQuery, type ContentStatus, type ContentType, type DateField } from './api';
import { ContentStatusBadge } from './ContentBadges';
import { ContentFormDialog } from './ContentFormDialog';
import { ContentHistoryPanel } from './ContentHistoryPanel';
import { CONTENT_STATUS_LABELS, CONTENT_STATUS_STYLE, CONTENT_TYPE_LABELS } from './content-meta';
import { ContentSheet } from './ContentSheet';
import { ContentSummary } from './ContentSummary';

const SORTS = [
  { value: 'updated,desc', label: 'Recently updated' },
  { value: 'published,desc', label: 'Newest published' },
  { value: 'planned,asc', label: 'Planned date' },
  { value: 'title,asc', label: 'Title' },
  { value: 'traffic,desc', label: 'Most traffic' },
];

type MonthScope = 'ANY' | DateField | 'ALL';

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
const day = (iso: string | null) => (iso ? dayFormat.format(parseLocalDate(iso)) : null);

/** Content & Blog (brief sections 47–48): the blog target for the month, its history, the pipeline and the content table. */
export default function ContentPage() {
  const { user } = useAuth();
  const { filters } = useMarketingFilters();
  const [adding, setAdding] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);
  const canEdit = hasPermission(user, 'CONTENT_EDIT');

  return (
    <div className="space-y-6">
      <PageHeader
        title="Content & Blog"
        description={filters ? `Blog pipeline, publishing target and leads · ${periodLabel(filters.month, filters.year)}` : 'Blog pipeline, publishing target and leads'}
        actions={
          canEdit && (
            <Button onClick={() => setAdding(true)}>
              <Plus aria-hidden />
              New content
            </Button>
          )
        }
      />
      <MarketingFilterBar />
      {filters ? (
        <>
          <ContentSummary filters={filters} />
          <ContentHistoryPanel filters={filters} />
          <ContentList filters={filters} canEdit={canEdit} onOpen={setOpenId} />
          <ContentSheet itemId={openId} onOpenChange={(open) => !open && setOpenId(null)} canEdit={canEdit} />
        </>
      ) : (
        <Skeleton className="h-96 rounded-xl" role="status" aria-label="Loading content" />
      )}
      <ContentFormDialog open={adding} onOpenChange={setAdding} onCreated={setOpenId} />
    </div>
  );
}

function ContentList({ filters, canEdit, onOpen }: { filters: MarketingFilters; canEdit: boolean; onOpen: (id: number) => void }) {
  const [search, setSearch] = useState('');
  const [type, setType] = useState('');
  const [status, setStatus] = useState('');
  const [scope, setScope] = useState<MonthScope>('ALL');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState(false);
  const debounced = useDebouncedValue(search.trim());
  // Today's pipeline (not month-bound) comes with the monthly summary.
  const summary = useContentSummary({ month: filters.month, year: filters.year, ownerId: filters.ownerId ?? undefined });

  const monthBound = scope !== 'ALL';
  const query: Omit<ContentQuery, 'page' | 'size' | 'sort'> = {
    search: debounced,
    contentType: type ? [type as ContentType] : undefined,
    status: status ? [status as ContentStatus] : undefined,
    ownerId: filters.ownerId ?? undefined,
    month: monthBound ? filters.month : undefined,
    year: monthBound ? filters.year : undefined,
    dateField: scope === 'PLANNED' || scope === 'PUBLISHED' ? scope : undefined,
  };
  const items = useContentItems({ ...query, sort, page, size: 25 });
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
      await exportContent(query);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  }

  const filtered = Boolean(search || type || status || monthBound);
  const counts = new Map((summary.data?.pipeline ?? []).map((p) => [p.status, p.items]));

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-3.5">
        <div>
          <h2 className="font-semibold">Content</h2>
          <p className="text-sm text-muted-foreground">Ideas through to published posts. Choose a stage to list it.</p>
        </div>
        <Button variant="outline" size="sm" disabled={exporting || !items.data?.totalElements} onClick={() => void onExport()}>
          <Download aria-hidden />
          Export CSV
        </Button>
      </div>

      <div role="group" aria-label="Pipeline" className="grid grid-cols-2 gap-2 border-b p-4 sm:grid-cols-3 lg:grid-cols-6">
        {CONTENT_STATUSES.map((s) => {
          const { icon: Icon } = CONTENT_STATUS_STYLE[s];
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
                {CONTENT_STATUS_LABELS[s]}
              </span>
              <span className="mt-0.5 block text-lg font-semibold tabular-nums">{summary.data ? formatCount(counts.get(s) ?? 0) : '…'}</span>
            </button>
          );
        })}
      </div>

      <div className="grid gap-3 border-b p-4 sm:grid-cols-2 lg:grid-cols-[1fr_repeat(4,minmax(0,11rem))]">
        <SearchInput placeholder="Search title, URL or keyword" aria-label="Search content" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
        <Select aria-label="Content type" value={type} onChange={(e) => filter(setType)(e.target.value)}>
          <option value="">All types</option>
          {CONTENT_TYPES.map((t) => (
            <option key={t} value={t}>
              {CONTENT_TYPE_LABELS[t]}
            </option>
          ))}
        </Select>
        <Select aria-label="Content status" value={status} onChange={(e) => filter(setStatus)(e.target.value)}>
          <option value="">All statuses</option>
          {CONTENT_STATUSES.map((s) => (
            <option key={s} value={s}>
              {CONTENT_STATUS_LABELS[s]}
            </option>
          ))}
        </Select>
        <Select aria-label="List month" value={scope} onChange={(e) => filter(setScope)(e.target.value as MonthScope)}>
          <option value="ALL">Any month</option>
          <option value="ANY">Active in {month}</option>
          <option value="PUBLISHED">Published in {month}</option>
          <option value="PLANNED">Planned for {month}</option>
        </Select>
        <Select aria-label="Sort content" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
          {SORTS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </Select>
      </div>

      {items.isPending ? (
        <div className="space-y-3 p-4" role="status" aria-label="Loading the content table">
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} className="h-12" />
          ))}
        </div>
      ) : items.isError ? (
        <ErrorState error={items.error} title="Couldn't load content" onRetry={() => void items.refetch()} />
      ) : items.data.content.length === 0 ? (
        <EmptyState
          icon={SquarePen}
          title={filtered ? 'No content matches these filters' : 'No content yet'}
          description={canEdit ? 'Add ideas and plan them for a month; publish them to count towards the blog target.' : undefined}
        />
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Title</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Planned</TableHead>
                <TableHead>Published</TableHead>
                <TableHead>Owner</TableHead>
                <TableHead className="text-right">Leads</TableHead>
                <TableHead className="text-right">
                  <span className="sr-only">Attachments</span>
                  <Paperclip className="ml-auto size-4" aria-hidden />
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody className={cn(items.isPlaceholderData && 'opacity-60')}>
              {items.data.content.map((c) => (
                <TableRow key={c.id}>
                  <TableCell className="max-w-sm">
                    <button type="button" className="block max-w-full truncate text-left font-medium hover:underline" onClick={() => onOpen(c.id)}>
                      {c.title}
                    </button>
                    <p className="truncate text-xs text-muted-foreground">
                      {CONTENT_TYPE_LABELS[c.contentType]}
                      {c.targetKeywordText && ` · ${c.targetKeywordText}`}
                    </p>
                  </TableCell>
                  <TableCell>
                    <ContentStatusBadge status={c.status} />
                  </TableCell>
                  <TableCell className="text-sm whitespace-nowrap">{day(c.plannedDate) ?? <span className="text-muted-foreground">—</span>}</TableCell>
                  <TableCell className="text-sm whitespace-nowrap">
                    <span className="inline-flex items-center gap-1">
                      {day(c.publicationDate) ?? <span className="text-muted-foreground">—</span>}
                      {c.publicationLocked && (
                        <span className="text-muted-foreground" title="Published in a closed month">
                          <Lock className="size-3.5" aria-hidden />
                          <span className="sr-only">(month closed)</span>
                        </span>
                      )}
                    </span>
                  </TableCell>
                  <TableCell className="max-w-44">{c.owner ? <UserCell name={c.owner.fullName} /> : <span className="text-sm text-muted-foreground">No owner</span>}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(c.leads)}</TableCell>
                  <TableCell className="text-right text-sm tabular-nums text-muted-foreground">
                    {c.attachments > 0 ? (
                      <span aria-label={`${c.attachments} ${c.attachments === 1 ? 'file' : 'files'}`}>{c.attachments}</span>
                    ) : (
                      <span aria-label="No files">—</span>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Pagination {...items.data} onPageChange={setPage} />
        </>
      )}
    </Card>
  );
}
