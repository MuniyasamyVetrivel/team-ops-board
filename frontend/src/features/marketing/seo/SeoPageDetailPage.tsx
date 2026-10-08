import { ArrowDownRight, ArrowLeft, ArrowUpRight, CircleOff, ExternalLink, Gauge, KeyRound, Pencil, Plus, Trophy } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { Pagination } from '@/components/common/Pagination';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';

import { MarketingFilterBar } from '../components/MarketingFilterBar';
import { MarketingKpiCard } from '../components/MarketingKpiCard';
import { MONTH_NAMES } from '../marketing-format';
import { useMarketingFilters } from '../use-marketing-filters';
import { useSeoKeywords, useSeoPage, type KeywordItem, type KeywordStatus } from './api';
import { KeywordFormDialog } from './KeywordFormDialog';
import { KeywordTable } from './KeywordTable';
import { PageFormDialog } from './PageFormDialog';
import { PageStatusBadge } from './SeoBadges';
import { PAGE_TYPE_LABELS } from './seo-meta';

const OPEN_KEYWORD_STATUSES: KeywordStatus[] = ['ACTIVE', 'PAUSED'];

/** One website page: its SEO figures for the selected month and its keywords. */
export default function SeoPageDetailPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const { filters } = useMarketingFilters();
  const page = useSeoPage(id, filters ? { month: filters.month, year: filters.year } : null);
  const [editing, setEditing] = useState(false);

  if (page.isError) {
    return (
      <div className="space-y-4">
        <BackLink />
        <Card>
          <ErrorState error={page.error} title="Couldn't open this page" onRetry={() => void page.refetch()} />
        </Card>
      </div>
    );
  }
  if (!page.data || !filters) {
    return (
      <div className="space-y-6" role="status" aria-label="Loading page">
        <Skeleton className="h-10 w-1/2" />
        <div className="grid gap-3 sm:grid-cols-3 xl:grid-cols-6">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-80 rounded-xl" />
      </div>
    );
  }

  const p = page.data;
  const { stats, previousStats } = p;
  const previousLabel = MONTH_NAMES[p.previousPeriod.month - 1] ?? 'last month';
  const loading = page.isPlaceholderData;

  return (
    <div className="space-y-6">
      <div className="space-y-3">
        <BackLink />
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
              <PageStatusBadge status={p.status} />
              <span>{PAGE_TYPE_LABELS[p.pageType]}</span>
              <span aria-hidden>·</span>
              <span>{p.department.name}</span>
              <span aria-hidden>·</span>
              <span>Owner: {p.owner?.fullName ?? 'none'}</span>
            </div>
            <h1 className="mt-1 text-xl font-semibold tracking-tight sm:text-2xl">{p.title}</h1>
            <p className="mt-1 flex items-center gap-1.5 font-mono text-sm text-muted-foreground">
              {p.url}
              {/^https?:\/\//.test(p.url) && (
                <a href={p.url} target="_blank" rel="noreferrer noopener" aria-label="Open the page in a new tab" className="hover:text-foreground">
                  <ExternalLink className="size-3.5" aria-hidden />
                </a>
              )}
            </p>
            {p.primaryKeyword && <p className="mt-1 text-sm text-muted-foreground">Primary keyword: {p.primaryKeyword}</p>}
          </div>
          {p.permissions.canEdit && (
            <Button variant="outline" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit page
            </Button>
          )}
        </div>
      </div>

      <MarketingFilterBar showOwner={false} />

      <section aria-label={`SEO figures for ${p.period.label}`} className="grid gap-3 sm:grid-cols-3 xl:grid-cols-6">
        <MarketingKpiCard label="Total keywords" icon={KeyRound} value={stats.totalKeywords} loading={loading} hint={stats.notRecorded > 0 ? `${stats.notRecorded} without a ranking for ${p.period.label}` : undefined} />
        <MarketingKpiCard label="Top 10 keywords" icon={Trophy} value={stats.top10} previous={previousStats.top10} previousLabel={previousLabel} loading={loading} hint={`${stats.top3} in the top 3`} />
        <MarketingKpiCard label="Average position" icon={Gauge} value={stats.averagePosition} format="decimal" previous={previousStats.averagePosition} previousLabel={previousLabel} better="lower" loading={loading} hint="Ranked keywords only" />
        <MarketingKpiCard label="Improved keywords" icon={ArrowUpRight} value={stats.improved} loading={loading} hint={`Since ${previousLabel}`} />
        <MarketingKpiCard label="Declined keywords" icon={ArrowDownRight} value={stats.declined} loading={loading} hint={`Since ${previousLabel}`} />
        <MarketingKpiCard label="Not ranked keywords" icon={CircleOff} value={stats.notRanked} previous={previousStats.notRanked} previousLabel={previousLabel} better="lower" loading={loading} />
      </section>

      <PageKeywords pageId={p.id} ownerId={p.owner?.id ?? null} month={filters.month} year={filters.year} canEdit={p.permissions.canEdit} canAdd={p.permissions.canEdit && p.status !== 'ARCHIVED'} />

      <PageFormDialog open={editing} onOpenChange={setEditing} page={p} onDeleted={() => void navigate('/digital-marketing/seo', { replace: true })} />
    </div>
  );
}

function BackLink() {
  return (
    <Link to="/digital-marketing/seo" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
      <ArrowLeft className="size-4" aria-hidden />
      SEO Rankings
    </Link>
  );
}

function PageKeywords({ pageId, ownerId, month, year, canEdit, canAdd }: { pageId: number; ownerId: number | null; month: number; year: number; canEdit: boolean; canAdd: boolean }) {
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<KeywordItem | null>(null);
  const keywords = useSeoKeywords({ pageId, status: OPEN_KEYWORD_STATUSES, month, year, sort: 'keyword,asc', page, size: 50 });

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-3.5">
        <div>
          <h2 className="font-semibold">Keywords</h2>
          <p className="text-sm text-muted-foreground">Positions for the selected month; archived keywords are hidden.</p>
        </div>
        {canAdd && (
          <Button size="sm" onClick={() => setAdding(true)}>
            <Plus aria-hidden />
            Add keyword
          </Button>
        )}
      </div>
      {keywords.isPending ? (
        <div className="space-y-3 p-4" role="status" aria-label="Loading keywords">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-12" />
          ))}
        </div>
      ) : keywords.isError ? (
        <ErrorState error={keywords.error} title="Couldn't load keywords" onRetry={() => void keywords.refetch()} />
      ) : keywords.data.content.length === 0 ? (
        <EmptyState icon={KeyRound} title="No keywords on this page" description={canAdd ? 'Add the keywords this page should rank for.' : 'Keywords added by the SEO team appear here.'} />
      ) : (
        <>
          <KeywordTable keywords={keywords.data.content} month={month} year={year} showPage={false} onEdit={canEdit ? setEditing : undefined} dimmed={keywords.isPlaceholderData} />
          <Pagination {...keywords.data} onPageChange={setPage} />
        </>
      )}
      <KeywordFormDialog open={adding} onOpenChange={setAdding} page={{ id: pageId, ownerId }} />
      <KeywordFormDialog open={editing !== null} onOpenChange={(o) => !o && setEditing(null)} keyword={editing ?? undefined} />
    </Card>
  );
}
