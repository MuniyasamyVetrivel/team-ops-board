import { Activity, ArrowLeft, CircleCheck, Clock, Gauge, ListTodo, Lock, Mail, MapPin, Phone, Ticket, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link, useParams } from 'react-router';

import { ErrorState } from '@/components/common/ErrorState';
import { StatusBadge } from '@/components/common/StatusBadge';
import { UserAvatar, UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { RoleBadges } from '@/features/admin/users/RoleBadges';
import { hasPermission, type RoleCode } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useTasks } from '@/features/tasks/api';
import { DueBadge, TaskStatusBadge } from '@/features/tasks/TaskBadges';
import { ACTIVE_STATUSES } from '@/features/tasks/types';
import { useWorkload } from '@/features/workload/api';
import { WorkloadMeter } from '@/features/workload/WorkloadMeter';
import { formatDate } from '@/lib/format';
import { cn } from '@/lib/utils';

import { useTeamProfile } from './api';

/** Work sections still to come. */
const LATER_SECTIONS: { title: string; icon: LucideIcon; phase: number }[] = [
  { title: 'Recent activity', icon: Activity, phase: 6 },
  { title: 'Tickets', icon: Ticket, phase: 7 },
];

/** Workload and current tasks; only rendered when the server says the viewer may see this person's work. */
function MemberWork({ userId, firstName }: { userId: number; firstName: string }) {
  const workload = useWorkload({ userId });
  const tasks = useTasks({ assigneeId: userId, status: ACTIVE_STATUSES, sort: 'due,asc', size: 6 });
  const row = workload.data?.rows[0];

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Gauge className="size-4 text-muted-foreground" aria-hidden />
            Workload
          </CardTitle>
        </CardHeader>
        <CardContent>
          {workload.isPending ? (
            <Skeleton className="h-16" />
          ) : workload.isError ? (
            <ErrorState error={workload.error} onRetry={() => void workload.refetch()} />
          ) : !row ? (
            <p className="text-sm text-muted-foreground">No workload data.</p>
          ) : (
            <div className="space-y-4">
              <WorkloadMeter percent={row.workloadPercent} level={row.level} />
              <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                <Stat label="Open tasks" value={row.activeTasks} />
                <Stat label="Overdue" value={row.overdue} danger={row.overdue > 0} />
                <Stat label="Due today" value={row.dueToday} />
                <Stat label="Completed (30 days)" value={row.completed} />
              </dl>
              <p className="text-xs text-muted-foreground">
                {row.remainingHours} h of work against {row.capacityHours} h of capacity in the next {workload.data.windowDays} days.
              </p>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex-row items-center justify-between">
          <CardTitle className="flex items-center gap-2 text-base">
            <ListTodo className="size-4 text-muted-foreground" aria-hidden />
            Current tasks
          </CardTitle>
          {tasks.data && tasks.data.totalElements > tasks.data.content.length && (
            <span className="text-xs text-muted-foreground">
              Showing {tasks.data.content.length} of {tasks.data.totalElements}
            </span>
          )}
        </CardHeader>
        <CardContent>
          {tasks.isPending ? (
            <Skeleton className="h-24" />
          ) : tasks.isError ? (
            <ErrorState error={tasks.error} onRetry={() => void tasks.refetch()} />
          ) : tasks.data.content.length === 0 ? (
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <CircleCheck className="size-4 text-status-success" aria-hidden />
              {firstName} has no open tasks.
            </p>
          ) : (
            <ul className="divide-y">
              {tasks.data.content.map((task) => (
                <li key={task.id}>
                  <Link to={`/tasks?task=${task.id}`} className="flex items-center gap-3 py-2.5 hover:bg-muted/40">
                    <span className="font-mono text-xs text-muted-foreground">{task.code}</span>
                    <span className="min-w-0 flex-1 truncate text-sm">{task.title}</span>
                    <TaskStatusBadge status={task.status} />
                    <DueBadge dueDate={task.dueDate} state={task.dueState} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <div className="grid gap-4 sm:grid-cols-2">
        {LATER_SECTIONS.map(({ title, icon: Icon, phase }) => (
          <Card key={title} className="border-dashed">
            <CardContent className="flex items-center gap-3 p-4">
              <span className="flex size-9 items-center justify-center rounded-lg bg-accent text-accent-foreground">
                <Icon className="size-4" aria-hidden />
              </span>
              <div>
                <p className="text-sm font-medium">{title}</p>
                <p className="text-xs text-muted-foreground">Arrives in Phase {phase}</p>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}

function Stat({ label, value, danger = false }: { label: string; value: number; danger?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className={cn('text-xl font-semibold tabular-nums', danger && 'text-status-danger')}>{value}</dd>
    </div>
  );
}

export default function TeamProfilePage() {
  const { id } = useParams();
  const { user: viewer } = useAuth();
  const profile = useTeamProfile(Number(id));

  const back = (
    <Button asChild variant="ghost" size="sm" className="-ml-2">
      <Link to="/team">
        <ArrowLeft aria-hidden />
        Team
      </Link>
    </Button>
  );

  if (profile.isPending) {
    return (
      <div className="space-y-6" role="status" aria-label="Loading profile">
        {back}
        <Skeleton className="h-32 rounded-xl" />
        <div className="grid gap-6 lg:grid-cols-3">
          <Skeleton className="h-56 rounded-xl" />
          <Skeleton className="h-56 rounded-xl lg:col-span-2" />
        </div>
      </div>
    );
  }
  if (profile.isError) {
    return (
      <div className="space-y-6">
        {back}
        <Card>
          <ErrorState error={profile.error} onRetry={() => void profile.refetch()} title="Couldn't load this profile" />
        </Card>
      </div>
    );
  }

  const { member, roles, memberSince, directReports, canViewWork } = profile.data;
  const isSelf = viewer?.id === member.id;

  return (
    <div className="space-y-6">
      {back}

      <Card>
        <div className="flex flex-col gap-5 p-6 sm:flex-row sm:items-center">
          <UserAvatar name={member.fullName} size="lg" />
          <div className="min-w-0 flex-1 space-y-1.5">
            <h1 className="text-xl font-semibold tracking-tight sm:text-2xl">
              {member.fullName}
              {isSelf && <span className="ml-2 text-sm font-normal text-muted-foreground">(you)</span>}
            </h1>
            <p className="text-muted-foreground">
              {member.jobTitle ?? 'No job title'} · {member.department.name}
            </p>
            <div className="flex flex-wrap items-center gap-1.5">
              <StatusBadge status={member.status} />
              <RoleBadges roles={roles as RoleCode[]} />
            </div>
          </div>
          {hasPermission(viewer, 'USER_MANAGE') && (
            <Button asChild variant="outline">
              <Link to="/admin/users">Manage users</Link>
            </Button>
          )}
        </div>
      </Card>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Details</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="space-y-3 text-sm">
              <Detail icon={Mail} label="Email">
                <a href={`mailto:${member.email}`} className="text-primary hover:underline">
                  {member.email}
                </a>
              </Detail>
              <Detail icon={Phone} label="Phone">{member.phone ?? '—'}</Detail>
              <Detail icon={MapPin} label="Location">{member.location ?? '—'}</Detail>
              <Detail icon={Clock} label="Working hours">{member.workingHours ?? '—'}</Detail>
            </dl>
            <div className="mt-5 space-y-3 border-t pt-4 text-sm">
              <div>
                <p className="mb-1.5 text-xs font-medium text-muted-foreground uppercase">Reports to</p>
                {member.manager ? (
                  <Link to={`/team/${member.manager.id}`} className="block rounded-md hover:underline">
                    <UserCell name={member.manager.fullName} detail={member.manager.jobTitle} />
                  </Link>
                ) : (
                  <p className="text-muted-foreground">Nobody</p>
                )}
              </div>
              <p className="text-xs text-muted-foreground">Member since {formatDate(memberSince)}</p>
            </div>
          </CardContent>
        </Card>

        <div className="space-y-6 lg:col-span-2">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Direct reports ({directReports.length})</CardTitle>
            </CardHeader>
            <CardContent>
              {directReports.length === 0 ? (
                <p className="text-sm text-muted-foreground">No one reports to {member.firstName}.</p>
              ) : (
                <ul className="grid gap-3 sm:grid-cols-2">
                  {directReports.map((report) => (
                    <li key={report.id}>
                      <Link to={`/team/${report.id}`} className="block rounded-lg border p-3 hover:bg-muted/40">
                        <UserCell name={report.fullName} detail={report.jobTitle} />
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>

          {canViewWork ? (
            <MemberWork userId={member.id} firstName={member.firstName} />
          ) : (
            <Card>
              <CardContent className="flex items-center gap-3 p-4 text-sm text-muted-foreground">
                <Lock className="size-4 shrink-0" aria-hidden />
                Tasks, workload and tickets are visible to {member.firstName}, their department manager and administrators.
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}

function Detail({ icon: Icon, label, children }: { icon: LucideIcon; label: string; children: ReactNode }) {
  return (
    <div className="flex items-start gap-3">
      <Icon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
      <div className="min-w-0">
        <dt className="text-xs text-muted-foreground">{label}</dt>
        <dd className="truncate">{children}</dd>
      </div>
    </div>
  );
}
