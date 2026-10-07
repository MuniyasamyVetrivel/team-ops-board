import { Construction, ShieldCheck } from 'lucide-react';

import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { visibleNavigation } from '@/config/navigation';
import { primaryRoleLabel } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';

function greeting(date: Date): string {
  const hour = date.getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}

const dateFormat = new Intl.DateTimeFormat(undefined, {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

export default function DashboardPage() {
  const { user } = useAuth();
  if (!user) return null;

  const now = new Date();
  const modules = visibleNavigation(user).filter((section) => section.label);

  return (
    <div className="space-y-6">
      <PageHeader title={`${greeting(now)}, ${user.firstName}`} description={dateFormat.format(now)} />

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <EmptyState
            icon={Construction}
            title="Your dashboard is on its way"
            description="KPI cards, team and department workload, overdue tasks, upcoming deadlines and the Digital Marketing summary arrive in Phase 6."
          />
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <ShieldCheck className="size-4 text-status-success" aria-hidden />
              Your access
            </CardTitle>
            <CardDescription>Computed by the server from your role and grants.</CardDescription>
          </CardHeader>
          <CardContent>
            <dl className="space-y-3 text-sm">
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Role</dt>
                <dd className="font-medium">{primaryRoleLabel(user)}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Department</dt>
                <dd className="font-medium">{user.department.name}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Permissions</dt>
                <dd className="font-medium tabular-nums">{user.permissions.length}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Modules</dt>
                <dd className="mt-2 flex flex-wrap gap-1.5">
                  {modules.map((section) => (
                    <span key={section.id} className="rounded-md bg-muted px-2 py-0.5 text-xs font-medium">
                      {section.label}
                    </span>
                  ))}
                </dd>
              </div>
            </dl>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
