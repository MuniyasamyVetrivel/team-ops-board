import { ChartLine } from 'lucide-react';

import { ErrorState } from '@/components/common/ErrorState';
import { Panel, PanelLink } from '@/components/common/Panel';
import { Skeleton } from '@/components/ui/skeleton';
import { useMarketingDashboard, type MarketingDashboard } from '@/features/marketing/dashboard/api';
import { formatCount, formatDecimal, formatPercent } from '@/features/marketing/marketing-format';

interface Figure {
  label: string;
  value: string;
  hint?: string;
}

/** Brief section 8.9: the figures Rakesh sees on the home dashboard, from the marketing dashboard endpoint. */
function figures(d: MarketingDashboard): Figure[] {
  const rows: Figure[] = [];
  if (d.seo) {
    rows.push(
      { label: 'SEO average ranking', value: formatDecimal(d.seo.current.averagePosition), hint: 'Of the ranked keywords' },
      { label: 'Keywords in Top 10', value: formatCount(d.seo.current.top10) },
      { label: 'Keywords improved', value: formatCount(d.seo.current.improved) },
      { label: 'Keywords declined', value: formatCount(d.seo.current.declined) },
    );
  }
  if (d.targets) {
    const s = d.targets.summary;
    rows.push({
      label: 'Marketing target achievement',
      value: s.total === 0 ? '—' : `${s.achieved} of ${s.total}`,
      hint: s.total === 0 ? 'No targets this month' : `${s.inProgress} in progress · ${s.behind} behind`,
    });
  }
  if (d.leads) {
    const count = (source: string) => d.leads?.bySource.find((s) => s.source === source)?.leads ?? 0;
    rows.push(
      { label: 'Email leads', value: formatCount(count('EMAIL')) },
      { label: 'Paid campaign leads', value: formatCount(count('LINKEDIN') + count('PAID_CAMPAIGN')), hint: 'LinkedIn and other paid sources' },
    );
    if (d.leads.target) rows.push({ label: 'Website leads', value: `${formatCount(d.leads.total)} of ${formatCount(d.leads.target.targetValue)}`, hint: `${formatPercent(d.leads.target.achievementPct)} achieved` });
  }
  if (d.backlinks) rows.push({ label: 'Backlinks gone live', value: formatCount(d.backlinks.current.live), hint: `${formatCount(d.backlinks.current.submitted)} submitted` });
  if (d.content) rows.push({ label: 'Blog/content leads', value: formatCount(d.content.current.leads), hint: `${formatCount(d.content.current.publishedBlogs)} blogs published` });
  return rows;
}

/** The marketing summary on the company dashboard; it reads the current business month. */
export function MarketingSummaryPanel() {
  const dashboard = useMarketingDashboard({ months: 2 });
  const data = dashboard.data;

  return (
    <Panel
      title="Digital Marketing performance"
      icon={ChartLine}
      description={data ? data.period.label : 'This month'}
      action={<PanelLink to="/digital-marketing">Marketing dashboard</PanelLink>}
    >
      {dashboard.isError ? (
        <ErrorState error={dashboard.error} title="Couldn't load the marketing summary" onRetry={() => void dashboard.refetch()} />
      ) : !data ? (
        <div className="grid grid-cols-2 gap-3 p-5 md:grid-cols-5" role="status" aria-label="Loading the marketing summary">
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} className="h-16" />
          ))}
        </div>
      ) : (
        <dl className="grid grid-cols-2 gap-px bg-border md:grid-cols-5" aria-label="Marketing summary">
          {figures(data).map((f) => (
            <div key={f.label} className="bg-card px-4 py-3">
              <dt className="text-xs text-muted-foreground">{f.label}</dt>
              <dd className="mt-0.5 text-lg font-semibold tabular-nums">{f.value}</dd>
              {f.hint && <p className="text-xs text-muted-foreground">{f.hint}</p>}
            </div>
          ))}
        </dl>
      )}
    </Panel>
  );
}
