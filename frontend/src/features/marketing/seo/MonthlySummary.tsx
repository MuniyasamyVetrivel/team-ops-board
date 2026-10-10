import { ArrowDownRight, ArrowUpRight, ChartColumn, Gauge, KeyRound, Trophy } from 'lucide-react';
import { useState } from 'react';

import { Delta } from '@/components/common/Delta';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from '@/lib/utils';

import { MarketingKpiCard } from '../components/MarketingKpiCard';
import type { MarketingFilters } from '../filter-memory';
import { formatCount, MONTH_NAMES } from '../marketing-format';
import { useMonthlyReport, useSeoPageOptions, type SeoStats } from './api';

interface Band {
  label: string;
  value: (stats: SeoStats) => number;
  /** Whether more keywords in this band is good news, bad news, or neither. */
  better: 'higher' | 'lower' | null;
  hint?: string;
}

/** Brief section 29. Top 10 includes the top 3. */
const BANDS: Band[] = [
  { label: 'Top 3', value: (s) => s.top3, better: 'higher' },
  { label: 'Top 10', value: (s) => s.top10, better: 'higher', hint: 'includes the top 3' },
  { label: '11–20', value: (s) => s.positions11to20, better: null },
  { label: '21–50', value: (s) => s.positions21to50, better: null },
  { label: '51–100', value: (s) => s.positions51to100, better: null },
  { label: 'Not ranked', value: (s) => s.notRanked, better: 'lower', hint: 'includes keywords with no data' },
];

/** The monthly SEO summary: keywords per position band this month against the month before. */
export function MonthlySummary({ filters }: { filters: MarketingFilters }) {
  const pageOptions = useSeoPageOptions();
  const [pageId, setPageId] = useState('');
  const report = useMonthlyReport({ pageId: pageId ? Number(pageId) : undefined, ownerId: filters.ownerId ?? undefined, month: filters.month, year: filters.year });

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <p className="text-sm text-muted-foreground">Keywords that are not archived, counted by their position in each month.</p>
        <Select aria-label="Page" className="w-64" value={pageId} onChange={(e) => setPageId(e.target.value)}>
          <option value="">All pages</option>
          {pageOptions.data?.map((p) => (
            <option key={p.id} value={p.id}>
              {p.title}
            </option>
          ))}
        </Select>
      </div>
      {report.isPending ? (
        <div className="space-y-3 p-4" role="status" aria-label="Loading the monthly summary">
          <div className="grid gap-4 sm:gap-6 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-5">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-24 rounded-xl" />
            ))}
          </div>
          <Skeleton className="h-64" />
        </div>
      ) : report.isError ? (
        <ErrorState error={report.error} title="Couldn't load the monthly summary" onRetry={() => void report.refetch()} />
      ) : report.data.stats.totalKeywords === 0 ? (
        <EmptyState icon={ChartColumn} title="No keywords to summarise" description="Add keywords and record their monthly positions to see the summary." />
      ) : (
        <SummaryBody stats={report.data.stats} previous={report.data.previousStats} label={report.data.period.label} previousLabel={MONTH_NAMES[report.data.previousPeriod.month - 1] ?? 'last month'} previousFull={report.data.previousPeriod.label} dimmed={report.isPlaceholderData} />
      )}
    </>
  );
}

function SummaryBody({ stats, previous, label, previousLabel, previousFull, dimmed }: { stats: SeoStats; previous: SeoStats; label: string; previousLabel: string; previousFull: string; dimmed: boolean }) {
  const widest = Math.max(1, ...BANDS.map((band) => Math.max(band.value(stats), band.value(previous))));
  return (
    <div className={cn('space-y-6 p-6', dimmed && 'opacity-60')}>
      <section aria-label={`SEO summary for ${label}`} className="grid gap-4 sm:gap-6 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-5">
        <MarketingKpiCard label="Total keywords" icon={KeyRound} value={stats.totalKeywords} hint={stats.notRecorded > 0 ? `${stats.notRecorded} without a ranking for ${label}` : 'All recorded this month'} />
        <MarketingKpiCard label="Top 10 keywords" icon={Trophy} value={stats.top10} previous={previous.top10} previousLabel={previousLabel} />
        <MarketingKpiCard label="Average position" icon={Gauge} value={stats.averagePosition} format="decimal" previous={previous.averagePosition} previousLabel={previousLabel} better="lower" hint="Ranked keywords only" />
        <MarketingKpiCard label="Improved" icon={ArrowUpRight} value={stats.improved} hint={`Since ${previousLabel}`} />
        <MarketingKpiCard label="Declined" icon={ArrowDownRight} value={stats.declined} hint={`Since ${previousLabel}`} />
      </section>
      <Table aria-label={`Keywords by position, ${label} against ${previousFull}`}>
        <TableHeader>
          <TableRow>
            <TableHead>Position</TableHead>
            <TableHead className="w-2/5">{label}</TableHead>
            <TableHead numeric>{previousFull}</TableHead>
            <TableHead numeric>Change</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {BANDS.map((band) => {
            const current = band.value(stats);
            const before = band.value(previous);
            return (
              <TableRow key={band.label}>
                <TableCell>
                  <p className="font-medium">{band.label}</p>
                  {band.hint && <p className="text-xs text-muted-foreground">{band.hint}</p>}
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-3">
                    <span className="w-8 text-right font-semibold tabular-nums">{formatCount(current)}</span>
                    <div className="h-2 flex-1 rounded-full bg-muted" aria-hidden>
                      <div className={cn('h-2 rounded-full', band.label === 'Not ranked' ? 'bg-status-danger/70' : 'bg-primary/70')} style={{ width: `${(current / widest) * 100}%` }} />
                    </div>
                  </div>
                </TableCell>
                <TableCell numeric className="text-muted-foreground">{formatCount(before)}</TableCell>
                <TableCell numeric>
                  <BandChange current={current} previous={before} better={band.better} />
                </TableCell>
              </TableRow>
            );
          })}
          <TableRow>
            <TableCell className="font-medium">Total keywords</TableCell>
            <TableCell>
              <span className="inline-block w-8 text-right font-semibold tabular-nums">{formatCount(stats.totalKeywords)}</span>
            </TableCell>
            <TableCell numeric className="text-muted-foreground">{formatCount(previous.totalKeywords)}</TableCell>
            <TableCell />
          </TableRow>
        </TableBody>
      </Table>
    </div>
  );
}

/** "↑ 3" / "↓ 2" / "— 0" on one line; green or red only when the direction is clearly good or bad. */
function BandChange({ current, previous, better }: { current: number; previous: number; better: Band['better'] }) {
  return <Delta value={current - previous} better={better} />;
}
