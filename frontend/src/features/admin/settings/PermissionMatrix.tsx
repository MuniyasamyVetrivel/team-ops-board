import { Check, LoaderCircle, Lock, ShieldCheck, UserRound } from 'lucide-react';
import { Fragment, useMemo, useState } from 'react';
import { toast } from 'sonner';

import { ErrorState } from '@/components/common/ErrorState';
import { FormBanner } from '@/components/common/FormField';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { isSuperAdmin, type PermissionCode, type RoleCode } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { groupByModule } from '@/features/admin/users/access';
import { usePermissions, useRoles, useUpdateRolePermissions, type PermissionResponse, type RoleResponse } from '@/features/admin/users/api';
import { errorMessage, toApiError } from '@/lib/api/errors';

import { MARKETING_MODULE, permissionDiff, toggleRolePermission } from './role-matrix';

/** Roles whose permissions the matrix edits; the Super Admin role always holds everything. */
const EDITABLE_ROLES: RoleCode[] = ['DEPARTMENT_MANAGER', 'EMPLOYEE'];

const MODULE_LABELS: Record<string, string> = {
  DASHBOARD: 'Dashboard',
  TASK: 'Tasks',
  WORKLOAD: 'Workload',
  PROJECT: 'Projects',
  TICKET: 'Help desk',
  APPROVAL: 'Approvals',
  COLLABORATION: 'Collaboration',
  CALENDAR: 'Calendar',
  REPORT: 'Reports',
  ADMIN: 'Administration',
  MARKETING: 'Digital Marketing',
};

type Edits = Partial<Record<RoleCode, Set<PermissionCode>>>;

/**
 * Which permissions each role grants. Only a Super Admin edits it (the server enforces the same rules): the Super
 * Admin role is locked, Digital Marketing permissions are granted per person, and actions bring their view permission.
 */
export function PermissionMatrix() {
  const { user } = useAuth();
  const canEdit = isSuperAdmin(user);
  const roles = useRoles();
  const permissions = usePermissions();
  const [edits, setEdits] = useState<Edits>({});
  const [confirming, setConfirming] = useState<RoleResponse | null>(null);

  const groups = useMemo(() => groupByModule(permissions.data ?? []), [permissions.data]);
  const roleList = useMemo(() => {
    const order: RoleCode[] = ['SUPER_ADMIN', 'DEPARTMENT_MANAGER', 'EMPLOYEE'];
    return [...(roles.data ?? [])].sort((a, b) => order.indexOf(a.code) - order.indexOf(b.code));
  }, [roles.data]);

  if (roles.isPending || permissions.isPending) {
    return (
      <div className="space-y-2" role="status" aria-label="Loading permissions">
        {Array.from({ length: 8 }, (_, i) => (
          <Skeleton key={i} className="h-10 rounded-md" />
        ))}
      </div>
    );
  }
  if (roles.isError || permissions.isError) {
    return (
      <ErrorState
        error={roles.error ?? permissions.error}
        title="Couldn't load roles and permissions"
        onRetry={() => {
          void roles.refetch();
          void permissions.refetch();
        }}
      />
    );
  }

  const current = (role: RoleResponse): Set<PermissionCode> => edits[role.code] ?? new Set(role.permissions);
  const toggle = (role: RoleResponse, code: PermissionCode, checked: boolean) =>
    setEdits((prev) => ({ ...prev, [role.code]: toggleRolePermission(current(role), code, checked) }));
  const discard = (role: RoleResponse) =>
    setEdits((prev) => {
      const next = { ...prev };
      delete next[role.code];
      return next;
    });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start gap-3 rounded-lg border bg-muted/40 px-4 py-3 text-sm">
        <ShieldCheck className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
        <div className="space-y-1 text-muted-foreground">
          <p>
            A role&apos;s permissions apply to everyone holding the role, straight away. Extra permissions for one person are granted on their profile under{' '}
            <span className="font-medium text-foreground">Users</span>.
          </p>
          <p>Digital Marketing permissions are always granted per person, so the module stays limited to the people who need it.</p>
          {!canEdit && <p className="font-medium text-foreground">Only a Super Admin can change role permissions.</p>}
        </div>
      </div>

      {canEdit && (
        <div className="flex flex-wrap gap-3">
          {roleList
            .filter((role) => EDITABLE_ROLES.includes(role.code))
            .map((role) => {
              const diff = permissionDiff(role.permissions, current(role));
              const dirty = diff.added.length + diff.removed.length > 0;
              if (!dirty) return null;
              return (
                <Card key={role.code} className="flex flex-wrap items-center gap-3 px-4 py-2.5" role="status" aria-label={`${role.name} unsaved changes`}>
                  <span className="text-sm">
                    <span className="font-medium">{role.name}</span>: {diff.added.length > 0 && <span className="text-status-success">+{diff.added.length} added</span>}
                    {diff.added.length > 0 && diff.removed.length > 0 && ', '}
                    {diff.removed.length > 0 && <span className="text-status-danger">−{diff.removed.length} removed</span>}
                  </span>
                  <Button size="sm" variant="ghost" onClick={() => discard(role)}>
                    Discard
                  </Button>
                  <Button size="sm" onClick={() => setConfirming(role)}>
                    Review and save
                  </Button>
                </Card>
              );
            })}
        </div>
      )}

      <Card className="overflow-hidden">
        <Table aria-label="Role permissions">
          <TableHeader>
            <TableRow>
              <TableHead className="w-full">Permission</TableHead>
              {roleList.map((role) => (
                <TableHead key={role.code} className="text-center whitespace-nowrap">
                  {role.name}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {groups.map(([module, items]) => (
              <Fragment key={module}>
                <TableRow className="bg-muted/50 hover:bg-muted/50">
                  <TableCell colSpan={roleList.length + 1} className="py-2 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                    {MODULE_LABELS[module] ?? module}
                    {module === MARKETING_MODULE && <span className="ml-2 font-normal tracking-normal normal-case">granted per person</span>}
                  </TableCell>
                </TableRow>
                {items.map((permission) => (
                  <TableRow key={permission.code}>
                    <TableCell>
                      <p className="text-sm">{permission.name}</p>
                      <p className="font-mono text-[11px] text-muted-foreground">{permission.code}</p>
                    </TableCell>
                    {roleList.map((role) => (
                      <TableCell key={role.code} className="text-center">
                        <MatrixCell role={role} permission={permission} checked={current(role).has(permission.code)} canEdit={canEdit} onToggle={toggle} />
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </Fragment>
            ))}
          </TableBody>
        </Table>
      </Card>

      <ConfirmDialog
        role={confirming}
        edited={confirming ? current(confirming) : new Set()}
        permissions={permissions.data}
        onClose={() => setConfirming(null)}
        onSaved={(role) => {
          discard(role);
          setConfirming(null);
        }}
        onStale={() => void roles.refetch()}
      />
    </div>
  );
}

function MatrixCell({
  role,
  permission,
  checked,
  canEdit,
  onToggle,
}: {
  role: RoleResponse;
  permission: PermissionResponse;
  checked: boolean;
  canEdit: boolean;
  onToggle: (role: RoleResponse, code: PermissionCode, checked: boolean) => void;
}) {
  const label = `${role.name}: ${permission.name}`;
  if (role.code === 'SUPER_ADMIN') {
    return (
      <span className="inline-flex items-center gap-1 text-xs text-muted-foreground" title="The Super Admin role always holds every permission">
        <Lock className="size-3.5" aria-hidden />
        <span className="sr-only">{label}: always granted</span>
        <Check className="size-4 text-status-success" aria-hidden />
      </span>
    );
  }
  if (permission.module === MARKETING_MODULE) {
    return (
      <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
        <UserRound className="size-3.5" aria-hidden />
        Per person
        <span className="sr-only">{label}: granted per person</span>
      </span>
    );
  }
  return (
    <Checkbox
      aria-label={label}
      checked={checked}
      disabled={!canEdit}
      onChange={(event) => onToggle(role, permission.code, event.target.checked)}
    />
  );
}

function ConfirmDialog({
  role,
  edited,
  permissions,
  onClose,
  onSaved,
  onStale,
}: {
  role: RoleResponse | null;
  edited: Set<PermissionCode>;
  permissions: PermissionResponse[];
  onClose: () => void;
  onSaved: (role: RoleResponse) => void;
  onStale: () => void;
}) {
  const update = useUpdateRolePermissions();
  const [banner, setBanner] = useState<string | null>(null);
  const names = useMemo(() => new Map(permissions.map((p) => [p.code, p.name])), [permissions]);
  const diff = role ? permissionDiff(role.permissions, edited) : { added: [], removed: [] };

  const save = async () => {
    if (!role) return;
    setBanner(null);
    try {
      await update.mutateAsync({ code: role.code, version: role.version, permissions: [...edited].sort() });
      toast.success(`${role.name} permissions saved`);
      onSaved(role);
    } catch (error) {
      setBanner(errorMessage(error));
      if (toApiError(error)?.code === 'STALE_UPDATE') onStale();
    }
  };

  return (
    <Dialog
      open={role !== null}
      onOpenChange={(open) => {
        if (!open) {
          setBanner(null);
          onClose();
        }
      }}
    >
      <DialogContent className="max-w-lg">
        {role && (
          <>
            <DialogHeader>
              <DialogTitle>Save {role.name} permissions?</DialogTitle>
              <DialogDescription>Everyone with the {role.name} role gets these changes as soon as you save.</DialogDescription>
            </DialogHeader>
            <DialogBody className="space-y-4">
              <FormBanner message={banner} />
              {diff.added.length > 0 && (
                <div>
                  <p className="mb-1.5 text-sm font-medium">Added</p>
                  <ul className="flex flex-wrap gap-1.5" aria-label="Added permissions">
                    {diff.added.map((code) => (
                      <li key={code}>
                        <Badge tone="success">+ {names.get(code) ?? code}</Badge>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {diff.removed.length > 0 && (
                <div>
                  <p className="mb-1.5 text-sm font-medium">Removed</p>
                  <ul className="flex flex-wrap gap-1.5" aria-label="Removed permissions">
                    {diff.removed.map((code) => (
                      <li key={code}>
                        <Badge tone="danger">− {names.get(code) ?? code}</Badge>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </DialogBody>
            <DialogFooter>
              <Button variant="outline" onClick={onClose}>
                Cancel
              </Button>
              <Button onClick={() => void save()} disabled={update.isPending}>
                {update.isPending && <LoaderCircle className="animate-spin" aria-hidden />}
                Save permissions
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
