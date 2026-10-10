import { AlarmClock, CalendarClock, CircleCheck, CircleDot, ClipboardList, LifeBuoy, ListTodo, OctagonX, PartyPopper, Timer, Users, Workflow } from 'lucide-react';
import { useNavigate } from 'react-router';

import { ErrorState } from '@/components/common/ErrorState';
import { KpiCard } from '@/components/common/KpiCard';
import { PageHeader } from '@/components/common/PageHeader';
import { Panel, PanelLink } from '@/components/common/Panel';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useCalendar } from '@/features/calendar/api';
import { TaskDrawer } from '@/features/tasks/TaskDrawer';
import { parseLocalDate } from '@/features/tasks/task-meta';
import { useTaskParam } from '@/features/tasks/use-task-param';
import { WorkloadMeter } from '@/features/workload/WorkloadMeter';
import { cn } from '@/lib/utils';

import { useDashboard, type DashboardResponse } from './api';
import { MarketingSummaryPanel } from './MarketingSummaryPanel';
import { DepartmentWorkloadChart, OnTimeTrendChart, StatusDonut, WeeklyCompletionChart } from './charts';
import { DepartmentPerformanceTable, EmployeeWorkloadTable, RecentActivity, TaskList, UpcomingEvents } from './sections';

function greeting(date: Date): string {
  const hour = date.getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}

const dateFormat = new Intl.DateTimeFormat(undefined, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
const shortDate = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short' });

const SCOPE_TEXT: Record<DashboardResponse['scope'], string> = {
  ALL: 'Company-wide view',
  DEPARTMENTS: 'Your departments',
  OWN: 'Your work',
};

const EVENTS_DAYS = 14;

function addDays(isoDate: string, days: number): string {
  const date = parseLocalDate(isoDate);
  date.setDate(date.getDate() + days);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6" role="status" aria-label="Loading dashboard">
      <div className="grid grid-cols-2 gap-4 sm:gap-6 xl:grid-cols-4">
        {Array.from({ length: 8 }, (_, i) => (
          <Skeleton key={i} className="h-36 rounded-xl" />
        ))}
      </div>
      <div className="grid grid-cols-12 gap-6">
        <Skeleton className="col-span-12 h-80 rounded-xl xl:col-span-8" />
        <Skeleton className="col-span-12 h-80 rounded-xl xl:col-span-4" />
        <Skeleton className="col-span-12 h-72 rounded-xl xl:col-span-8" />
        <Skeleton className="col-span-12 h-72 rounded-xl xl:col-span-4" />
      </div>
    </div>
  );
}

/** Small key beside a chart title; each colour is named, never relied on alone. */
function LegendKey({ items }: { items: { color: string; label: string }[] }) {
  return (
    <ul className="flex flex-wrap items-center gap-x-4 gap-y-1 text-label text-muted-foreground">
      {items.map((item) => (
        <li key={item.label} className="flex items-center gap-1.5 whitespace-nowrap">
          <span className="size-2.5 rounded-full" style={{ background: item.color }} aria-hidden />
          {item.label}
        </li>
      ))}
    </ul>
  );
}

/** This week's completions against last week's, when both weeks are in the series. */
function completedDelta(data: DashboardResponse): number | null {
  const weeks = data.weeklyCompletion;
  const current = weeks.findIndex((w) => w.weekStart === data.weekStart);
  if (current < 1) return null;
  return weeks[current]!.completed - weeks[current - 1]!.completed;
}

/** The most recent week with completions to rate (the current week may have none yet). */
function latestOnTime(data: DashboardResponse): number | null {
  for (let i = data.weeklyCompletion.length - 1; i >= 0; i--) {
    const week = data.weeklyCompletion[i]!;
    if (week.onTimePercent !== null) return week.onTimePercent;
  }
  return null;
}

/**
 * Home dashboard (brief sections 8, 67, 68, 82). One page for every role: the server scopes the data, and team
 * sections appear only when the response is for more than the viewer's own work. Laid out on a 12-column grid with
 * 24px gutters; cards in a row share a height.
 */
export default function DashboardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const dashboard = useDashboard();
  const [taskId, setTaskId] = useTaskParam();
  const canSeeCalendar = hasPermission(user, 'CALENDAR_VIEW');
  const today = dashboard.data?.today;
  const events = useCalendar({ from: today ?? '', to: today ? addDays(today, EVENTS_DAYS - 1) : '' }, canSeeCalendar && Boolean(today));
  if (!user) return null;

  const title = `${greeting(new Date())}, ${user.firstName}`;
  const data = dashboard.data;
  const personal = data?.scope === 'OWN';
  const tasksPath = personal ? '/my/tasks' : '/tasks';
  const onTime = data ? latestOnTime(data) : null;

  return (
    <div className="space-y-6">
      <PageHeader title={title} description={data ? `${dateFormat.format(parseLocalDate(data.today))} · ${SCOPE_TEXT[data.scope]}` : undefined} />

      {dashboard.isError ? (
        <Card>
          <ErrorState error={dashboard.error} title="Couldn't load the dashboard" onRetry={() => void dashboard.refetch()} />
        </Card>
      ) : !data ? (
        <DashboardSkeleton />
      ) : (
        <>
          <section aria-label="Key figures" className="grid grid-cols-2 gap-4 sm:gap-6 xl:grid-cols-4">
            <KpiCard
              label={personal ? 'My open tasks' : 'Open tasks'}
              value={data.kpis.openTasks}
              icon={ClipboardList}
              tone="text-primary"
              hint={`${data.kpis.inProgress} in progress · ${data.kpis.blocked} blocked`}
              to={tasksPath}
            />
            <KpiCard label="Due today" value={data.kpis.dueToday} icon={CalendarClock} tone="text-status-warning" to={tasksPath} />
            <KpiCard label="Overdue" value={data.kpis.overdue} icon={AlarmClock} tone="text-status-danger" alert={data.kpis.overdue > 0} hint="Past their due date" to={tasksPath} />
            <KpiCard
              label="Completed this week"
              value={data.kpis.completedThisWeek}
              icon={CircleCheck}
              tone="text-status-success"
              delta={{ value: completedDelta(data), label: 'vs last week' }}
              hint={`Since ${shortDate.format(parseLocalDate(data.weekStart))}`}
            />
            {personal ? (
              <>
                <KpiCard label="In progress" value={data.kpis.inProgress} icon={CircleDot} tone="text-primary" />
                <KpiCard label="Blocked" value={data.kpis.blocked} icon={OctagonX} tone="text-status-danger" />
                <KpiCard label="My open tickets" value={data.kpis.openTickets} icon={LifeBuoy} to="/my/tickets" />
                <KpiCard label="Pending approvals" value={data.kpis.pendingApprovals} icon={Workflow} tone="text-status-warning" to={hasPermission(user, 'APPROVAL_VIEW') ? '/approvals' : undefined} />
              </>
            ) : (
              <>
                <KpiCard label="Open tickets" value={data.kpis.openTickets} icon={LifeBuoy} to={hasPermission(user, 'TICKET_VIEW') ? '/tickets' : undefined} />
                <KpiCard label="SLA breaches" value={data.kpis.slaBreaches} icon={Timer} tone="text-status-danger" alert={(data.kpis.slaBreaches ?? 0) > 0} to={hasPermission(user, 'TICKET_VIEW') ? '/sla' : undefined} />
                <KpiCard label="Pending approvals" value={data.kpis.pendingApprovals} icon={Workflow} tone="text-status-warning" to={hasPermission(user, 'APPROVAL_VIEW') ? '/approvals' : undefined} />
                <KpiCard label="Team members" value={data.kpis.teamMembers} icon={Users} to={hasPermission(user, 'TEAM_VIEW') ? '/team' : undefined} />
              </>
            )}
          </section>

          <div className="grid grid-cols-12 gap-6">
            <Panel
              title="Weekly task completion"
              description={`Tasks completed per week, last ${data.weeklyCompletion.length} weeks`}
              action={
                <LegendKey
                  items={[
                    { color: 'var(--chart-1)', label: 'Previous weeks' },
                    { color: 'var(--chart-highlight)', label: 'This week' },
                  ]}
                />
              }
              className="col-span-12 xl:col-span-8"
            >
              <div className="px-6 pt-6 pb-4">
                <WeeklyCompletionChart weeks={data.weeklyCompletion} currentWeekStart={data.weekStart} />
              </div>
            </Panel>
            <Panel title="Task status" description={`Open work by status; completed in the last ${data.completedWindowDays} days`} className="col-span-12 xl:col-span-4">
              <div className="p-6">
                <StatusDonut slices={data.statusDistribution} />
              </div>
            </Panel>

            {personal ? (
              data.workload.rows[0] && (
                <Panel title="My workload" description={`Remaining hours against your capacity over the next ${data.workload.windowDays} days`} className="col-span-12 lg:col-span-6 xl:col-span-4">
                  <div className="p-6">
                    <WorkloadMeter percent={data.workload.rows[0].workloadPercent} level={data.workload.rows[0].level} />
                    <p className="mt-3 text-label text-muted-foreground tabular-nums">
                      {data.workload.rows[0].remainingHours} h of {data.workload.rows[0].capacityHours} h
                    </p>
                  </div>
                </Panel>
              )
            ) : (
              <>
                <Panel
                  title="Employee workload"
                  description={`Busiest ${data.workload.rows.length} of ${data.workload.people} · average ${data.workload.averagePercent}%`}
                  action={hasPermission(user, 'WORKLOAD_VIEW') && <PanelLink to="/workload">View all</PanelLink>}
                  className="col-span-12 xl:col-span-8"
                >
                  <EmployeeWorkloadTable rows={data.workload.rows} onOpenPerson={hasPermission(user, 'TEAM_VIEW') ? (row) => void navigate(`/team/${row.user.id}`) : undefined} />
                </Panel>
                <Panel title="Department workload" description="Remaining hours as a share of each team's capacity" className="col-span-12 xl:col-span-4">
                  <div className="px-6 pt-4 pb-6">
                    <DepartmentWorkloadChart departments={data.departments} />
                  </div>
                </Panel>
              </>
            )}

            <Panel
              title="On-time delivery"
              description={onTime === null ? 'Share of completions finished by their due date' : `Latest week ${onTime}% · completions finished by their due date`}
              className={cn('col-span-12', personal ? 'lg:col-span-6 xl:col-span-8' : 'xl:col-span-4')}
            >
              <div className="px-6 pt-6 pb-4">
                <OnTimeTrendChart weeks={data.weeklyCompletion} />
              </div>
            </Panel>
            <Panel
              title="Overdue tasks"
              description={`${data.kpis.overdue} overdue, oldest first`}
              action={<PanelLink to={tasksPath}>View tasks</PanelLink>}
              className={cn('col-span-12 lg:col-span-6', !personal && 'xl:col-span-4')}
            >
              <TaskList tasks={data.overdueTasks} empty="Nothing overdue" emptyIcon={PartyPopper} onOpen={(t) => setTaskId(t.id)} showAssignee={!personal} />
            </Panel>
            <Panel title="Upcoming deadlines" description="Due in the next 7 days" className={cn('col-span-12 lg:col-span-6', !personal && 'xl:col-span-4')}>
              <TaskList tasks={data.upcomingTasks} empty="No deadlines this week" emptyIcon={ListTodo} onOpen={(t) => setTaskId(t.id)} showAssignee={!personal} />
            </Panel>

            {!personal && data.departments.length > 0 && (
              <Panel
                title="Department performance"
                description={`Open and overdue now; completions and on-time rate over the last ${data.completedWindowDays} days`}
                className="col-span-12"
              >
                <DepartmentPerformanceTable departments={data.departments} completedWindowDays={data.completedWindowDays} />
              </Panel>
            )}

            {/* Brief section 8.9: the company-wide (Super Admin) view adds the Digital Marketing summary. */}
            {data.scope === 'ALL' && hasPermission(user, 'MARKETING_VIEW') && (
              <div className="col-span-12">
                <MarketingSummaryPanel />
              </div>
            )}

            <Panel title="Recent activity" className={cn('col-span-12', canSeeCalendar && 'xl:col-span-7')}>
              <RecentActivity items={data.recentActivity} onOpenTask={setTaskId} />
            </Panel>
            {canSeeCalendar && (
              <Panel title="Coming up" description={`Events and leave in the next ${EVENTS_DAYS} days`} className="col-span-12 xl:col-span-5">
                {events.isPending ? (
                  <div className="space-y-2 p-6" role="status" aria-label="Loading events">
                    {Array.from({ length: 3 }, (_, i) => (
                      <Skeleton key={i} className="h-12" />
                    ))}
                  </div>
                ) : events.isError ? (
                  <ErrorState error={events.error} title="Couldn't load events" onRetry={() => void events.refetch()} />
                ) : (
                  <UpcomingEvents items={events.data.items} />
                )}
              </Panel>
            )}
          </div>
        </>
      )}

      <TaskDrawer taskId={taskId} onClose={() => setTaskId(null)} />
    </div>
  );
}
