import {
  AlarmClock,
  Building,
  CalendarClock,
  CalendarDays,
  CircleCheck,
  CircleDot,
  ClipboardList,
  Gauge,
  History,
  LifeBuoy,
  ChartColumn,
  ListTodo,
  OctagonX,
  PartyPopper,
  PieChart,
  Timer,
  Users,
  Workflow,
} from 'lucide-react';
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

import { useDashboard, type DashboardResponse } from './api';
import { DepartmentWorkloadChart, StatusDonut, WeeklyCompletionChart } from './charts';
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
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        {Array.from({ length: 8 }, (_, i) => (
          <Skeleton key={i} className="h-24 rounded-xl" />
        ))}
      </div>
      <div className="grid gap-6 lg:grid-cols-3">
        <Skeleton className="h-80 rounded-xl" />
        <Skeleton className="h-80 rounded-xl lg:col-span-2" />
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <Skeleton className="h-72 rounded-xl" />
        <Skeleton className="h-72 rounded-xl" />
      </div>
    </div>
  );
}

/**
 * Home dashboard (brief sections 8, 67, 68, 82). One page for every role: the server scopes the data, and team
 * sections appear only when the response is for more than the viewer's own work.
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
          <section aria-label="Key figures" className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <KpiCard label={personal ? 'My open tasks' : 'Open tasks'} value={data.kpis.openTasks} icon={ClipboardList} tone="text-primary" to={tasksPath} />
            <KpiCard label="Due today" value={data.kpis.dueToday} icon={CalendarClock} tone="text-status-warning" to={tasksPath} />
            <KpiCard label="Overdue" value={data.kpis.overdue} icon={AlarmClock} tone="text-status-danger" alert={data.kpis.overdue > 0} to={tasksPath} />
            <KpiCard label="Completed this week" value={data.kpis.completedThisWeek} icon={CircleCheck} tone="text-status-success" hint={`Since ${shortDate.format(parseLocalDate(data.weekStart))}`} />
            {personal ? (
              <>
                <KpiCard label="In progress" value={data.kpis.inProgress} icon={CircleDot} tone="text-primary" />
                <KpiCard label="Blocked" value={data.kpis.blocked} icon={OctagonX} tone="text-status-danger" />
                <KpiCard label="My open tickets" value={data.kpis.openTickets} icon={LifeBuoy} to="/my/tickets" />
                <KpiCard label="Pending approvals" value={data.kpis.pendingApprovals} icon={Workflow} hint="Available with approvals" />
              </>
            ) : (
              <>
                <KpiCard label="Open tickets" value={data.kpis.openTickets} icon={LifeBuoy} to={hasPermission(user, 'TICKET_VIEW') ? '/tickets' : undefined} />
                <KpiCard label="SLA breaches" value={data.kpis.slaBreaches} icon={Timer} tone="text-status-danger" alert={(data.kpis.slaBreaches ?? 0) > 0} to={hasPermission(user, 'TICKET_VIEW') ? '/sla' : undefined} />
                <KpiCard label="Pending approvals" value={data.kpis.pendingApprovals} icon={Workflow} hint="Available with approvals" />
                <KpiCard label="Team members" value={data.kpis.teamMembers} icon={Users} to={hasPermission(user, 'TEAM_VIEW') ? '/team' : undefined} />
              </>
            )}
          </section>

          <div className="grid gap-6 lg:grid-cols-3">
            <Panel title="Task status" icon={PieChart} description="Open work by status">
              <div className="p-5">
                <StatusDonut slices={data.statusDistribution} completedWindowDays={data.completedWindowDays} />
              </div>
            </Panel>
            <Panel title="Weekly task completion" icon={ChartColumn} description="Tasks completed per week, last 8 weeks" className="lg:col-span-2">
              <div className="p-5">
                <WeeklyCompletionChart weeks={data.weeklyCompletion} />
              </div>
            </Panel>
          </div>

          {personal ? (
            data.workload.rows[0] && (
              <Panel title="My workload" icon={Gauge} description={`Remaining hours against your capacity over the next ${data.workload.windowDays} days`}>
                <div className="p-5">
                  <WorkloadMeter percent={data.workload.rows[0].workloadPercent} level={data.workload.rows[0].level} />
                  <p className="mt-2 text-xs text-muted-foreground tabular-nums">
                    {data.workload.rows[0].remainingHours} h of {data.workload.rows[0].capacityHours} h
                  </p>
                </div>
              </Panel>
            )
          ) : (
            <div className="grid gap-6 lg:grid-cols-2">
              <Panel title="Department workload" icon={Building} description="Remaining hours as a share of each team's capacity">
                <div className="p-5">
                  <DepartmentWorkloadChart departments={data.departments} />
                </div>
              </Panel>
              <Panel
                title="Employee workload"
                icon={Gauge}
                description={`Busiest ${data.workload.rows.length} of ${data.workload.people} · average ${data.workload.averagePercent}%`}
                action={hasPermission(user, 'WORKLOAD_VIEW') && <PanelLink to="/workload">View all</PanelLink>}
              >
                <EmployeeWorkloadTable rows={data.workload.rows} onOpenPerson={hasPermission(user, 'TEAM_VIEW') ? (row) => void navigate(`/team/${row.user.id}`) : undefined} />
              </Panel>
            </div>
          )}

          <div className="grid gap-6 lg:grid-cols-2">
            <Panel title="Overdue tasks" icon={AlarmClock} description={`${data.kpis.overdue} overdue, oldest first`} action={<PanelLink to={tasksPath}>View tasks</PanelLink>}>
              <TaskList tasks={data.overdueTasks} empty="Nothing overdue" emptyIcon={PartyPopper} onOpen={(t) => setTaskId(t.id)} showAssignee={!personal} />
            </Panel>
            <Panel title="Upcoming deadlines" icon={CalendarClock} description="Due in the next 7 days">
              <TaskList tasks={data.upcomingTasks} empty="No deadlines this week" emptyIcon={ListTodo} onOpen={(t) => setTaskId(t.id)} showAssignee={!personal} />
            </Panel>
          </div>

          {!personal && data.departments.length > 0 && (
            <Panel title="Department performance" icon={Building} description={`Open and overdue now; completions and on-time rate over the last ${data.completedWindowDays} days`}>
              <DepartmentPerformanceTable departments={data.departments} completedWindowDays={data.completedWindowDays} />
            </Panel>
          )}

          <div className="grid gap-6 lg:grid-cols-2">
            <Panel title="Recent activity" icon={History}>
              <RecentActivity items={data.recentActivity} onOpenTask={setTaskId} />
            </Panel>
            {canSeeCalendar && (
              <Panel title="Coming up" icon={CalendarDays} description={`Events and leave in the next ${EVENTS_DAYS} days`}>
                {events.isPending ? (
                  <div className="space-y-2 p-5" role="status" aria-label="Loading events">
                    {Array.from({ length: 3 }, (_, i) => (
                      <Skeleton key={i} className="h-10" />
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
