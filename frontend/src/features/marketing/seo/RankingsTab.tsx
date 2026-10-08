import { ChartLine, Download, History, Monitor, Smartphone } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { toast } from 'sonner';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { errorMessage } from '@/lib/api/errors';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { cn } from '@/lib/utils';

import type { RankingMovement } from '../api';
import type { MarketingFilters } from '../filter-memory';
import { formatCount } from '../marketing-format';
import { exportRankings, useRankings, useSeoPageOptions, type KeywordItem, type KeywordStatus, type RankingQuery, type StandingFilter } from './api';
import { KeywordHistorySheet } from './KeywordHistorySheet';
import { PreviousPosition, StandingBadge, StandingChange } from './SeoBadges';
import { MOVEMENT_LABELS, POSITION_BANDS, SEARCH_ENGINE_LABELS, STANDING_FILTER_LABELS } from './seo-meta';

const TRACKED: KeywordStatus[] = ['ACTIVE', 'PAUSED'];

/** Brief section 27: best ranking, worst ranking, biggest improvement, biggest decline. */
const SORTS = [
  { value: 'best', label: 'Best ranking' },
  { value: 'worst', label: 'Worst ranking' },
  { value: 'improvement', label: 'Biggest improvement' },
  { value: 'decline', label: 'Biggest decline' },
  { value: 'volume,desc', label: 'Search volume' },
  { value: 'keyword,asc', label: 'Keyword' },
  { value: 'updated,desc', label: 'Last updated' },
];

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

/** The SEO ranking table: every tracked keyword with its position in the selected month. */
export function RankingsTab({ filters, canEdit }: { filters: MarketingFilters; canEdit: boolean }) {
  const pageOptions = useSeoPageOptions();
  const [search, setSearch] = useState('');
  const [pageId, setPageId] = useState('');
  const [standing, setStanding] = useState('');
  const [band, setBand] = useState('');
  const [movement, setMovement] = useState('');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const [historyOf, setHistoryOf] = useState<number | null>(null);
  const [exporting, setExporting] = useState(false);
  const debounced = useDebouncedValue(search.trim());
  const range = POSITION_BANDS.find((b) => b.value === band);

  const query: Omit<RankingQuery, 'page' | 'size'> = {
    search: debounced,
    pageId: pageId ? Number(pageId) : undefined,
    standing: standing ? (standing as StandingFilter) : undefined,
    minPosition: range?.min,
    maxPosition: range?.max,
    movement: movement ? (movement as RankingMovement) : undefined,
    status: TRACKED,
    ownerId: filters.ownerId ?? undefined,
    month: filters.month,
    year: filters.year,
    sort,
  };
  const rankings = useRankings({ ...query, page, size: 25 });

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  async function onExport() {
    setExporting(true);
    try {
      await exportRankings(query);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  }

  const filtered = Boolean(search || pageId || standing || band || movement || filters.ownerId !== null);

  return (
    <>
      <div className="grid gap-3 border-b p-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-[1fr_repeat(5,minmax(0,9.5rem))_auto]">
        <SearchInput placeholder="Search keywords" aria-label="Search keywords" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
        <Select aria-label="Page" value={pageId} onChange={(e) => filter(setPageId)(e.target.value)}>
          <option value="">All pages</option>
          {pageOptions.data?.map((p) => (
            <option key={p.id} value={p.id}>
              {p.title}
            </option>
          ))}
        </Select>
        <Select aria-label="Ranking status" value={standing} onChange={(e) => filter(setStanding)(e.target.value)}>
          <option value="">All statuses</option>
          {Object.entries(STANDING_FILTER_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
        <Select aria-label="Position" value={band} onChange={(e) => filter(setBand)(e.target.value)}>
          <option value="">Any position</option>
          {POSITION_BANDS.map((b) => (
            <option key={b.value} value={b.value}>
              {b.label}
            </option>
          ))}
        </Select>
        <Select aria-label="Movement" value={movement} onChange={(e) => filter(setMovement)(e.target.value)}>
          <option value="">Any movement</option>
          {Object.entries(MOVEMENT_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
        <Select aria-label="Sort rankings" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
          {SORTS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </Select>
        <Button variant="outline" disabled={exporting || !rankings.data?.totalElements} onClick={() => void onExport()}>
          <Download aria-hidden />
          Export CSV
        </Button>
      </div>
      {rankings.isPending ? (
        <div className="space-y-3 p-4" role="status" aria-label="Loading rankings">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-12" />
          ))}
        </div>
      ) : rankings.isError ? (
        <ErrorState error={rankings.error} title="Couldn't load rankings" onRetry={() => void rankings.refetch()} />
      ) : rankings.data.content.length === 0 ? (
        <EmptyState
          icon={ChartLine}
          title={filtered ? 'No keywords match these filters' : 'No keywords to rank yet'}
          description={filtered ? 'Try a different search or clear some filters.' : 'Add pages and keywords, then record their monthly positions.'}
        />
      ) : (
        <>
          <RankingTable rows={rankings.data.content} month={filters.month} year={filters.year} onHistory={setHistoryOf} dimmed={rankings.isPlaceholderData} />
          <Pagination {...rankings.data} onPageChange={setPage} />
        </>
      )}
      <KeywordHistorySheet keywordId={historyOf} onOpenChange={(open) => !open && setHistoryOf(null)} month={filters.month} year={filters.year} canEdit={canEdit} />
    </>
  );
}

function RankingTable({ rows, month, year, onHistory, dimmed }: { rows: KeywordItem[]; month: number; year: number; onHistory: (id: number) => void; dimmed: boolean }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Keyword</TableHead>
          <TableHead>Page</TableHead>
          <TableHead>Position</TableHead>
          <TableHead>Previous</TableHead>
          <TableHead>Change</TableHead>
          <TableHead className="text-right">Search volume</TableHead>
          <TableHead>Owner</TableHead>
          <TableHead>Last updated</TableHead>
          <TableHead>
            <span className="sr-only">History</span>
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody className={cn(dimmed && 'opacity-60')}>
        {rows.map((row) => {
          const DeviceIcon = row.device === 'MOBILE' ? Smartphone : Monitor;
          const volume = row.entry?.searchVolume ?? row.searchVolume;
          return (
            <TableRow key={row.id}>
              <TableCell className="max-w-xs">
                <p className="truncate font-medium">{row.keyword}</p>
                <p className="flex items-center gap-1 text-xs text-muted-foreground">
                  <DeviceIcon className="size-3" aria-label={row.device === 'MOBILE' ? 'Mobile' : 'Desktop'} />
                  {SEARCH_ENGINE_LABELS[row.searchEngine]} · {row.location}
                </p>
              </TableCell>
              <TableCell className="max-w-56">
                <Link to={`/digital-marketing/seo/pages/${row.page.id}`} className="block truncate text-sm hover:underline">
                  {row.page.title}
                </Link>
                <p className="truncate font-mono text-xs text-muted-foreground">{row.page.url}</p>
              </TableCell>
              <TableCell>
                <StandingBadge standing={row.ranking} month={month} year={year} />
              </TableCell>
              <TableCell className="text-sm">
                <PreviousPosition standing={row.ranking} />
              </TableCell>
              <TableCell>
                <StandingChange standing={row.ranking} />
              </TableCell>
              <TableCell className="text-right text-sm tabular-nums">{formatCount(volume)}</TableCell>
              <TableCell className="max-w-44">{row.owner ? <UserCell name={row.owner.fullName} /> : <span className="text-sm text-muted-foreground">No owner</span>}</TableCell>
              <TableCell className="text-sm whitespace-nowrap text-muted-foreground">{row.entry?.updatedAt ? dayFormat.format(new Date(row.entry.updatedAt)) : '—'}</TableCell>
              <TableCell>
                <Button variant="ghost" size="icon" aria-label={`Ranking history for ${row.keyword}`} onClick={() => onHistory(row.id)}>
                  <History aria-hidden />
                </Button>
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
