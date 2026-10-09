import { Link2, Mail, MousePointerClick, Search, SquarePen, UserPlus } from 'lucide-react';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { Link } from 'react-router';

import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';

import { MarketingFilterBar } from '../components/MarketingFilterBar';
import { MarketingKpiCard } from '../components/MarketingKpiCard';
import { MarketingReferencePanels } from '../components/MarketingReferencePanels';
import { TargetBar } from '../components/TargetProgress';
import { formatCount, formatInr, MONTH_NAMES, periodLabel } from '../marketing-format';
import { useMarketingFilters } from '../use-marketing-filters';
import { useMarketingDashboard, type MarketingDashboard } from './api';
import { ActivitiesPanel, BacklinkPanel, ContentPanel, EmailPanel, LeadSourcePanel, LinkedInPanel, SeoOverviewPanel, TargetAchievementPanel } from './DashboardSections';
import { DashboardTrendPanel } from './DashboardTrendPanel';

/**
 * The Digital Marketing executive dashboard (brief sections 22, 49 and 61): one request for the month, the month
 * before and the trend. Each block appears only when the server includes it (the viewer's module permissions).
 */
export default function MarketingDashboardPage() {
  const { context, filters } = useMarketingFilters();
  const [months, setMonths] = useState(6);
  const dashboard = useMarketingDashboard(
    { month: filters?.month, year: filters?.year, ownerId: filters?.ownerId ?? undefined, months },
    filters !== null,
  );

  return (
    <div className="space-y-6">
      <PageHeader
        title="Digital Marketing"
        description={filters ? <span aria-live="polite">{periodLabel(filters.month, filters.year)}</span> : <Skeleton className="h-4 w-28" />}
      />
      <MarketingFilterBar />

      {context.isError || dashboard.isError ? (
        <Card>
          <ErrorState
            error={context.error ?? dashboard.error}
            title="Couldn't load the marketing dashboard"
            onRetry={() => void (context.isError ? context.refetch() : dashboard.refetch())}
          />
        </Card>
      ) : !dashboard.data ? (
        <DashboardSkeleton />
      ) : (
        <Body data={dashboard.data} loading={dashboard.isPlaceholderData} months={months} onMonthsChange={setMonths} />
      )}

      <MarketingReferencePanels threshold={context.data?.behindThresholdPct ?? null} />
    </div>
  );
}

function Body({ data, loading, months, onMonthsChange }: { data: MarketingDashboard; loading: boolean; months: number; onMonthsChange: (months: number) => void }) {
  const short = MONTH_NAMES[data.comparisonPeriod.month - 1] ?? data.comparisonPeriod.label;
  const compareLabel = data.comparisonPeriod.year === data.period.year ? short : data.comparisonPeriod.label;

  return (
    <div className="space-y-6" aria-busy={loading}>
      <KpiRow data={data} compareLabel={compareLabel} loading={loading} />

      <div className="grid gap-6 xl:grid-cols-2">
        {data.seo && <SeoOverviewPanel seo={data.seo} compareLabel={compareLabel} />}
        {data.targets && <TargetAchievementPanel targets={data.targets} />}
        {data.leads && <LeadSourcePanel leads={data.leads} compareLabel={compareLabel} />}
        {data.email && <EmailPanel email={data.email} compareLabel={compareLabel} />}
        {data.linkedin && <LinkedInPanel linkedin={data.linkedin} compareLabel={compareLabel} />}
        {data.backlinks && <BacklinkPanel backlinks={data.backlinks} compareLabel={compareLabel} />}
        {data.content && <ContentPanel content={data.content} compareLabel={compareLabel} />}
        <ActivitiesPanel activities={data.activities} />
      </div>

      <DashboardTrendPanel trend={data.trend} months={months} onMonthsChange={onMonthsChange} />
    </div>
  );
}

/** Brief section 61 KPI row: one card per module the viewer may see, each linking to its page. */
function KpiRow({ data, compareLabel, loading }: { data: MarketingDashboard; compareLabel: string; loading: boolean }) {
  const cards: ReactNode[] = [];
  if (data.seo) {
    const s = data.seo;
    cards.push(
      <KpiLink key="seo" to="/digital-marketing/seo">
        <MarketingKpiCard label="SEO · Top 10 keywords" icon={Search} value={s.current.top10} previous={s.comparison.top10} previousLabel={compareLabel} loading={loading} hint={`${formatCount(s.current.improved)} improved · ${formatCount(s.current.declined)} declined`} />
      </KpiLink>,
    );
  }
  if (data.leads) {
    const l = data.leads;
    cards.push(
      <KpiLink key="leads" to="/digital-marketing/leads">
        <MarketingKpiCard label="Leads" icon={UserPlus} value={l.total} previous={l.comparisonTotal} previousLabel={compareLabel} loading={loading} hint={l.target ? `Target ${formatCount(l.target.targetValue)} · ${formatCount(l.target.remaining)} to go` : undefined}>
          {l.target && <TargetBar label="Leads against target" achievementPct={l.target.achievementPct} status={l.target.status} className="mt-2" />}
        </MarketingKpiCard>
      </KpiLink>,
    );
  }
  if (data.email) {
    const e = data.email;
    cards.push(
      <KpiLink key="email" to="/digital-marketing/email-campaigns">
        <MarketingKpiCard label="Email · Open rate" icon={Mail} value={e.current.rates.openRate} format="percent" previous={e.comparison.rates.openRate} previousLabel={compareLabel} loading={loading} hint={`${formatCount(e.current.counts.emailsSent)} sent · ${formatCount(e.current.counts.leads)} leads`} />
      </KpiLink>,
    );
  }
  if (data.linkedin) {
    const p = data.linkedin.current;
    cards.push(
      <KpiLink key="linkedin" to="/digital-marketing/paid-campaigns">
        <MarketingKpiCard label="LinkedIn · Leads" icon={MousePointerClick} value={p.results.leads} previous={data.linkedin.comparison.results.leads} previousLabel={compareLabel} loading={loading} hint={`${formatInr(p.results.spend)} spent · CPL ${formatInr(p.rates.costPerLead)}`} />
      </KpiLink>,
    );
  }
  if (data.backlinks) {
    const b = data.backlinks;
    cards.push(
      <KpiLink key="backlinks" to="/digital-marketing/backlinks">
        <MarketingKpiCard label="Backlinks · Live" icon={Link2} value={b.current.live} previous={b.comparison.live} previousLabel={compareLabel} loading={loading} hint={b.target ? `${formatCount(b.current.submitted)} submitted · ${formatCount(b.target.remaining)} to submit` : `${formatCount(b.current.submitted)} submitted`}>
          {b.target && <TargetBar label="Live backlinks against target" achievementPct={b.target.target.achievementPct} status={b.target.target.status} className="mt-2" />}
        </MarketingKpiCard>
      </KpiLink>,
    );
  }
  if (data.content) {
    const c = data.content;
    cards.push(
      <KpiLink key="content" to="/digital-marketing/content">
        <MarketingKpiCard label="Content · Blogs published" icon={SquarePen} value={c.current.publishedBlogs} previous={c.comparison.publishedBlogs} previousLabel={compareLabel} loading={loading} hint={c.blogTarget ? `${formatCount(c.blogTarget.remaining)} remaining · ${formatCount(c.current.leads)} leads` : `${formatCount(c.current.leads)} leads`}>
          {c.blogTarget && <TargetBar label="Blogs against target" achievementPct={c.blogTarget.target.achievementPct} status={c.blogTarget.target.status} className="mt-2" />}
        </MarketingKpiCard>
      </KpiLink>,
    );
  }
  if (cards.length === 0) return null;
  return (
    <section aria-label={`Marketing performance for ${data.period.label}`} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6">
      {cards}
    </section>
  );
}

function KpiLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="block rounded-xl transition-shadow hover:shadow-md focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none">
      {children}
    </Link>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6" role="status" aria-label="Loading the marketing dashboard">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6">
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} className="h-28 rounded-xl" />
        ))}
      </div>
      <div className="grid gap-6 xl:grid-cols-2">
        {Array.from({ length: 4 }, (_, i) => (
          <Skeleton key={i} className="h-72 rounded-xl" />
        ))}
      </div>
    </div>
  );
}
