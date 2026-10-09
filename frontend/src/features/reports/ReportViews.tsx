import { AlarmClock, CircleCheck, ClipboardList, Clock, Hourglass, LifeBuoy, ShieldCheck, Timer } from 'lucide-react';
import type { ReactNode } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { ErrorState } from '@/components/common/ErrorState';
import { Panel } from '@/components/common/Panel';
import { UserCell } from '@/components/common/UserAvatar';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { MarketingKpiCard } from '@/features/marketing/components/MarketingKpiCard';
import { formatCount, formatDecimal, formatPercent } from '@/features/marketing/marketing-format';
import { ProjectStatusBadge } from '@/features/projects/ProjectBadges';
import { TaskStatusBadge } from '@/features/tasks/TaskBadges';
import { PRIORITY_LABELS } from '@/features/tasks/task-meta';
import { TICKET_STATUS_LABELS } from '@/features/tickets/ticket-meta';
import { WorkloadLevelBadge, WorkloadMeter } from '@/features/workload/WorkloadMeter';
import { cn } from '@/lib/utils';

import type { ProjectReport, TaskReport, TicketReport, WorkloadReport } from './api';

const AXIS = { fontSize: 12, fill: 'var(--muted-foreground)' };
const INITIAL = { width: 640, height: 240 };
/** Categorical chart tokens (never the status colours). */
const FIRST = 'var(--chart-3)';
const SECOND = 'var(--chart-1)';

const shortDay = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', timeZone: 'UTC' });
const weekLabel = (iso: string) => shortDay.format(new Date(`${iso}T00:00:00Z`));

/** Skeleton, error or the report: every view shares these states. */
export function ReportState<T>({ query, label, children }: { query: { data?: T; isError: boolean; error: unknown; refetch: () => unknown }; label: string; children: (data: T) => ReactNode }) {
  if (query.isError) {
    return (
      <Card>
        <ErrorState error={query.error} title={`Couldn't load the ${label}`} onRetry={() => void query.refetch()} />
      </Card>
    );
  }
  if (!query.data) {
    return (
      <div className="space-y-4" role="status" aria-label={`Loading the ${label}`}>
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }
  return <>{children(query.data)}</>;
}

/** Two series per category on one bar chart, with a legend in words. */
function PairChart({ label, data, first, second }: { label: string; data: { label: string; a: number; b: number }[]; first: string; second: string }) {
  return (
    <figure aria-label={label} className="space-y-2 p-5">
      <div className="h-60">
        <ResponsiveContainer width="100%" height="100%" initialDimension={INITIAL}>
          <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
            <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={false} />
            <YAxis tick={AXIS} tickLine={false} axisLine={false} width={36} allowDecimals={false} />
            <Tooltip cursor={{ fill: 'var(--muted)', opacity: 0.4 }} />
            <Bar dataKey="a" name={first} fill={FIRST} radius={[3, 3, 0, 0]} isAnimationActive={false} />
            <Bar dataKey="b" name={second} fill={SECOND} radius={[3, 3, 0, 0]} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <figcaption>
        <ul className="flex flex-wrap gap-4 text-xs text-muted-foreground" aria-label="Chart legend">
          <li className="flex items-center gap-1.5">
            <span className="size-2.5 rounded-sm" style={{ background: FIRST }} aria-hidden />
            {first}
          </li>
          <li className="flex items-center gap-1.5">
            <span className="size-2.5 rounded-sm" style={{ background: SECOND }} aria-hidden />
            {second}
          </li>
        </ul>
      </figcaption>
    </figure>
  );
}

/** Labelled horizontal bars on a common scale (open work by status, ticket ageing). */
function BarList({ label, rows }: { label: string; rows: { key: string; name: ReactNode; value: number }[] }) {
  const max = Math.max(...rows.map((r) => r.value), 1);
  return (
    <ul className="space-y-2.5 p-5" aria-label={label}>
      {rows.map((r) => (
        <li key={r.key} className="grid grid-cols-[9rem_1fr_3rem] items-center gap-3 text-sm">
          <span className="truncate">{r.name}</span>
          <div className="h-2 overflow-hidden rounded-full bg-muted" aria-hidden>
            <div className="h-full rounded-full bg-primary/70" style={{ width: `${(r.value / max) * 100}%` }} />
          </div>
          <span className="text-right font-semibold tabular-nums">{formatCount(r.value)}</span>
        </li>
      ))}
    </ul>
  );
}

/** A rate's change against the previous period, in points: "Up 8 pts vs September 2026". */
function pointsHint(now: number | null, before: number | null | undefined, label: string): string | undefined {
  if (now === null || before === null || before === undefined) return undefined;
  const diff = Math.round((now - before) * 10) / 10;
  return diff === 0 ? `No change vs ${label}` : `${diff > 0 ? 'Up' : 'Down'} ${Math.abs(diff)} pts vs ${label}`;
}

interface Comparison<T> {
  data: T | undefined;
  label: string;
}

/** Task completion, overdue %, department performance, employee productivity and the weekly trend. */
export function TaskReportView({ report, previous }: { report: TaskReport; previous: Comparison<TaskReport> }) {
  const s = report.summary;
  const p = previous.data?.summary;
  return (
    <div className="space-y-6">
      <section aria-label="Task figures" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6">
        <MarketingKpiCard label="Tasks created" icon={ClipboardList} value={s.created} previous={p?.created ?? null} previousLabel={previous.label} better={null} />
        <MarketingKpiCard label="Tasks completed" icon={CircleCheck} value={s.completed} previous={p?.completed ?? null} previousLabel={previous.label} />
        <MarketingKpiCard label="On time" icon={Clock} value={s.onTimePct} format="percent" hint={pointsHint(s.onTimePct, p?.onTimePct, previous.label) ?? `${formatCount(s.completedOnTime)} of ${formatCount(s.completedWithDueDate)} with a due date`} />
        <MarketingKpiCard label="Hours logged" icon={Timer} value={s.hoursLogged} format="decimal" previous={p?.hoursLogged ?? null} previousLabel={previous.label} better={null} hint="On tasks completed in the range" />
        <MarketingKpiCard label="Open now" icon={Hourglass} value={s.open} hint="Active tasks today" />
        <MarketingKpiCard label="Overdue now" icon={AlarmClock} value={s.overdue} hint={s.overduePct === null ? undefined : `${formatPercent(s.overduePct)} of open tasks`} />
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <Panel title="Weekly trend" icon={ClipboardList} description="Tasks created and completed per week">
          <PairChart label="Tasks created and completed per week" first="Created" second="Completed" data={report.trend.map((w) => ({ label: weekLabel(w.weekStart), a: w.created, b: w.completed }))} />
        </Panel>
        <Panel title="Open tasks by status" icon={Hourglass}>
          <BarList label="Open tasks by status" rows={report.openByStatus.map((o) => ({ key: o.status, name: <TaskStatusBadge status={o.status} />, value: o.tasks }))} />
        </Panel>
      </div>

      <Panel title="Department performance" icon={ShieldCheck} description="Created and completed in the range; open and overdue now">
        {report.departments.length === 0 ? (
          <p className="px-5 py-4 text-sm text-muted-foreground">No tasks in this report.</p>
        ) : (
          <Table aria-label="Department performance">
            <TableHeader>
              <TableRow>
                <TableHead>Department</TableHead>
                <TableHead className="text-right">Created</TableHead>
                <TableHead className="text-right">Completed</TableHead>
                <TableHead className="text-right">On time</TableHead>
                <TableHead className="text-right">Open</TableHead>
                <TableHead className="text-right">Overdue</TableHead>
                <TableHead className="text-right">Overdue %</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {report.departments.map((d) => (
                <TableRow key={d.department.id}>
                  <TableCell className="font-medium">{d.department.name}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(d.created)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(d.completed)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatPercent(d.onTimePct)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(d.open)}</TableCell>
                  <TableCell className={cn('text-right tabular-nums', d.overdue > 0 && 'text-status-danger')}>{formatCount(d.overdue)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatPercent(d.overduePct)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Panel>

      <Panel title="Employee productivity" icon={CircleCheck} description="Most completed first">
        {report.employees.length === 0 ? (
          <p className="px-5 py-4 text-sm text-muted-foreground">No assigned tasks in this report.</p>
        ) : (
          <Table aria-label="Employee productivity">
            <TableHeader>
              <TableRow>
                <TableHead>Employee</TableHead>
                <TableHead className="text-right">Completed</TableHead>
                <TableHead className="text-right">On time</TableHead>
                <TableHead className="text-right">Open</TableHead>
                <TableHead className="text-right">Overdue</TableHead>
                <TableHead className="text-right">Hours logged</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {report.employees.map((e) => (
                <TableRow key={e.user.id}>
                  <TableCell className="max-w-56">
                    <UserCell name={e.user.fullName} detail={e.department.name} />
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(e.completed)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatPercent(e.onTimePct)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(e.open)}</TableCell>
                  <TableCell className={cn('text-right tabular-nums', e.overdue > 0 && 'text-status-danger')}>{formatCount(e.overdue)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatDecimal(e.hoursLogged)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Panel>
    </div>
  );
}

/** Department workload (bar chart and table) and every employee's workload. */
export function WorkloadReportView({ report }: { report: WorkloadReport }) {
  return (
    <div className="space-y-6">
      <Panel title="Department workload" icon={Hourglass} description={`Remaining hours against capacity over the next ${report.employees.windowDays} days`}>
        {report.departments.length === 0 ? (
          <p className="px-5 py-4 text-sm text-muted-foreground">No people in this report.</p>
        ) : (
          <Table aria-label="Department workload">
            <TableHeader>
              <TableRow>
                <TableHead>Department</TableHead>
                <TableHead className="text-right">People</TableHead>
                <TableHead className="text-right">Active tasks</TableHead>
                <TableHead className="text-right">Overdue</TableHead>
                <TableHead className="text-right">Remaining h</TableHead>
                <TableHead className="text-right">Capacity h</TableHead>
                <TableHead className="min-w-44">Workload</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {report.departments.map((d) => (
                <TableRow key={d.department.id}>
                  <TableCell className="font-medium">{d.department.name}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(d.people)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(d.activeTasks)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(d.overdue)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatDecimal(d.remainingHours)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatDecimal(d.capacityHours)}</TableCell>
                  <TableCell>{d.workloadPercent === null || d.level === null ? <span className="text-muted-foreground">—</span> : <WorkloadMeter percent={d.workloadPercent} level={d.level} />}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Panel>
      <Panel title="Employee workload" icon={ClipboardList} description="Highest workload first">
        <Table aria-label="Employee workload">
          <TableHeader>
            <TableRow>
              <TableHead>Employee</TableHead>
              <TableHead className="text-right">Active</TableHead>
              <TableHead className="text-right">Overdue</TableHead>
              <TableHead className="text-right">Due today</TableHead>
              <TableHead className="min-w-44">Workload</TableHead>
              <TableHead>Level</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {report.employees.rows.map((r) => (
              <TableRow key={r.user.id}>
                <TableCell className="max-w-56">
                  <UserCell name={r.user.fullName} detail={r.department.name} />
                </TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(r.activeTasks)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(r.overdue)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(r.dueToday)}</TableCell>
                <TableCell>
                  <WorkloadMeter percent={r.workloadPercent} level={r.level} />
                </TableCell>
                <TableCell>
                  <WorkloadLevelBadge level={r.level} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Panel>
    </div>
  );
}

/** Ticket performance: created and resolved, SLA compliance, ageing, priorities and departments. */
export function TicketReportView({ report, previous }: { report: TicketReport; previous: Comparison<TicketReport> }) {
  const s = report.summary;
  const p = previous.data?.summary;
  return (
    <div className="space-y-6">
      <section aria-label="Ticket figures" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6">
        <MarketingKpiCard label="Tickets created" icon={LifeBuoy} value={s.created} previous={p?.created ?? null} previousLabel={previous.label} better={null} />
        <MarketingKpiCard label="Tickets resolved" icon={CircleCheck} value={s.resolved} previous={p?.resolved ?? null} previousLabel={previous.label} />
        <MarketingKpiCard label="First response SLA" icon={ShieldCheck} value={s.firstResponseCompliance} format="percent" hint={pointsHint(s.firstResponseCompliance, p?.firstResponseCompliance, previous.label) ?? 'Met, of tickets created in the range'} />
        <MarketingKpiCard label="Resolution SLA" icon={ShieldCheck} value={s.resolutionCompliance} format="percent" hint={pointsHint(s.resolutionCompliance, p?.resolutionCompliance, previous.label)} />
        <MarketingKpiCard label="Average resolution" icon={Timer} value={s.averageResolutionHours} format="decimal" previous={p?.averageResolutionHours ?? null} previousLabel={previous.label} better="lower" hint="Hours from creation" />
        <MarketingKpiCard label="Open now" icon={Hourglass} value={s.open} hint={`${formatCount(s.openBreached)} with a breached SLA`} />
      </section>

      <div className="grid gap-6 xl:grid-cols-2">
        <Panel title="Open ticket ageing" icon={AlarmClock} description="Open tickets by time since they were raised">
          <BarList label="Open ticket ageing" rows={report.ageing.map((a) => ({ key: a.label, name: a.label, value: a.tickets }))} />
        </Panel>
        <Panel title="Open tickets by status" icon={Hourglass}>
          <BarList label="Open tickets by status" rows={report.openByStatus.map((o) => ({ key: o.status, name: TICKET_STATUS_LABELS[o.status], value: o.tickets }))} />
        </Panel>
      </div>

      <Panel title="By priority" icon={ShieldCheck}>
        <PairChart label="Tickets created and resolved by priority" first="Created" second="Resolved" data={report.priorities.map((r) => ({ label: PRIORITY_LABELS[r.priority], a: r.created, b: r.resolved }))} />
        <Table aria-label="Tickets by priority">
          <TableHeader>
            <TableRow>
              <TableHead>Priority</TableHead>
              <TableHead className="text-right">Created</TableHead>
              <TableHead className="text-right">Resolved</TableHead>
              <TableHead className="text-right">Open</TableHead>
              <TableHead className="text-right">First response SLA</TableHead>
              <TableHead className="text-right">Resolution SLA</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {report.priorities.map((r) => (
              <TableRow key={r.priority}>
                <TableCell className="font-medium">{PRIORITY_LABELS[r.priority]}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(r.created)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(r.resolved)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatCount(r.open)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatPercent(r.firstResponseCompliance)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatPercent(r.resolutionCompliance)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Panel>

      <Panel title="By department" icon={LifeBuoy} description="The handling department">
        {report.departments.length === 0 ? (
          <p className="px-5 py-4 text-sm text-muted-foreground">No tickets in this report.</p>
        ) : (
          <Table aria-label="Tickets by department">
            <TableHeader>
              <TableRow>
                <TableHead>Department</TableHead>
                <TableHead className="text-right">Created</TableHead>
                <TableHead className="text-right">Resolved</TableHead>
                <TableHead className="text-right">Open</TableHead>
                <TableHead className="text-right">Open breached</TableHead>
                <TableHead className="text-right">Resolution SLA</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {report.departments.map((d) => (
                <TableRow key={d.department.id}>
                  <TableCell className="font-medium">{d.department.name}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(d.created)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(d.resolved)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(d.open)}</TableCell>
                  <TableCell className={cn('text-right tabular-nums', d.openBreached > 0 && 'text-status-danger')}>{formatCount(d.openBreached)}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatPercent(d.resolutionCompliance)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Panel>
    </div>
  );
}

const day = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
const dayLabel = (iso: string | null) => (iso ? day.format(new Date(`${iso}T00:00:00Z`)) : '—');

/** Project progress: projects by status and each project's progress, tasks, milestones and risks. */
export function ProjectReportView({ report }: { report: ProjectReport }) {
  return (
    <div className="space-y-6">
      <section aria-label="Projects by status" className="flex flex-wrap gap-3">
        {report.byStatus.map((s) => (
          <div key={s.status} className="flex items-center gap-2 rounded-lg border bg-card px-3 py-2">
            <ProjectStatusBadge status={s.status} />
            <span className="font-semibold tabular-nums">{formatCount(s.projects)}</span>
          </div>
        ))}
      </section>
      <Panel title="Project progress" icon={ClipboardList} description={`As of ${dayLabel(report.today)}`}>
        {report.projects.length === 0 ? (
          <p className="px-5 py-4 text-sm text-muted-foreground">No projects match these filters.</p>
        ) : (
          <Table aria-label="Project progress">
            <TableHeader>
              <TableRow>
                <TableHead>Project</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="min-w-40">Progress</TableHead>
                <TableHead className="text-right">Tasks</TableHead>
                <TableHead className="text-right">Milestones</TableHead>
                <TableHead className="text-right">Open risks</TableHead>
                <TableHead>End date</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {report.projects.map((p) => (
                <TableRow key={p.id}>
                  <TableCell className="max-w-64">
                    <p className="truncate font-medium">{p.name}</p>
                    <p className="truncate text-xs text-muted-foreground">
                      <span className="font-mono">{p.code}</span>
                      {p.department && ` · ${p.department.name}`}
                    </p>
                  </TableCell>
                  <TableCell>
                    <ProjectStatusBadge status={p.status} />
                  </TableCell>
                  <TableCell>
                    <div className="h-2 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label={`${p.name} progress`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={p.progress ?? 0}>
                      <div className="h-full rounded-full bg-primary" style={{ width: `${p.progress ?? 0}%` }} />
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground tabular-nums">{p.progress === null ? '—' : `${p.progress}%`}</p>
                  </TableCell>
                  <TableCell className="text-right text-sm tabular-nums">
                    {formatCount(p.tasksCompleted)} / {formatCount(p.tasks)}
                    {p.tasksOverdue > 0 && <p className="text-xs text-status-danger">{formatCount(p.tasksOverdue)} overdue</p>}
                  </TableCell>
                  <TableCell className="text-right text-sm tabular-nums">
                    {formatCount(p.milestonesCompleted)} / {formatCount(p.milestones)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(p.openRisks)}</TableCell>
                  <TableCell className="text-sm whitespace-nowrap">
                    {dayLabel(p.endDate)}
                    {p.pastEndDate && (
                      <Badge tone="danger" className="ml-2">
                        <AlarmClock aria-hidden />
                        Past end date
                      </Badge>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Panel>
    </div>
  );
}
