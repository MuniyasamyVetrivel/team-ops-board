import { ArrowDown, ArrowUp, ClipboardPen, FileText, FileUp, KeyRound, Plus } from 'lucide-react';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';

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
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { cn } from '@/lib/utils';
import { useIdParam } from '@/lib/use-id-param';

import { useImportDefinitions } from '../api';
import { CsvImportDialog } from '../components/CsvImportDialog';
import { MarketingFilterBar } from '../components/MarketingFilterBar';
import { formatCount, formatDecimal, periodLabel } from '../marketing-format';
import type { MarketingFilters } from '../filter-memory';
import { useMarketingFilters } from '../use-marketing-filters';
import { DEVICES, KEYWORD_STATUSES, PAGE_STATUSES, PAGE_TYPES, RANKING_IMPORT_TYPE, useSeoKeywords, useSeoPageOptions, useSeoPages, type Device, type KeywordItem, type KeywordStatus, type PageStatus, type PageType } from './api';
import { KeywordFormDialog } from './KeywordFormDialog';
import { KeywordHistorySheet } from './KeywordHistorySheet';
import { KeywordTable } from './KeywordTable';
import { MonthlyRecordSheet } from './MonthlyRecordSheet';
import { MonthlySummary } from './MonthlySummary';
import { PageFormDialog } from './PageFormDialog';
import { RankingsTab } from './RankingsTab';
import { PageStatusBadge } from './SeoBadges';
import { DEVICE_LABELS, KEYWORD_STATUS_LABELS, PAGE_STATUS_LABELS, PAGE_TYPE_LABELS } from './seo-meta';

const TABS = ['rankings', 'summary', 'pages', 'keywords'] as const;
type Tab = (typeof TABS)[number];

/** "Not archived" is the default view for both pages and keywords. */
const OPEN_PAGE_STATUSES: PageStatus[] = ['ACTIVE', 'INACTIVE'];
const OPEN_KEYWORD_STATUSES: KeywordStatus[] = ['ACTIVE', 'PAUSED'];

const PAGE_SORTS = [
  { value: 'title,asc', label: 'Title' },
  { value: 'type,asc', label: 'Page type' },
  { value: 'updated,desc', label: 'Recently updated' },
];

const KEYWORD_SORTS = [
  { value: 'keyword,asc', label: 'Keyword' },
  { value: 'page,asc', label: 'Page' },
  { value: 'volume,desc', label: 'Search volume' },
  { value: 'difficulty,desc', label: 'Difficulty' },
  { value: 'updated,desc', label: 'Recently updated' },
];

/**
 * SEO Rankings: the monthly ranking table, the monthly summary, and the pages and keywords being tracked, all for the
 * selected month. The rankings table is the default tab (?tab=summary|pages|keywords for the others).
 */
export default function SeoRankingsPage() {
  const { user } = useAuth();
  const { filters } = useMarketingFilters();
  const [params, setParams] = useSearchParams();
  const requested = params.get('tab');
  const tab: Tab = TABS.find((t) => t === requested) ?? 'rankings';
  const [recording, setRecording] = useState(false);
  const [importing, setImporting] = useState(false);
  const canEdit = hasPermission(user, 'SEO_EDIT');
  // Offered by the server only to users who may run it (SEO_EDIT).
  const rankingImport = useImportDefinitions().data?.find((d) => d.type === RANKING_IMPORT_TYPE);

  function selectTab(next: string) {
    setParams(
      (existing) => {
        const updated = new URLSearchParams(existing);
        if (next === 'rankings') updated.delete('tab');
        else updated.set('tab', next);
        return updated;
      },
      { replace: true },
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="SEO Rankings"
        description={filters ? `Monthly keyword positions · ${periodLabel(filters.month, filters.year)}` : 'Monthly keyword positions'}
        actions={
          canEdit && (
            <div className="flex flex-wrap gap-2">
              {rankingImport && (
                <Button variant="outline" onClick={() => setImporting(true)}>
                  <FileUp aria-hidden />
                  Import CSV
                </Button>
              )}
              <Button disabled={!filters} onClick={() => setRecording(true)}>
                <ClipboardPen aria-hidden />
                Record rankings
              </Button>
            </div>
          )
        }
      />
      <MarketingFilterBar />
      <Card>
        <Tabs value={tab} onValueChange={selectTab}>
          <TabsList>
            <TabsTrigger value="rankings">Rankings</TabsTrigger>
            <TabsTrigger value="summary">Monthly summary</TabsTrigger>
            <TabsTrigger value="pages">Pages</TabsTrigger>
            <TabsTrigger value="keywords">Keywords</TabsTrigger>
          </TabsList>
          <TabsContent value="rankings">{filters ? <RankingsTab filters={filters} canEdit={canEdit} /> : <ListSkeleton label="Loading rankings" />}</TabsContent>
          <TabsContent value="summary">{filters ? <MonthlySummary filters={filters} /> : <ListSkeleton label="Loading the monthly summary" />}</TabsContent>
          <TabsContent value="pages">{filters ? <PagesTab filters={filters} canEdit={canEdit} /> : <ListSkeleton label="Loading pages" />}</TabsContent>
          <TabsContent value="keywords">{filters ? <KeywordsTab filters={filters} canEdit={canEdit} /> : <ListSkeleton label="Loading keywords" />}</TabsContent>
        </Tabs>
      </Card>
      {filters && <MonthlyRecordSheet open={recording} onOpenChange={setRecording} month={filters.month} year={filters.year} />}
      {rankingImport && <CsvImportDialog definition={rankingImport} open={importing} onOpenChange={setImporting} />}
    </div>
  );
}

function ListSkeleton({ label }: { label: string }) {
  return (
    <div className="space-y-3 p-4" role="status" aria-label={label}>
      {Array.from({ length: 6 }, (_, i) => (
        <Skeleton key={i} className="h-12" />
      ))}
    </div>
  );
}

function PagesTab({ filters, canEdit }: { filters: MarketingFilters; canEdit: boolean }) {
  const navigate = useNavigate();
  const [adding, setAdding] = useState(false);
  const [search, setSearch] = useState('');
  const [type, setType] = useState('');
  const [status, setStatus] = useState('open');
  const [sort, setSort] = useState(PAGE_SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(search.trim());

  const pages = useSeoPages({
    search: debounced,
    type: type ? [type as PageType] : undefined,
    status: status === 'open' ? OPEN_PAGE_STATUSES : status === 'all' ? undefined : [status as PageStatus],
    ownerId: filters.ownerId ?? undefined,
    month: filters.month,
    year: filters.year,
    sort,
    page,
    size: 25,
  });

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  const filtered = Boolean(search || type || status !== 'open' || filters.ownerId !== null);
  const open = (id: number) => void navigate(`/digital-marketing/seo/pages/${id}?month=${filters.month}&year=${filters.year}`);

  return (
    <>
      <div className="grid gap-3 border-b p-4 sm:grid-cols-2 lg:grid-cols-[1fr_repeat(3,minmax(0,11rem))_auto]">
        <SearchInput placeholder="Search title, URL or primary keyword" aria-label="Search pages" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
        <Select aria-label="Page type" value={type} onChange={(e) => filter(setType)(e.target.value)}>
          <option value="">All page types</option>
          {PAGE_TYPES.map((t) => (
            <option key={t} value={t}>
              {PAGE_TYPE_LABELS[t]}
            </option>
          ))}
        </Select>
        <Select aria-label="Page status" value={status} onChange={(e) => filter(setStatus)(e.target.value)}>
          <option value="open">Not archived</option>
          <option value="all">All statuses</option>
          {PAGE_STATUSES.map((s) => (
            <option key={s} value={s}>
              {PAGE_STATUS_LABELS[s]}
            </option>
          ))}
        </Select>
        <Select aria-label="Sort pages" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
          {PAGE_SORTS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </Select>
        {canEdit && (
          <Button onClick={() => setAdding(true)}>
            <Plus aria-hidden />
            Add page
          </Button>
        )}
      </div>
      {pages.isPending ? (
        <ListSkeleton label="Loading pages" />
      ) : pages.isError ? (
        <ErrorState error={pages.error} title="Couldn't load pages" onRetry={() => void pages.refetch()} />
      ) : pages.data.content.length === 0 ? (
        <EmptyState
          icon={FileText}
          title={filtered ? 'No pages match these filters' : 'No website pages yet'}
          description={filtered ? 'Try a different search or clear some filters.' : 'Add the pages you want to rank, then the keywords each one targets.'}
        />
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Page</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Owner</TableHead>
                <TableHead className="text-right">Keywords</TableHead>
                <TableHead className="text-right">Top 10</TableHead>
                <TableHead className="text-right">Avg. position</TableHead>
                <TableHead>Movement</TableHead>
                <TableHead className="text-right">Not ranked</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody className={cn(pages.isPlaceholderData && 'opacity-60')}>
              {pages.data.content.map((p) => (
                <TableRow
                  key={p.id}
                  data-clickable="true"
                  tabIndex={0}
                  aria-label={`Open ${p.title}`}
                  onClick={() => open(p.id)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') open(p.id);
                  }}
                >
                  <TableCell className="max-w-sm">
                    <p className="truncate font-medium">{p.title}</p>
                    <p className="truncate font-mono text-xs text-muted-foreground">{p.url}</p>
                  </TableCell>
                  <TableCell className="text-sm">{PAGE_TYPE_LABELS[p.pageType]}</TableCell>
                  <TableCell className="max-w-44">{p.owner ? <UserCell name={p.owner.fullName} /> : <span className="text-sm text-muted-foreground">No owner</span>}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(p.stats.totalKeywords)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(p.stats.top10)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatDecimal(p.stats.averagePosition)}</TableCell>
                  <TableCell>
                    <span className="flex gap-3 text-xs font-medium tabular-nums">
                      <span className="inline-flex items-center gap-0.5 text-status-success" title="Improved since last month">
                        <ArrowUp className="size-3.5" aria-hidden />
                        {p.stats.improved}
                        <span className="sr-only"> improved</span>
                      </span>
                      <span className="inline-flex items-center gap-0.5 text-status-danger" title="Declined since last month">
                        <ArrowDown className="size-3.5" aria-hidden />
                        {p.stats.declined}
                        <span className="sr-only"> declined</span>
                      </span>
                    </span>
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(p.stats.notRanked)}</TableCell>
                  <TableCell>
                    <PageStatusBadge status={p.status} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Pagination {...pages.data} onPageChange={setPage} />
        </>
      )}
      <PageFormDialog open={adding} onOpenChange={setAdding} onSaved={(saved) => void navigate(`/digital-marketing/seo/pages/${saved.id}`)} />
    </>
  );
}

function KeywordsTab({ filters, canEdit }: { filters: MarketingFilters; canEdit: boolean }) {
  const pageOptions = useSeoPageOptions();
  const [search, setSearch] = useState('');
  const [pageId, setPageId] = useState('');
  const [device, setDevice] = useState('');
  const [status, setStatus] = useState('open');
  const [sort, setSort] = useState(KEYWORD_SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<KeywordItem | null>(null);
  const [adding, setAdding] = useState(false);
  const [historyOf, setHistoryOf] = useIdParam('keyword');
  const debounced = useDebouncedValue(search.trim());

  const keywords = useSeoKeywords({
    search: debounced,
    pageId: pageId ? Number(pageId) : undefined,
    device: device ? (device as Device) : undefined,
    status: status === 'open' ? OPEN_KEYWORD_STATUSES : status === 'all' ? undefined : [status as KeywordStatus],
    ownerId: filters.ownerId ?? undefined,
    month: filters.month,
    year: filters.year,
    sort,
    page,
    size: 25,
  });

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  const filtered = Boolean(search || pageId || device || status !== 'open' || filters.ownerId !== null);

  return (
    <>
      <div className="grid gap-3 border-b p-4 sm:grid-cols-2 lg:grid-cols-[1fr_repeat(4,minmax(0,10rem))_auto]">
        <SearchInput placeholder="Search keywords" aria-label="Search keywords" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
        <Select aria-label="Page" value={pageId} onChange={(e) => filter(setPageId)(e.target.value)}>
          <option value="">All pages</option>
          {pageOptions.data?.map((p) => (
            <option key={p.id} value={p.id}>
              {p.title}
            </option>
          ))}
        </Select>
        <Select aria-label="Device" value={device} onChange={(e) => filter(setDevice)(e.target.value)}>
          <option value="">All devices</option>
          {DEVICES.map((d) => (
            <option key={d} value={d}>
              {DEVICE_LABELS[d]}
            </option>
          ))}
        </Select>
        <Select aria-label="Keyword status" value={status} onChange={(e) => filter(setStatus)(e.target.value)}>
          <option value="open">Not archived</option>
          <option value="all">All statuses</option>
          {KEYWORD_STATUSES.map((s) => (
            <option key={s} value={s}>
              {KEYWORD_STATUS_LABELS[s]}
            </option>
          ))}
        </Select>
        <Select aria-label="Sort keywords" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
          {KEYWORD_SORTS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </Select>
        {canEdit && (
          <Button onClick={() => setAdding(true)}>
            <KeyRound aria-hidden />
            Add keyword
          </Button>
        )}
      </div>
      {keywords.isPending ? (
        <ListSkeleton label="Loading keywords" />
      ) : keywords.isError ? (
        <ErrorState error={keywords.error} title="Couldn't load keywords" onRetry={() => void keywords.refetch()} />
      ) : keywords.data.content.length === 0 ? (
        <EmptyState
          icon={KeyRound}
          title={filtered ? 'No keywords match these filters' : 'No keywords yet'}
          description={filtered ? 'Try a different search or clear some filters.' : 'Add the keywords each page should rank for.'}
        />
      ) : (
        <>
          <KeywordTable keywords={keywords.data.content} month={filters.month} year={filters.year} onEdit={canEdit ? setEditing : undefined} onHistory={setHistoryOf} dimmed={keywords.isPlaceholderData} />
          <Pagination {...keywords.data} onPageChange={setPage} />
        </>
      )}
      <KeywordFormDialog open={editing !== null} onOpenChange={(o) => !o && setEditing(null)} keyword={editing ?? undefined} />
      <KeywordFormDialog open={adding} onOpenChange={setAdding} />
      <KeywordHistorySheet keywordId={historyOf} onOpenChange={(open) => !open && setHistoryOf(null)} month={filters.month} year={filters.year} canEdit={canEdit} />
    </>
  );
}
