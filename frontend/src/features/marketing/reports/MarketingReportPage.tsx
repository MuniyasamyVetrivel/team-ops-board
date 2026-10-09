import { ArrowRight, ChartColumn, Download, FileText, Lock, LockOpen, Snowflake, TrendingDown, TrendingUp } from 'lucide-react';
import { useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { toast } from 'sonner';

import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Panel } from '@/components/common/Panel';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { errorMessage } from '@/lib/api/errors';
import { formatDateTime } from '@/lib/format';

import { MarketingFilterBar } from '../components/MarketingFilterBar';
import { RankingBadge } from '../components/RankingBadges';
import { TargetBar, TargetStatusBadge } from '../components/TargetProgress';
import { formatPercent, periodLabel } from '../marketing-format';
import { useMarketingFilters } from '../use-marketing-filters';
import { exportMonthlyReport, useFreezeReport, useFrozenMonths, useMonthlyReport, type KeywordMove, type MonthlyReport, type ReportGroup } from './api';
import { ReportChange } from './ReportChange';
import { formatReportValue, headlines } from './report-meta';

const AXIS = { fontSize: 12, fill: 'var(--muted-foreground)' };
const INITIAL = { width: 640, height: 260 };
/** Categorical chart tokens: the month before is muted, the month is the primary series. */
const PREVIOUS = 'var(--chart-3)';
const CURRENT = 'var(--chart-1)';

/** Freezing needs every marketing view permission (the server checks it too), so a frozen report is never partial. */
const FULL_ACCESS = ['MARKETING_EDIT', 'SEO_VIEW', 'LEAD_VIEW', 'CAMPAIGN_VIEW', 'BACKLINK_VIEW', 'CONTENT_VIEW', 'TARGET_VIEW'] as const;

/**
 * The Digital Marketing monthly report (brief section 50): every module for the month against the month before
 * ("September 2026 → October 2026", with % change), keyword movements, target achievement, CSV export, and freezing an
 * ended month so its report stays as it stood.
 */
export default function MarketingReportPage() {
  const { user } = useAuth();
  const { context, filters, setFilters } = useMarketingFilters();
  const [live, setLive] = useState(false);
  const [exporting, setExporting] = useState(false);
  const query = filters ? { month: filters.month, year: filters.year, ownerId: filters.ownerId ?? undefined, live: live || undefined } : null;
  const report = useMonthlyReport(query);
  const frozenMonths = useFrozenMonths();
  const freeze = useFreezeReport();

  const current = context.data?.currentPeriod;
  const ended = filters !== null && current !== undefined && filters.year * 12 + filters.month < current.year * 12 + current.month;
  const frozenHere = (frozenMonths.data ?? []).some((f) => filters && f.period.month === filters.month && f.period.year === filters.year);
  const canFreeze = user !== null && FULL_ACCESS.every((p) => hasPermission(user, p)) && ended && filters?.ownerId === null && !frozenHere;

  async function onExport() {
    if (!query) return;
    setExporting(true);
    try {
      await exportMonthlyReport(query);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  }

  async function onFreeze() {
    if (!filters) return;
    try {
      await freeze.mutateAsync({ month: filters.month, year: filters.year });
      setLive(false);
      toast.success(`${periodLabel(filters.month, filters.year)} report frozen`);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  const data = report.data;
  return (
    <div className="space-y-6">
      <PageHeader
        title="Monthly report"
        description={data ? `${data.comparisonPeriod.label} → ${data.period.label}` : filters ? periodLabel(filters.month, filters.year) : undefined}
        actions={
          <div className="flex flex-wrap gap-2">
            {canFreeze && (
              <Button variant="outline" disabled={freeze.isPending} onClick={() => void onFreeze()}>
                <Snowflake aria-hidden />
                Freeze report
              </Button>
            )}
            <Button variant="outline" disabled={exporting || !data} onClick={() => void onExport()}>
              <Download aria-hidden />
              Export CSV
            </Button>
          </div>
        }
      />
      <MarketingFilterBar />

      {report.isError ? (
        <Card>
          <ErrorState error={report.error} title="Couldn't load the monthly report" onRetry={() => void report.refetch()} />
        </Card>
      ) : !data ? (
        <div className="space-y-4" role="status" aria-label="Loading the monthly report">
          <div className="grid gap-3 sm:grid-cols-3 xl:grid-cols-6">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-24 rounded-xl" />
            ))}
          </div>
          <Skeleton className="h-72 rounded-xl" />
        </div>
      ) : (
        <div className="space-y-6" aria-busy={report.isPlaceholderData}>
          <FrozenNotice report={data} frozenHere={frozenHere} live={live} onLiveChange={setLive} />
          <Headlines report={data} />
          <ComparisonChart report={data} />
          <div className="grid gap-6 xl:grid-cols-2">
            {data.groups.map((g) => (
              <GroupTable key={g.key} group={g} report={data} />
            ))}
          </div>
          {data.keywordMovements && (
            <div className="grid gap-6 xl:grid-cols-2">
              <MovesPanel title="Keywords that improved most" icon={TrendingUp} moves={data.keywordMovements.improved} empty="No keyword climbed this month." />
              <MovesPanel title="Keywords that declined most" icon={TrendingDown} moves={data.keywordMovements.declined} empty="No keyword dropped this month." />
            </div>
          )}
          {data.targets && <TargetsPanel report={data} />}
        </div>
      )}

      {(frozenMonths.data?.length ?? 0) > 0 && (
        <Panel title="Frozen reports" icon={Lock} description="Months whose report is kept as it stood">
          <ul className="flex flex-wrap gap-2 p-4" aria-label="Frozen reports">
            {frozenMonths.data?.map((f) => (
              <li key={f.period.label}>
                <Button variant="outline" size="sm" onClick={() => setFilters({ month: f.period.month, year: f.period.year, ownerId: null })}>
                  <FileText aria-hidden />
                  {f.period.label}
                </Button>
              </li>
            ))}
          </ul>
        </Panel>
      )}
    </div>
  );
}

function FrozenNotice({ report, frozenHere, live, onLiveChange }: { report: MonthlyReport; frozenHere: boolean; live: boolean; onLiveChange: (live: boolean) => void }) {
  if (!frozenHere || report.ownerId !== null) return null;
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-muted/40 px-4 py-3 text-sm" role="status">
      <span className="flex items-center gap-2">
        {report.frozen ? <Lock className="size-4" aria-hidden /> : <LockOpen className="size-4" aria-hidden />}
        {report.frozen
          ? `Frozen on ${formatDateTime(report.generatedAt)}${report.generatedBy ? ` by ${report.generatedBy.fullName}` : ''}. These figures stay as reported.`
          : 'Showing live figures; later corrections are included.'}
      </span>
      <label className="flex items-center gap-2">
        <Checkbox checked={live} onChange={(e) => onLiveChange(e.target.checked)} />
        Show live figures
      </label>
    </div>
  );
}

/** Brief section 50's example: "Leads 180 → 200, +11.1%". */
function Headlines({ report }: { report: MonthlyReport }) {
  const items = headlines(report);
  if (items.length === 0) return null;
  return (
    <section aria-label={`${report.comparisonPeriod.label} to ${report.period.label}`} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6">
      {items.map(({ title, line }) => (
        <div key={title} className="rounded-xl border bg-card p-4 shadow-xs">
          <p className="text-sm text-muted-foreground">{title}</p>
          <p className="mt-1 flex items-baseline gap-1.5 tabular-nums">
            <span className="text-sm text-muted-foreground">{formatReportValue(line.previous, line.unit)}</span>
            <ArrowRight className="size-3.5 self-center text-muted-foreground" aria-label="to" />
            <span className="text-2xl font-semibold tracking-tight">{formatReportValue(line.current, line.unit)}</span>
          </p>
          <ReportChange line={line} className="mt-1" />
        </div>
      ))}
    </section>
  );
}

/** One group's counts side by side for the two months. */
function ComparisonChart({ report }: { report: MonthlyReport }) {
  const chartable = report.groups.filter((g) => g.lines.some((l) => l.unit === 'COUNT'));
  const [key, setKey] = useState<string>(chartable[0]?.key ?? '');
  const group = chartable.find((g) => g.key === key) ?? chartable[0];
  if (!group) return null;
  const data = group.lines
    .filter((l) => l.unit === 'COUNT' && ((l.current ?? 0) > 0 || (l.previous ?? 0) > 0))
    .map((l) => ({ label: l.label, previous: l.previous ?? 0, current: l.current ?? 0 }));

  return (
    <Panel
      title="Month against month"
      icon={ChartColumn}
      description={`${report.comparisonPeriod.label} and ${report.period.label}`}
      action={
        <Select aria-label="Chart section" className="w-56" value={group.key} onChange={(e) => setKey(e.target.value)}>
          {chartable.map((g) => (
            <option key={g.key} value={g.key}>
              {g.title}
            </option>
          ))}
        </Select>
      }
    >
      {data.length === 0 ? (
        <p className="px-5 py-4 text-sm text-muted-foreground">Nothing to compare in this section.</p>
      ) : (
        <figure aria-label={`${group.title}: ${report.comparisonPeriod.label} and ${report.period.label}`} className="space-y-2 p-5">
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%" initialDimension={INITIAL}>
              <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={false} interval={0} />
                <YAxis tick={AXIS} tickLine={false} axisLine={false} width={40} allowDecimals={false} />
                <Tooltip cursor={{ fill: 'var(--muted)', opacity: 0.4 }} />
                <Bar dataKey="previous" name={report.comparisonPeriod.label} fill={PREVIOUS} radius={[3, 3, 0, 0]} isAnimationActive={false} />
                <Bar dataKey="current" name={report.period.label} fill={CURRENT} radius={[3, 3, 0, 0]} isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
          </div>
          <figcaption>
            <ul className="flex flex-wrap gap-4 text-xs text-muted-foreground" aria-label="Chart legend">
              <li className="flex items-center gap-1.5">
                <span className="size-2.5 rounded-sm" style={{ background: PREVIOUS }} aria-hidden />
                {report.comparisonPeriod.label}
              </li>
              <li className="flex items-center gap-1.5">
                <span className="size-2.5 rounded-sm" style={{ background: CURRENT }} aria-hidden />
                {report.period.label}
              </li>
            </ul>
          </figcaption>
        </figure>
      )}
    </Panel>
  );
}

function GroupTable({ group, report }: { group: ReportGroup; report: MonthlyReport }) {
  return (
    <Card>
      <div className="border-b px-5 py-3.5">
        <h2 className="font-semibold">{group.title}</h2>
      </div>
      <Table aria-label={group.title}>
        <TableHeader>
          <TableRow>
            <TableHead>Measure</TableHead>
            <TableHead className="text-right">{report.comparisonPeriod.label}</TableHead>
            <TableHead className="text-right">{report.period.label}</TableHead>
            <TableHead>Change</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {group.lines.map((l) => (
            <TableRow key={l.label}>
              <TableCell className="font-medium">{l.label}</TableCell>
              <TableCell className="text-right tabular-nums text-muted-foreground">{formatReportValue(l.previous, l.unit)}</TableCell>
              <TableCell className="text-right font-semibold tabular-nums">{formatReportValue(l.current, l.unit)}</TableCell>
              <TableCell>
                <ReportChange line={l} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Card>
  );
}

const rankingStatus = (position: number | null) => (position === null ? 'NOT_RANKED' : position <= 10 ? 'TOP_10' : 'RANKING');

function MovesPanel({ title, icon, moves, empty }: { title: string; icon: typeof TrendingUp; moves: KeywordMove[]; empty: string }) {
  return (
    <Panel title={title} icon={icon}>
      {moves.length === 0 ? (
        <p className="px-5 py-4 text-sm text-muted-foreground">{empty}</p>
      ) : (
        <ul className="divide-y" aria-label={title}>
          {moves.map((m) => (
            <li key={m.keywordId} className="flex items-center justify-between gap-3 px-5 py-2.5 text-sm">
              <div className="min-w-0">
                <p className="truncate font-medium">{m.keyword}</p>
                {m.page && <p className="truncate text-xs text-muted-foreground">{m.page}</p>}
              </div>
              <span className="flex shrink-0 items-center gap-1.5">
                <RankingBadge position={m.previousPosition} status={rankingStatus(m.previousPosition)} />
                <ArrowRight className="size-3.5 text-muted-foreground" aria-label="to" />
                <RankingBadge position={m.position} status={rankingStatus(m.position)} />
                {m.change !== null && (
                  <span className="w-14 text-right text-xs font-medium tabular-nums">
                    {m.change > 0 ? `+${m.change}` : m.change} {Math.abs(m.change) === 1 ? 'place' : 'places'}
                  </span>
                )}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

function TargetsPanel({ report }: { report: MonthlyReport }) {
  const targets = report.targets ?? [];
  return (
    <Card>
      <div className="border-b px-5 py-3.5">
        <h2 className="font-semibold">Target achievement</h2>
        <p className="text-sm text-muted-foreground">Each target with the same type&apos;s achievement in {report.comparisonPeriod.label}</p>
      </div>
      {targets.length === 0 ? (
        <p className="px-5 py-4 text-sm text-muted-foreground">No targets set for this month.</p>
      ) : (
        <Table aria-label="Target achievement">
          <TableHeader>
            <TableRow>
              <TableHead>Target</TableHead>
              <TableHead className="text-right">Target</TableHead>
              <TableHead className="text-right">Actual</TableHead>
              <TableHead className="min-w-40">Achievement</TableHead>
              <TableHead className="text-right">{report.comparisonPeriod.label}</TableHead>
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {targets.map((t) => {
              const unit = t.unit === 'CURRENCY' ? 'CURRENCY' : 'COUNT';
              return (
                <TableRow key={t.type}>
                  <TableCell className="font-medium">{t.type}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatReportValue(t.targetValue, unit)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatReportValue(t.actual, unit)}</TableCell>
                  <TableCell>
                    <TargetBar label={`${t.type} achievement`} achievementPct={t.achievementPct} status={t.status} />
                    <p className="mt-1 text-xs text-muted-foreground tabular-nums">{formatPercent(t.achievementPct)}</p>
                  </TableCell>
                  <TableCell className="text-right text-sm tabular-nums text-muted-foreground">{formatPercent(t.previousAchievementPct)}</TableCell>
                  <TableCell>
                    <TargetStatusBadge status={t.status} />
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
    </Card>
  );
}
