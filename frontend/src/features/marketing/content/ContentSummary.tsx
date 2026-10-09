import { CalendarClock, CircleCheck, Hourglass, Target, UserPlus } from 'lucide-react';
import { useState } from 'react';

import { ErrorState } from '@/components/common/ErrorState';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';

import { MarketingKpiCard } from '../components/MarketingKpiCard';
import { TargetBar, TargetProgress, TargetStatusBadge } from '../components/TargetProgress';
import type { MarketingFilters } from '../filter-memory';
import { formatCount, formatPercent, MONTH_NAMES } from '../marketing-format';
import { useContentSummary } from './api';
import { CONTENT_TYPE_LABELS } from './content-meta';

type Compare = 'previous' | 'lastYear';

/**
 * Brief section 48: the month's blog target, blogs published, what remains and the leads content brought in (12
 * target, 9 achieved, 3 remaining), with blogs planned for the month, against another month. Targets show only for
 * TARGET_VIEW holders (decided by the server).
 */
export function ContentSummary({ filters }: { filters: MarketingFilters }) {
  const [compare, setCompare] = useState<Compare>('previous');
  const summary = useContentSummary({
    month: filters.month,
    year: filters.year,
    ownerId: filters.ownerId ?? undefined,
    ...(compare === 'lastYear' ? { compareMonth: filters.month, compareYear: filters.year - 1 } : {}),
  });

  if (summary.isError) {
    return (
      <Card>
        <ErrorState error={summary.error} title="Couldn't load the monthly summary" onRetry={() => void summary.refetch()} />
      </Card>
    );
  }
  if (!summary.data) {
    return (
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5" role="status" aria-label="Loading the monthly summary">
        {Array.from({ length: 5 }, (_, i) => (
          <Skeleton key={i} className="h-28 rounded-xl" />
        ))}
      </div>
    );
  }

  const { current, comparison, blogTarget, blogLeadsTarget, targetsVisible } = summary.data;
  const short = MONTH_NAMES[comparison.period.month - 1] ?? comparison.period.label;
  const compareLabel = comparison.period.year === current.period.year ? short : comparison.period.label;
  const loading = summary.isPlaceholderData;
  const target = blogTarget?.target;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-semibold">Blog target · {current.period.label}</h2>
        <Select aria-label="Compare with" className="w-56" value={compare} onChange={(e) => setCompare(e.target.value as Compare)}>
          <option value="previous">Compare with the previous month</option>
          <option value="lastYear">Compare with the same month last year</option>
        </Select>
      </div>
      <section aria-label={`Blog target for ${current.period.label}`} className={cn('grid gap-3 sm:grid-cols-2', targetsVisible ? 'xl:grid-cols-5' : 'xl:grid-cols-3')}>
        {targetsVisible && (
          <MarketingKpiCard label="Blog target" icon={Target} value={blogTarget?.targetValue ?? null} loading={loading} hint={blogTarget ? 'Blogs to publish this month' : `No blog target for ${current.period.label}`}>
            {target && (
              <div className="mt-1">
                <TargetStatusBadge status={target.status} />
              </div>
            )}
          </MarketingKpiCard>
        )}
        <MarketingKpiCard label="Published" icon={CircleCheck} value={current.publishedBlogs} previous={comparison.publishedBlogs} previousLabel={compareLabel} loading={loading}>
          {target && (
            <div className="mt-2 space-y-1">
              <TargetBar label="Blog target achievement" achievementPct={target.achievementPct} status={target.status} />
              <p className="text-xs text-muted-foreground tabular-nums">{formatPercent(target.achievementPct)} of target</p>
            </div>
          )}
        </MarketingKpiCard>
        {targetsVisible && <MarketingKpiCard label="Remaining" icon={Hourglass} value={blogTarget?.remaining ?? null} loading={loading} hint="Target − published" />}
        <MarketingKpiCard label="Leads" icon={UserPlus} value={current.leads} previous={comparison.leads} previousLabel={compareLabel} loading={loading} hint="Leads that name content" />
        <MarketingKpiCard label="Planned" icon={CalendarClock} value={current.plannedBlogs} previous={comparison.plannedBlogs} previousLabel={compareLabel} better={null} loading={loading} hint="Blogs planned for the month" />
      </section>

      <div className={cn('grid gap-6', blogLeadsTarget ? 'lg:grid-cols-3' : 'lg:grid-cols-2')}>
        <Card className={cn(loading && 'opacity-60')}>
          <div className="border-b px-5 py-3.5">
            <h3 className="font-semibold">Published this month</h3>
            <p className="text-sm text-muted-foreground">
              {formatCount(current.publishedAll)} items · {formatCount(current.refreshed)} refreshed
            </p>
          </div>
          {summary.data.publishedByType.length === 0 ? (
            <p className="px-5 py-4 text-sm text-muted-foreground">Nothing published this month yet.</p>
          ) : (
            <ul className="divide-y" aria-label="Published by type">
              {summary.data.publishedByType.map((t) => (
                <li key={t.contentType} className="flex items-center justify-between px-5 py-2.5 text-sm">
                  <span>{CONTENT_TYPE_LABELS[t.contentType]}</span>
                  <span className="font-medium tabular-nums">{formatCount(t.published)}</span>
                </li>
              ))}
            </ul>
          )}
          <p className="border-t px-5 py-2.5 text-xs text-muted-foreground">Only blog posts count towards the blog target.</p>
        </Card>

        <Card className={cn(loading && 'opacity-60')}>
          <div className="border-b px-5 py-3.5">
            <h3 className="font-semibold">Top content by leads</h3>
            <p className="text-sm text-muted-foreground">Leads in {current.period.label}</p>
          </div>
          {summary.data.topContent.length === 0 ? (
            <p className="px-5 py-4 text-sm text-muted-foreground">No leads named content this month.</p>
          ) : (
            <ol className="divide-y" aria-label="Top content by leads">
              {summary.data.topContent.map((c) => (
                <li key={c.id} className="flex items-center justify-between gap-3 px-5 py-2.5 text-sm">
                  <div className="min-w-0">
                    <p className="truncate font-medium" title={c.title}>
                      {c.title}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {CONTENT_TYPE_LABELS[c.contentType]}
                      {c.organicTraffic !== null && ` · ${formatCount(c.organicTraffic)} visits`}
                    </p>
                  </div>
                  <span className="shrink-0 tabular-nums">
                    {formatCount(c.leads)} {c.leads === 1 ? 'lead' : 'leads'}
                  </span>
                </li>
              ))}
            </ol>
          )}
        </Card>

        {blogLeadsTarget && (
          <TargetProgress
            label="Blog Leads target"
            target={blogLeadsTarget.targetValue}
            actual={blogLeadsTarget.actual}
            achievementPct={blogLeadsTarget.achievementPct}
            remaining={blogLeadsTarget.remaining}
            status={blogLeadsTarget.status}
            hint="Every lead with the Blog source"
          />
        )}
      </div>
    </div>
  );
}
