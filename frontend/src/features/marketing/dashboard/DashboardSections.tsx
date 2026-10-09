import { ArrowDownRight, ArrowUpRight, CalendarCheck, CircleCheck, Hourglass, Link2, Mail, Minus, MousePointerClick, Search, SquarePen, Target, UserPlus } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

import { Panel } from '@/components/common/Panel';
import { Badge } from '@/components/ui/badge';
import { DueBadge } from '@/features/tasks/TaskBadges';
import { cn } from '@/lib/utils';

import { OccurrenceStatusBadge } from '../activities/ActivityBadges';
import { ChangeText } from '../components/MarketingKpiCard';
import { TargetBar, TargetProgress, TargetStatusBadge } from '../components/TargetProgress';
import { formatCount, formatDecimal, formatInr, formatPercent } from '../marketing-format';
import { RANKING_STATUS_LABELS } from '../marketing-meta';
import { BudgetBar } from '../paid/PaidBadges';
import { LEAD_SOURCE_LABELS } from '../targets/target-meta';
import type { ActivitySection, LeadSection, LinkedInSection, MarketingDashboard, SeoSection } from './api';

type Kind = 'count' | 'percent' | 'currency' | 'decimal';
type Better = 'higher' | 'lower' | null;

const show = (value: number | null | undefined, kind: Kind) =>
  kind === 'currency' ? formatInr(value) : kind === 'percent' ? formatPercent(value) : kind === 'decimal' ? formatDecimal(value) : formatCount(value);

function PanelLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="shrink-0 text-xs font-medium text-primary hover:underline">
      {children}
    </Link>
  );
}

interface MetricRowProps {
  label: string;
  value: number | null | undefined;
  before?: number | null;
  kind?: Kind;
  better?: Better;
  hint?: string;
  compareLabel: string;
}

/**
 * A figure with its change against the comparison month: counts and amounts by percentage, rates by percentage
 * points. Direction is always in words and an arrow, never colour alone.
 */
function MetricRow({ label, value, before, kind = 'count', better = 'higher', hint, compareLabel }: MetricRowProps) {
  return (
    <div className="flex items-start justify-between gap-3 px-5 py-2.5">
      <dt className="min-w-0 text-sm">
        {label}
        {hint && <span className="block text-xs text-muted-foreground">{hint}</span>}
      </dt>
      <dd className="text-right">
        <span className="block text-sm font-semibold tabular-nums">{show(value, kind)}</span>
        {before !== undefined &&
          (kind === 'percent' ? (
            <PointsChange now={value ?? null} before={before} better={better} compareLabel={compareLabel} />
          ) : (
            <ChangeText current={value} previous={before} previousLabel={compareLabel} better={better} className="justify-end" />
          ))}
      </dd>
    </div>
  );
}

function PointsChange({ now, before, better, compareLabel }: { now: number | null; before: number | null; better: Better; compareLabel: string }) {
  if (now === null || before === null) return <span className="block text-xs text-muted-foreground">No comparison with {compareLabel}</span>;
  const diff = Math.round((now - before) * 100) / 100;
  if (diff === 0) {
    return (
      <span className="flex items-center justify-end gap-1 text-xs text-muted-foreground">
        <Minus className="size-3.5" aria-hidden />
        No change vs {compareLabel}
      </span>
    );
  }
  const good = better === null ? null : (diff > 0) === (better === 'higher');
  const Icon = diff > 0 ? ArrowUpRight : ArrowDownRight;
  return (
    <span className={cn('flex items-center justify-end gap-1 text-xs font-medium tabular-nums', good === null ? 'text-muted-foreground' : good ? 'text-status-success' : 'text-status-danger')}>
      <Icon className="size-3.5" aria-hidden />
      {diff > 0 ? 'Up' : 'Down'} {Math.abs(diff).toFixed(2)} pts vs {compareLabel}
    </span>
  );
}

/** Brief section 22 SEO block: how the month's keywords rank, and how they moved against the month before. */
export function SeoOverviewPanel({ seo, compareLabel }: { seo: SeoSection; compareLabel: string }) {
  const { current, comparison } = seo;
  const total = Math.max(current.totalKeywords, 1);
  const bands = [
    { key: 'TOP_10' as const, value: current.top10, bar: 'bg-status-success' },
    { key: 'RANKING' as const, value: current.ranking, bar: 'bg-status-warning' },
    { key: 'NOT_RANKED' as const, value: current.notRanked, bar: 'bg-status-danger' },
  ];
  return (
    <Panel title="SEO ranking overview" icon={Search} description={`${formatCount(seo.totalPages)} pages · ${formatCount(current.totalKeywords)} keywords`} action={<PanelLink to="/digital-marketing/seo">Rankings</PanelLink>}>
      <div className="space-y-2 px-5 pt-4">
        <div className="flex h-3 overflow-hidden rounded-full bg-muted" role="img" aria-label={bands.map((b) => `${RANKING_STATUS_LABELS[b.key]} ${b.value}`).join(', ')}>
          {bands.map((b) => (
            <div key={b.key} className={b.bar} style={{ width: `${(b.value / total) * 100}%` }} />
          ))}
        </div>
        <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground" aria-label="Ranking bands">
          {bands.map((b) => (
            <li key={b.key} className="flex items-center gap-1.5">
              <span className={cn('size-2.5 rounded-sm', b.bar)} aria-hidden />
              {RANKING_STATUS_LABELS[b.key]} <span className="font-medium text-foreground tabular-nums">{formatCount(b.value)}</span>
            </li>
          ))}
        </ul>
      </div>
      <dl className="mt-2 divide-y">
        <MetricRow label="Top 3 keywords" value={current.top3} before={comparison.top3} compareLabel={compareLabel} />
        <MetricRow label="Top 10 keywords" value={current.top10} before={comparison.top10} compareLabel={compareLabel} />
        <MetricRow label="Improved" value={current.improved} hint="Climbed since the month before" compareLabel={compareLabel} />
        <MetricRow label="Declined" value={current.declined} hint="Dropped since the month before" compareLabel={compareLabel} />
        <MetricRow label="Not ranked" value={current.notRanked} before={comparison.notRanked} better="lower" hint={current.notRecorded > 0 ? `${formatCount(current.notRecorded)} not recorded yet` : undefined} compareLabel={compareLabel} />
        <MetricRow label="Average position" value={current.averagePosition} before={comparison.averagePosition} kind="decimal" better="lower" hint="Of the ranked keywords" compareLabel={compareLabel} />
      </dl>
    </Panel>
  );
}

/** Every target of the month with its achievement bar and labelled status. */
export function TargetAchievementPanel({ targets }: { targets: NonNullable<MarketingDashboard['targets']> }) {
  const { summary } = targets;
  return (
    <Panel
      title="Target achievement"
      icon={Target}
      description={`${summary.achieved} achieved · ${summary.inProgress} in progress · ${summary.behind} behind`}
      action={<PanelLink to="/digital-marketing/targets">Targets</PanelLink>}
    >
      {targets.targets.length === 0 ? (
        <p className="px-5 py-4 text-sm text-muted-foreground">No targets set for this month.</p>
      ) : (
        <ul className="divide-y" aria-label="Targets">
          {targets.targets.map((t) => (
            <li key={t.id} className="space-y-1.5 px-5 py-2.5">
              <div className="flex items-center justify-between gap-2 text-sm">
                <span className="truncate font-medium">{t.type.name}</span>
                <TargetStatusBadge status={t.status} />
              </div>
              <TargetBar label={`${t.type.name} achievement`} achievementPct={t.achievementPct} status={t.status} />
              <p className="flex justify-between text-xs text-muted-foreground tabular-nums">
                <span>
                  {show(t.actual, t.type.unit === 'CURRENCY' ? 'currency' : 'count')} of {show(t.targetValue, t.type.unit === 'CURRENCY' ? 'currency' : 'count')}
                </span>
                <span>{t.status === null ? 'Planned' : `${formatPercent(t.achievementPct)} achieved`}</span>
              </p>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

/** Leads by source against the month before, with the Website Leads target when the viewer may see it. */
export function LeadSourcePanel({ leads, compareLabel }: { leads: LeadSection; compareLabel: string }) {
  const sources = [...leads.bySource].filter((s) => s.leads > 0 || s.comparison > 0).sort((a, b) => b.leads - a.leads);
  const max = Math.max(...sources.map((s) => s.leads), 1);
  return (
    <Panel title="Lead source distribution" icon={UserPlus} description={`${formatCount(leads.total)} leads this month`} action={<PanelLink to="/digital-marketing/leads">Leads</PanelLink>}>
      <div className="space-y-4 p-5">
        {leads.target && (
          <TargetProgress
            label="Website Leads target"
            target={leads.target.targetValue}
            actual={leads.target.actual}
            achievementPct={leads.target.achievementPct}
            remaining={leads.target.remaining}
            status={leads.target.status}
          />
        )}
        {sources.length === 0 ? (
          <p className="text-sm text-muted-foreground">No leads this month or the month before.</p>
        ) : (
          <ul className="space-y-2.5" aria-label="Leads by source">
            {sources.map((s) => (
              <li key={s.source}>
                <div className="flex items-center justify-between gap-2 text-sm">
                  <span>{LEAD_SOURCE_LABELS[s.source]}</span>
                  <span className="font-medium tabular-nums">
                    {formatCount(s.leads)} <span className="text-xs font-normal text-muted-foreground">({formatCount(s.comparison)} in {compareLabel})</span>
                  </span>
                </div>
                <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-muted" aria-hidden>
                  <div className="h-full rounded-full bg-primary/70" style={{ width: `${(s.leads / max) * 100}%` }} />
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Panel>
  );
}

export function EmailPanel({ email, compareLabel }: { email: NonNullable<MarketingDashboard['email']>; compareLabel: string }) {
  const { current: c, comparison: p } = email;
  return (
    <Panel title="Email campaign performance" icon={Mail} description={`${formatCount(c.campaigns)} sent ${c.campaigns === 1 ? 'campaign' : 'campaigns'}`} action={<PanelLink to="/digital-marketing/email-campaigns">Campaigns</PanelLink>}>
      <dl className="divide-y">
        <MetricRow label="Emails sent" value={c.counts.emailsSent} before={p.counts.emailsSent} better={null} compareLabel={compareLabel} />
        <MetricRow label="Unique opens" value={c.counts.uniqueOpens} before={p.counts.uniqueOpens} compareLabel={compareLabel} />
        <MetricRow label="Open rate" value={c.rates.openRate} before={p.rates.openRate} kind="percent" hint="Unique opens ÷ delivered" compareLabel={compareLabel} />
        <MetricRow label="Click rate" value={c.rates.clickRate} before={p.rates.clickRate} kind="percent" hint="Unique clicks ÷ delivered" compareLabel={compareLabel} />
        <MetricRow label="Leads" value={c.counts.leads} before={p.counts.leads} compareLabel={compareLabel} />
        <MetricRow label="Lead conversion" value={c.rates.leadConversionRate} before={p.rates.leadConversionRate} kind="percent" hint="Leads ÷ delivered" compareLabel={compareLabel} />
      </dl>
    </Panel>
  );
}

export function LinkedInPanel({ linkedin, compareLabel }: { linkedin: LinkedInSection; compareLabel: string }) {
  const { current: c, comparison: p } = linkedin;
  return (
    <Panel title="LinkedIn campaign performance" icon={MousePointerClick} description={`${formatCount(linkedin.runningCampaigns)} running · ${formatCount(c.campaigns)} with results`} action={<PanelLink to="/digital-marketing/paid-campaigns">Campaigns</PanelLink>}>
      {linkedin.runningCampaigns > 0 && (
        <div className="space-y-1 px-5 pt-4">
          <p className="text-xs text-muted-foreground tabular-nums">
            {formatInr(linkedin.budget.spent)} of {formatInr(linkedin.budget.budget)} budget spent to date
          </p>
          <BudgetBar label="LinkedIn budget used" progress={linkedin.budget} />
        </div>
      )}
      <dl className="mt-2 divide-y">
        <MetricRow label="Spend" value={c.results.spend} before={p.results.spend} kind="currency" better={null} compareLabel={compareLabel} />
        <MetricRow label="Impressions" value={c.results.impressions} before={p.results.impressions} compareLabel={compareLabel} />
        <MetricRow label="Clicks" value={c.results.clicks} before={p.results.clicks} compareLabel={compareLabel} />
        <MetricRow label="CTR" value={c.rates.ctr} before={p.rates.ctr} kind="percent" hint="Clicks ÷ impressions" compareLabel={compareLabel} />
        <MetricRow label="Leads" value={c.results.leads} before={p.results.leads} compareLabel={compareLabel} />
        <MetricRow label="Cost per lead" value={c.rates.costPerLead} before={p.rates.costPerLead} kind="currency" better="lower" hint="Spend ÷ leads" compareLabel={compareLabel} />
      </dl>
    </Panel>
  );
}

/** One horizontal bar per figure on a common scale, so the funnel reads at a glance. */
function FunnelBars({ label, rows }: { label: string; rows: { label: string; value: number | null; tone: string }[] }) {
  const max = Math.max(...rows.map((r) => r.value ?? 0), 1);
  return (
    <ul className="space-y-2.5" aria-label={label}>
      {rows.map((r) => (
        <li key={r.label} className="grid grid-cols-[6rem_1fr_3rem] items-center gap-3 text-sm">
          <span className="text-muted-foreground">{r.label}</span>
          <div className="h-2 overflow-hidden rounded-full bg-muted" aria-hidden>
            <div className={cn('h-full rounded-full', r.tone)} style={{ width: `${((r.value ?? 0) / max) * 100}%` }} />
          </div>
          <span className="text-right font-semibold tabular-nums">{formatCount(r.value)}</span>
        </li>
      ))}
    </ul>
  );
}

/** Brief section 46: target, submitted, approved, live and what is still to submit. */
export function BacklinkPanel({ backlinks, compareLabel }: { backlinks: NonNullable<MarketingDashboard['backlinks']>; compareLabel: string }) {
  const { current: c, comparison: p, target } = backlinks;
  return (
    <Panel title="Backlink progress" icon={Link2} description={`${formatCount(c.rejected)} rejected · ${formatCount(c.lost)} lost this month`} action={<PanelLink to="/digital-marketing/backlinks">Backlinks</PanelLink>}>
      <div className="space-y-4 p-5">
        <FunnelBars
          label="Backlink funnel"
          rows={[
            ...(target ? [{ label: 'Target', value: target.targetValue, tone: 'bg-muted-foreground/40' }] : []),
            { label: 'Submitted', value: c.submitted, tone: 'bg-primary/50' },
            { label: 'Approved', value: c.approved, tone: 'bg-primary/70' },
            { label: 'Live', value: c.live, tone: 'bg-primary' },
          ]}
        />
        <div className="flex flex-wrap items-center justify-between gap-2 border-t pt-3 text-sm">
          {target ? (
            <span>
              <span className="font-semibold tabular-nums">{formatCount(target.remaining)}</span> still to submit · live {formatPercent(target.target.achievementPct)} of target
            </span>
          ) : (
            <span className="text-muted-foreground">No backlink target shown for this month.</span>
          )}
          {target && <TargetStatusBadge status={target.target.status} />}
        </div>
        <ChangeText current={c.live} previous={p.live} previousLabel={compareLabel} better="higher" className="" />
      </div>
    </Panel>
  );
}

/** Brief section 48: blog target, published, remaining, planned and the leads content brought in. */
export function ContentPanel({ content, compareLabel }: { content: NonNullable<MarketingDashboard['content']>; compareLabel: string }) {
  const { current: c, comparison: p, blogTarget } = content;
  return (
    <Panel title="Content performance" icon={SquarePen} description={`${formatCount(c.publishedAll)} items published · ${formatCount(c.refreshed)} refreshed`} action={<PanelLink to="/digital-marketing/content">Content</PanelLink>}>
      <div className="space-y-4 p-5">
        <FunnelBars
          label="Blog progress"
          rows={[
            ...(blogTarget ? [{ label: 'Target', value: blogTarget.targetValue, tone: 'bg-muted-foreground/40' }] : []),
            { label: 'Planned', value: c.plannedBlogs, tone: 'bg-primary/50' },
            { label: 'Published', value: c.publishedBlogs, tone: 'bg-primary' },
          ]}
        />
        <div className="flex flex-wrap items-center justify-between gap-2 border-t pt-3 text-sm">
          {blogTarget ? (
            <span>
              <span className="font-semibold tabular-nums">{formatCount(blogTarget.remaining)}</span> remaining · {formatPercent(blogTarget.target.achievementPct)} of target
            </span>
          ) : (
            <span className="text-muted-foreground">No blog target shown for this month.</span>
          )}
          {blogTarget && <TargetStatusBadge status={blogTarget.target.status} />}
        </div>
        <dl className="-mx-5 divide-y border-t">
          <MetricRow label="Leads from content" value={c.leads} before={p.leads} compareLabel={compareLabel} />
        </dl>
      </div>
    </Panel>
  );
}

/** Recurring activity occurrences due in the month, and the open ones that need attention (oldest first). */
export function ActivitiesPanel({ activities }: { activities: ActivitySection }) {
  return (
    <Panel title="Recurring activities" icon={CalendarCheck} description="Occurrences due this month" action={<PanelLink to="/digital-marketing/activities">Activities</PanelLink>}>
      <dl className="grid grid-cols-2 gap-3 p-5 sm:grid-cols-4">
        <Count label="Due" value={activities.due} icon={CalendarCheck} />
        <Count label="Completed" value={activities.completed} icon={CircleCheck} hint={activities.completionPct === null ? undefined : `${formatPercent(activities.completionPct)} done`} />
        <Count label="Open" value={activities.open} icon={Hourglass} />
        <Count label="Overdue" value={activities.overdue} icon={Hourglass} alert={activities.overdue > 0} />
      </dl>
      {activities.attention.length === 0 ? (
        <p className="border-t px-5 py-3 text-sm text-muted-foreground">Nothing open is due by the end of the month.</p>
      ) : (
        <ul className="divide-y border-t" aria-label="Needs attention">
          {activities.attention.map((o) => (
            <li key={o.id} className="flex flex-wrap items-center justify-between gap-2 px-5 py-2.5 text-sm">
              <div className="min-w-0">
                <p className="truncate font-medium">{o.activityName}</p>
                <p className="text-xs text-muted-foreground">
                  {o.periodLabel}
                  {o.assignee && ` · ${o.assignee.fullName}`}
                </p>
              </div>
              <span className="flex items-center gap-1.5">
                <OccurrenceStatusBadge status={o.status} />
                <DueBadge dueDate={o.dueDate} state={o.dueState} />
              </span>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

function Count({ label, value, icon: Icon, hint, alert = false }: { label: string; value: number; icon: typeof Hourglass; hint?: string; alert?: boolean }) {
  return (
    <div className="rounded-lg border p-3">
      <dt className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <Icon className="size-3.5" aria-hidden />
        {label}
      </dt>
      <dd className={cn('mt-0.5 text-xl font-semibold tabular-nums', alert && 'text-status-danger')}>
        {formatCount(value)}
        {alert && (
          <Badge tone="danger" className="ml-2 align-middle">
            Overdue
          </Badge>
        )}
      </dd>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}
