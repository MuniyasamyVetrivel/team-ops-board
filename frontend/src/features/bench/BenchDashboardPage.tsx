import { Armchair, CalendarClock, ChartPie, Gauge, Hourglass, Sparkles, Timer, Users, UsersRound } from 'lucide-react';
import { Link } from 'react-router';

import { DonutChart } from '@/components/charts/DonutChart';
import { CHART_COLORS } from '@/components/charts/chart-theme';
import { EmptyState } from '@/components/common/EmptyState';
import { KpiGrid, KpiProgress, KpiTile } from '@/components/common/KpiCard';
import { Panel, PanelLink } from '@/components/common/Panel';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { ToolSection } from '@/features/modules/ToolSection';

import { CONSULTANT_STATUSES, useBenchDashboard, type BenchDashboard } from './api';
import { BenchErrorState, ConsultantCell, ConsultantStatusBadge } from './BenchBadges';
import { allocationHref, BENCH_PATHS } from './bench-paths';
import { formatBenchDate, formatDays, formatFte, formatPercent, rollOffLabel, rollOffTone } from './bench-meta';

/**
 * Bench Management on today's business date (the server's `asOf`): utilisation, billable vs non-billable, who is on
 * the bench and for how long, roll-offs in the next 30 days and the skills available on the bench. Every figure is
 * computed by the server; the page only renders it.
 */
export default function BenchDashboardPage() {
  const dashboard = useBenchDashboard();

  return (
    <div className="space-y-6">
      <ToolSection
        title="Dashboard"
        description={dashboard.data ? `As of ${formatBenchDate(dashboard.data.asOf)}. One consultant at 100% is 1.00 FTE.` : 'Utilisation, the bench and upcoming roll-offs.'}
      />
      {dashboard.isPending ? (
        <DashboardSkeleton />
      ) : dashboard.isError ? (
        <Card>
          <BenchErrorState error={dashboard.error} title="Couldn't load the dashboard" onRetry={() => void dashboard.refetch()} />
        </Card>
      ) : dashboard.data.byStatus.every((s) => s.count === 0) ? (
        <Card>
          <EmptyState
            icon={UsersRound}
            title="No consultants yet"
            description="Add consultants from Employee records, or import them from a CSV, to see utilisation and the bench."
            action={
              <Button asChild variant="outline">
                <Link to={BENCH_PATHS.consultants}>Go to consultants</Link>
              </Button>
            }
          />
        </Card>
      ) : (
        <DashboardBody data={dashboard.data} />
      )}
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6" role="status" aria-label="Loading the dashboard">
      <KpiGrid>
        {Array.from({ length: 4 }, (_, i) => (
          <KpiTile key={i} label="Loading" value="" loading />
        ))}
      </KpiGrid>
      <div className="grid gap-6 lg:grid-cols-2">
        <Skeleton className="h-72 rounded-xl" />
        <Skeleton className="h-72 rounded-xl" />
      </div>
      <Skeleton className="h-64 rounded-xl" />
    </div>
  );
}

function DashboardBody({ data }: { data: BenchDashboard }) {
  const noCapacity = data.capacityConsultants === 0;
  return (
    <>
      <KpiGrid aria-label="Bench figures">
        <KpiTile
          label="Utilisation"
          icon={Gauge}
          value={formatPercent(data.utilisationPercent)}
          muted={data.utilisationPercent === null}
          hint={noCapacity ? 'Nobody is available yet' : `${formatFte(data.allocatedFte)} of ${formatFte(data.capacityFte)} FTE allocated`}
          progress={<KpiProgress value={data.utilisationPercent} label="Utilisation" />}
        />
        <KpiTile
          label="Billable"
          icon={ChartPie}
          value={formatPercent(data.billablePercent)}
          muted={data.billablePercent === null}
          hint={`${formatFte(data.billableFte)} FTE billable · ${formatFte(data.nonBillableFte)} non-billable`}
          progress={<KpiProgress value={data.billablePercent} label="Billable share of capacity" />}
        />
        <KpiTile
          label="On bench now"
          icon={Armchair}
          tone="text-status-warning"
          value={data.onBenchNow.toLocaleString()}
          muted={data.onBenchNow === 0}
          hint={data.unallocated === 1 ? '1 with no allocation at all' : `${data.unallocated.toLocaleString()} with no allocation at all`}
          to={BENCH_PATHS.bench}
        />
        <KpiTile
          label="Average days on bench"
          icon={Hourglass}
          value={data.averageDaysOnBench === null ? '—' : Math.round(data.averageDaysOnBench).toLocaleString()}
          muted={data.averageDaysOnBench === null}
          hint={data.onBenchNow > 0 ? `Longest: ${formatDays(data.longestDaysOnBench)}` : 'Nobody is on the bench'}
          to={BENCH_PATHS.bench}
        />
      </KpiGrid>

      <div className="grid gap-6 lg:grid-cols-2">
        <Panel title="Billable vs non-billable" icon={ChartPie} description={`Capacity: ${data.capacityConsultants.toLocaleString()} consultants (${formatFte(data.capacityFte)} FTE)`}>
          <div className="px-6 py-5">
            <DonutChart
              ariaLabel="Capacity by allocation, in FTE"
              centerLabel="utilised"
              centerValue={formatPercent(data.utilisationPercent)}
              formatValue={formatFte}
              segments={[
                { key: 'billable', label: 'Billable', value: data.billableFte, color: CHART_COLORS[0] },
                { key: 'non-billable', label: 'Non-billable', value: data.nonBillableFte, color: CHART_COLORS[2] },
                { key: 'bench', label: 'On the bench (unallocated)', value: data.benchFte, color: CHART_COLORS[1] },
              ]}
            />
          </div>
        </Panel>
        <StatusPanel data={data} />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <RollOffsPanel data={data} />
        <LongestOnBenchPanel data={data} />
      </div>

      <SkillsPanel data={data} />
    </>
  );
}

function StatusPanel({ data }: { data: BenchDashboard }) {
  const counts = new Map(data.byStatus.map((s) => [s.status, s.count]));
  const total = data.byStatus.reduce((sum, s) => sum + s.count, 0);
  return (
    <Panel title="Consultants by status" icon={Users} description={`${total.toLocaleString()} consultants`} action={<PanelLink to={BENCH_PATHS.consultants}>All consultants</PanelLink>}>
      <ul className="divide-y" aria-label="Consultants by status">
        {CONSULTANT_STATUSES.map((status) => {
          const count = counts.get(status) ?? 0;
          const share = total ? (count / total) * 100 : 0;
          return (
            <li key={status} className="flex items-center gap-3 px-6 py-2.5">
              <span className="w-44 shrink-0">
                <ConsultantStatusBadge status={status} />
              </span>
              <span className="hidden h-1.5 flex-1 overflow-hidden rounded-full bg-muted sm:block" aria-hidden>
                <span className="block h-full rounded-full bg-primary/70" style={{ width: `${share}%` }} />
              </span>
              <span className={`ml-auto w-10 text-right text-sm font-semibold tabular-nums ${count === 0 ? 'text-muted-foreground' : ''}`}>{count.toLocaleString()}</span>
            </li>
          );
        })}
      </ul>
    </Panel>
  );
}

function RollOffsPanel({ data }: { data: BenchDashboard }) {
  const more = data.upcomingRollOffCount - data.upcomingRollOffs.length;
  return (
    <Panel
      title="Upcoming roll-offs"
      icon={CalendarClock}
      description={`${data.upcomingRollOffCount.toLocaleString()} allocations end in the next 30 days`}
      action={<PanelLink to={`${BENCH_PATHS.allocations}?status=ACTIVE&sort=end,asc`}>All allocations</PanelLink>}
    >
      {data.upcomingRollOffs.length === 0 ? (
        <EmptyState icon={CalendarClock} title="No roll-offs in the next 30 days" className="py-10" />
      ) : (
        <ul className="divide-y">
          {data.upcomingRollOffs.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-6 py-3">
              <div className="min-w-0 flex-1">
                <ConsultantCell consultantId={a.consultant.id} name={a.consultant.fullName} user={a.consultant.user} detail={`${a.engagement.client.name} · ${a.engagement.name} · ${a.allocationPercent}%`} />
              </div>
              <Link to={allocationHref(a.id)} className="text-right">
                <Badge tone={rollOffTone(a.daysUntilEnd)}>
                  <Timer aria-hidden />
                  {rollOffLabel(a.daysUntilEnd)}
                </Badge>
                <span className="block text-xs text-muted-foreground">{formatBenchDate(a.endDate)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
      {more > 0 && <p className="border-t px-6 py-2.5 text-xs text-muted-foreground">And {more.toLocaleString()} more</p>}
    </Panel>
  );
}

function LongestOnBenchPanel({ data }: { data: BenchDashboard }) {
  const longest = Math.max(1, ...data.longestOnBench.map((e) => e.daysOnBench));
  return (
    <Panel title="Longest on the bench" icon={Hourglass} description="Below 100% today, by days on the bench" action={<PanelLink to={BENCH_PATHS.bench}>Full bench</PanelLink>}>
      {data.longestOnBench.length === 0 ? (
        <EmptyState icon={Armchair} title="Nobody is on the bench" description="Every available consultant is fully allocated today." className="py-10" />
      ) : (
        <ul className="divide-y">
          {data.longestOnBench.map((e) => (
            <li key={e.consultantId} className="flex items-center gap-3 px-6 py-3">
              <div className="min-w-0 flex-1">
                <ConsultantCell consultantId={e.consultantId} name={e.employee?.fullName} user={e.employee?.user} detail={`${e.benchPercent}% free · since ${formatBenchDate(e.benchSince)}`} />
              </div>
              <span className="hidden h-1.5 w-24 overflow-hidden rounded-full bg-muted sm:block" aria-hidden>
                <span className="block h-full rounded-full bg-highlight" style={{ width: `${(e.daysOnBench / longest) * 100}%` }} />
              </span>
              <span className="w-20 text-right text-sm font-semibold tabular-nums">{formatDays(e.daysOnBench)}</span>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

function SkillsPanel({ data }: { data: BenchDashboard }) {
  const top = Math.max(1, ...data.benchSkills.map((s) => s.consultants));
  return (
    <Panel title="Skills on the bench" icon={Sparkles} description="Skills of consultants below 100% today, most available first">
      {data.benchSkills.length === 0 ? (
        <EmptyState icon={Sparkles} title="No skills on the bench" description="Nobody is on the bench, or their profiles list no skills yet." className="py-10" />
      ) : (
        <ul className="grid gap-x-8 gap-y-1 px-6 py-4 sm:grid-cols-2 xl:grid-cols-3" aria-label="Skills on the bench">
          {data.benchSkills.map((s) => (
            <li key={s.skill}>
              <Link to={`${BENCH_PATHS.bench}?skill=${encodeURIComponent(s.skill)}`} className="group flex items-center gap-3 rounded-md py-1.5">
                <span className="min-w-0 flex-1 truncate text-sm group-hover:underline">{s.skill}</span>
                <span className="h-1.5 w-20 overflow-hidden rounded-full bg-muted" aria-hidden>
                  <span className="block h-full rounded-full bg-primary" style={{ width: `${(s.consultants / top) * 100}%` }} />
                </span>
                <span className="w-24 text-right text-xs text-muted-foreground tabular-nums">{s.consultants === 1 ? '1 consultant' : `${s.consultants} consultants`}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}
