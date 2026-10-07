import { Info, LoaderCircle } from 'lucide-react';
import { useMemo, useState } from 'react';
import { toast } from 'sonner';

import { ErrorState } from '@/components/common/ErrorState';
import { FormBanner } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Skeleton } from '@/components/ui/skeleton';
import { hasPermission, isSuperAdmin, ROLE_LABELS, type PermissionCode, type RoleCode } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { errorMessage } from '@/lib/api/errors';

import { effectiveGrants, groupByModule, inheritedPermissions, MODULE_LABELS } from './access';
import { usePermissions, useRoles, useUpdateAccess, type UserDetail } from './api';

/**
 * Role + direct grants. Permissions included by the role are shown as checked and locked; the rest can be granted
 * individually. The server re-checks everything (no escalation, no self-edit, last Super Admin).
 */
export function AccessEditor({ user }: { user: UserDetail }) {
  const { user: actor } = useAuth();
  const roles = useRoles();
  const catalogue = usePermissions();
  const updateAccess = useUpdateAccess(user.id);

  const [role, setRole] = useState<RoleCode>(user.roles[0] ?? 'EMPLOYEE');
  const [grants, setGrants] = useState<PermissionCode[]>(user.directPermissions);
  const [banner, setBanner] = useState<string | null>(null);

  const isSelf = actor?.id === user.id;
  const canEdit = hasPermission(actor, 'PERMISSION_MANAGE') && !isSelf;
  const inherited = useMemo(
    () => inheritedPermissions(roles.data ?? [], [role], catalogue.data ?? []),
    [roles.data, role, catalogue.data],
  );

  if (roles.isPending || catalogue.isPending) {
    return (
      <div className="space-y-3">
        <Skeleton className="h-20" />
        <Skeleton className="h-60" />
      </div>
    );
  }
  if (roles.isError || catalogue.isError) {
    return <ErrorState error={roles.error ?? catalogue.error} onRetry={() => void Promise.all([roles.refetch(), catalogue.refetch()])} />;
  }

  const assignableRoles = roles.data.filter((r) => r.code !== 'SUPER_ADMIN' || isSuperAdmin(actor) || user.roles.includes('SUPER_ADMIN'));
  const nextGrants = effectiveGrants(grants, inherited);
  const dirty = role !== (user.roles[0] ?? 'EMPLOYEE') || nextGrants.join() !== [...user.directPermissions].sort().join();

  function toggle(code: PermissionCode, checked: boolean) {
    setGrants((current) => (checked ? [...current, code] : current.filter((c) => c !== code)));
  }

  async function save() {
    setBanner(null);
    try {
      const updated = await updateAccess.mutateAsync({ roles: [role], permissions: nextGrants });
      setGrants(updated.directPermissions);
      toast.success('Access updated. It applies on their next request.');
    } catch (error) {
      setBanner(errorMessage(error));
    }
  }

  return (
    <div className="space-y-6">
      {!canEdit && (
        <div className="flex gap-2 rounded-lg border bg-muted/50 px-3 py-2.5 text-sm text-muted-foreground">
          <Info className="mt-0.5 size-4 shrink-0" aria-hidden />
          {isSelf ? 'You cannot change your own access. Ask another administrator.' : 'You need the Permission Manage permission to change access.'}
        </div>
      )}
      <FormBanner message={banner} />

      <fieldset disabled={!canEdit} className="space-y-2">
        <legend className="mb-2 text-sm font-medium">Role</legend>
        <div className="grid gap-2 sm:grid-cols-3">
          {assignableRoles.map((option) => (
            <label
              key={option.code}
              className="flex cursor-pointer flex-col gap-1 rounded-lg border p-3 text-sm has-checked:border-primary has-checked:bg-accent has-disabled:cursor-not-allowed"
            >
              <span className="flex items-center gap-2 font-medium">
                <input type="radio" name="role" value={option.code} checked={role === option.code} onChange={() => setRole(option.code)} className="accent-primary" />
                {ROLE_LABELS[option.code]}
              </span>
              <span className="text-xs text-muted-foreground">{option.description}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <fieldset disabled={!canEdit || role === 'SUPER_ADMIN'}>
        <legend className="mb-1 text-sm font-medium">Permissions</legend>
        <p className="mb-3 text-xs text-muted-foreground">
          {role === 'SUPER_ADMIN' ? 'Super Admins have every permission.' : 'Locked items come from the role. Tick others to grant them directly.'}
        </p>
        <div className="space-y-4">
          {groupByModule(catalogue.data).map(([module, permissions]) => (
            <div key={module} className="rounded-lg border">
              <p className="border-b bg-muted/40 px-3 py-2 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                {MODULE_LABELS[module] ?? module}
              </p>
              <ul className="grid gap-x-4 gap-y-2 p-3 sm:grid-cols-2">
                {permissions.map((permission) => {
                  const fromRole = inherited.has(permission.code);
                  return (
                    <li key={permission.code}>
                      <label className="flex items-start gap-2 text-sm">
                        <Checkbox
                          className="mt-0.5"
                          checked={fromRole || grants.includes(permission.code)}
                          disabled={fromRole}
                          onChange={(event) => toggle(permission.code, event.target.checked)}
                        />
                        <span>
                          {permission.name}
                          {fromRole && <span className="ml-1.5 text-xs text-muted-foreground">(from role)</span>}
                        </span>
                      </label>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </div>
      </fieldset>

      {canEdit && (
        <div className="sticky bottom-0 -mx-6 flex justify-end gap-2 border-t bg-card px-6 py-4">
          <Button
            variant="outline"
            disabled={!dirty || updateAccess.isPending}
            onClick={() => {
              setRole(user.roles[0] ?? 'EMPLOYEE');
              setGrants(user.directPermissions);
            }}
          >
            Reset
          </Button>
          <Button disabled={!dirty || updateAccess.isPending} onClick={() => void save()}>
            {updateAccess.isPending && <LoaderCircle className="animate-spin" aria-hidden />}
            Save access
          </Button>
        </div>
      )}
    </div>
  );
}
