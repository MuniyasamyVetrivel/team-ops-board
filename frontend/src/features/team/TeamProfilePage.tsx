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
import { formatDate } from '@/lib/format';

import { useTeamProfile } from './api';

/** Work sections filled in by later phases. */
const WORK_SECTIONS: { title: string; icon: LucideIcon; phase: number }[] = [
  { title: 'Current tasks', icon: ListTodo, phase: 5 },
  { title: 'Completed tasks', icon: CircleCheck, phase: 5 },
  { title: 'Workload', icon: Gauge, phase: 5 },
  { title: 'Recent activity', icon: Activity, phase: 6 },
  { title: 'Tickets', icon: Ticket, phase: 7 },
];

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
            <div className="grid gap-4 sm:grid-cols-2">
              {WORK_SECTIONS.map(({ title, icon: Icon, phase }) => (
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
