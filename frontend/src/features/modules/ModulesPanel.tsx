import { CircleCheck, CircleMinus, Blocks } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { Panel } from '@/components/common/Panel';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { Skeleton } from '@/components/ui/skeleton';
import { useDepartments, type DepartmentListItem } from '@/features/departments/api';
import { errorMessage } from '@/lib/api/errors';

import { useModuleSettings, useSetModuleSwitch, type ModuleSettings } from './api';
import { moduleIcon } from './module-icons';

/**
 * Admin settings → Modules (MODULE_MANAGE, Super Admin only by default): switch each department tool on or off per
 * department. People see a tool when it is on for one of their departments and they hold its view permission.
 */
export function ModulesPanel() {
  const modules = useModuleSettings();
  const departments = useDepartments();

  if (modules.isPending || departments.isPending) {
    return (
      <div className="space-y-4" aria-busy="true" aria-label="Loading modules">
        {[0, 1].map((i) => (
          <Skeleton key={i} className="h-48 rounded-xl" />
        ))}
      </div>
    );
  }
  if (modules.isError) return <ErrorState error={modules.error} title="Couldn't load modules" onRetry={() => void modules.refetch()} />;
  if (departments.isError) {
    return <ErrorState error={departments.error} title="Couldn't load departments" onRetry={() => void departments.refetch()} />;
  }
  if (modules.data.length === 0) {
    return (
      <Card>
        <EmptyState
          icon={Blocks}
          title="No department tools yet"
          description="Department tools such as Bench Management, Vendors and Finance will appear here once they are added. You can then switch each one on for the departments that use it."
        />
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        A tool appears in the "Department tools" sidebar section for people in a department where it is on and who hold its view permission. Its
        permissions are Super Admin only until you grant them in Roles &amp; permissions or on a user.
      </p>
      {modules.data.map((module) => (
        <ModuleCard key={module.key} module={module} departments={departments.data} />
      ))}
    </div>
  );
}

function ModuleCard({ module, departments }: { module: ModuleSettings; departments: DepartmentListItem[] }) {
  const setSwitch = useSetModuleSwitch();
  const [pending, setPending] = useState<ReadonlySet<number>>(new Set());
  const enabledIds = new Set(module.departments.map((d) => d.id));
  // Inactive departments can't be switched on; one that is still on stays listed so it can be switched off.
  const rows = departments.filter((d) => d.status === 'ACTIVE' || enabledIds.has(d.id)).sort((a, b) => a.name.localeCompare(b.name));
  const onCount = rows.filter((d) => enabledIds.has(d.id)).length;

  const toggle = (department: DepartmentListItem, enabled: boolean) => {
    setPending((current) => new Set(current).add(department.id));
    setSwitch.mutate(
      { key: module.key, departmentId: department.id, enabled },
      {
        onSuccess: () => toast.success(`${module.name} switched ${enabled ? 'on' : 'off'} for ${department.name}`),
        onError: (error) => toast.error(errorMessage(error, `Couldn't change ${module.name} for ${department.name}`)),
        onSettled: () =>
          setPending((current) => {
            const next = new Set(current);
            next.delete(department.id);
            return next;
          }),
      },
    );
  };

  const Icon = moduleIcon(module.icon);
  return (
    <Panel
      title={module.name}
      icon={Icon}
      description={module.description || undefined}
      action={
        <Badge tone={onCount > 0 ? 'success' : 'neutral'}>
          {onCount > 0 ? <CircleCheck aria-hidden /> : <CircleMinus aria-hidden />}
          {onCount > 0 ? `On for ${onCount} of ${rows.length}` : 'Off everywhere'}
        </Badge>
      }
    >
      <div className="px-6 pt-3 text-xs text-muted-foreground">
        Permissions: <code className="font-mono">{module.viewPermission}</code> to open, <code className="font-mono">{module.editPermission}</code> to
        change
      </div>
      {rows.length === 0 ? (
        <p className="px-6 py-4 text-sm text-muted-foreground">There are no active departments.</p>
      ) : (
        <ul className="grid gap-x-6 px-6 py-3 sm:grid-cols-2 lg:grid-cols-3" aria-label={`${module.name} by department`}>
          {rows.map((department) => {
            const on = enabledIds.has(department.id);
            const busy = pending.has(department.id);
            const inactive = department.status !== 'ACTIVE';
            const id = `module-${module.key}-${department.id}`;
            return (
              <li key={department.id} className="flex min-h-11 items-center justify-between gap-3 border-b border-border/60 py-2 last:border-b-0">
                <label htmlFor={id} className="flex min-w-0 cursor-pointer items-center gap-3 text-sm">
                  <Checkbox
                    id={id}
                    checked={on}
                    disabled={busy || (inactive && !on)}
                    onChange={(event) => toggle(department, event.target.checked)}
                    aria-label={`${module.name} for ${department.name}`}
                  />
                  <span className="min-w-0 truncate">
                    {department.name}
                    <span className="ml-1.5 text-xs text-muted-foreground">{department.code}</span>
                  </span>
                </label>
                {inactive ? <Badge tone="warning">Inactive</Badge> : <Badge tone={on ? 'success' : 'neutral'}>{on ? 'On' : 'Off'}</Badge>}
              </li>
            );
          })}
        </ul>
      )}
    </Panel>
  );
}
