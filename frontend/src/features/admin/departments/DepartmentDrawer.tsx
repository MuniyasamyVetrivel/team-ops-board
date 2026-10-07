import { LoaderCircle, Pencil, Trash2, UserPlus } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { toast } from 'sonner';

import { ErrorState } from '@/components/common/ErrorState';
import { StatusBadge } from '@/components/common/StatusBadge';
import { UserCell } from '@/components/common/UserAvatar';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import {
  useDepartment,
  useRemoveMember,
  useUpsertMember,
  type DepartmentDetail,
  type DepartmentMemberItem,
  type MemberRole,
} from '@/features/departments/api';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useTeamDirectory } from '@/features/team/api';
import { errorMessage } from '@/lib/api/errors';

import { DepartmentFormDialog } from './DepartmentFormDialog';

const MEMBERSHIP_LABELS: Record<DepartmentMemberItem['membership'], string> = {
  PRIMARY: 'Primary',
  MANAGER: 'Co-manager',
  MEMBER: 'Secondary member',
};

export function DepartmentDrawer({ departmentId, onClose }: { departmentId: number | null; onClose: () => void }) {
  const query = useDepartment(departmentId);
  return (
    <Dialog open={departmentId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent aria-describedby={undefined}>
        {query.isPending ? (
          <div className="space-y-4 p-6">
            <DialogTitle className="sr-only">Loading department</DialogTitle>
            <Skeleton className="h-10 w-56" />
            <Skeleton className="h-72" />
          </div>
        ) : query.isError ? (
          <div className="p-6">
            <DialogTitle className="sr-only">Error</DialogTitle>
            <ErrorState error={query.error} onRetry={() => void query.refetch()} />
          </div>
        ) : (
          <DepartmentBody department={query.data} />
        )}
      </SheetContent>
    </Dialog>
  );
}

function DepartmentBody({ department }: { department: DepartmentDetail }) {
  const [editing, setEditing] = useState(false);
  const primary = department.members.filter((m) => m.membership === 'PRIMARY');
  const secondary = department.members.filter((m) => m.membership !== 'PRIMARY');

  return (
    <>
      <div className="space-y-2 border-b px-6 py-5 pr-12">
        <div className="flex items-center gap-2">
          <DialogTitle className="text-lg">{department.name}</DialogTitle>
          <Badge>{department.code}</Badge>
        </div>
        <DialogDescription>{department.description ?? 'No description'}</DialogDescription>
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <StatusBadge status={department.status} />
          <span className="text-muted-foreground">
            Manager: <span className="font-medium text-foreground">{department.manager?.fullName ?? 'Not assigned'}</span>
          </span>
          {department.canEdit && (
            <Button variant="outline" size="sm" className="ml-auto" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit
            </Button>
          )}
        </div>
      </div>

      <div className="flex-1 space-y-6 overflow-y-auto px-6 py-5">
        <MemberList title={`People (${primary.length})`} members={primary} department={department} empty="Nobody has this as their primary department yet." />
        <MemberList
          title={`Cross-department members (${secondary.length})`}
          members={secondary}
          department={department}
          empty="People from other departments who also work here."
        />
        {department.canManageMembers && department.status === 'ACTIVE' && <AddMember department={department} />}
      </div>

      <DepartmentFormDialog open={editing} onOpenChange={setEditing} department={department} />
    </>
  );
}

function MemberList({ title, members, department, empty }: { title: string; members: DepartmentMemberItem[]; department: DepartmentDetail; empty: string }) {
  const { user } = useAuth();
  const remove = useRemoveMember(department.id);
  const canRemove = (member: DepartmentMemberItem) =>
    department.canManageMembers && member.membership !== 'PRIMARY' && (member.membership === 'MEMBER' || hasPermission(user, 'DEPARTMENT_MANAGE'));

  async function onRemove(member: DepartmentMemberItem) {
    try {
      await remove.mutateAsync(member.user.id);
      toast.success(`${member.user.fullName} was removed from ${department.name}`);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <section>
      <h3 className="mb-2 text-sm font-semibold">{title}</h3>
      {members.length === 0 ? (
        <p className="rounded-lg border border-dashed px-3 py-4 text-center text-sm text-muted-foreground">{empty}</p>
      ) : (
        <ul className="divide-y rounded-lg border">
          {members.map((member) => (
            <li key={`${member.membership}-${member.user.id}`} className="flex items-center gap-3 px-3 py-2.5">
              <Link to={`/team/${member.user.id}`} className="min-w-0 flex-1 rounded-md hover:underline">
                <UserCell
                  name={member.user.fullName}
                  detail={member.membership === 'PRIMARY' ? member.user.jobTitle : `${member.primaryDepartment.name} · ${member.user.jobTitle ?? ''}`}
                />
              </Link>
              {member.user.status !== 'ACTIVE' && <StatusBadge status={member.user.status} />}
              {member.membership !== 'PRIMARY' && <Badge tone={member.membership === 'MANAGER' ? 'primary' : 'neutral'}>{MEMBERSHIP_LABELS[member.membership]}</Badge>}
              {department.manager?.id === member.user.id && <Badge tone="primary">Manager</Badge>}
              {canRemove(member) && (
                <Button variant="ghost" size="icon" className="size-8" disabled={remove.isPending} onClick={() => void onRemove(member)} aria-label={`Remove ${member.user.fullName}`}>
                  <Trash2 />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function AddMember({ department }: { department: DepartmentDetail }) {
  const { user } = useAuth();
  const people = useTeamDirectory({ size: 100, sort: 'name,asc' });
  const upsert = useUpsertMember(department.id);
  const [userId, setUserId] = useState('');
  const [role, setRole] = useState<MemberRole>('MEMBER');
  const canAssignManager = hasPermission(user, 'DEPARTMENT_MANAGE');

  const existing = new Set(department.members.map((m) => m.user.id));
  const candidates = (people.data?.content ?? []).filter((p) => !existing.has(p.id) && p.department.id !== department.id);

  async function add() {
    try {
      const updated = await upsert.mutateAsync({ userId: Number(userId), role });
      const added = updated.members.find((m) => m.user.id === Number(userId));
      toast.success(`${added?.user.fullName ?? 'Member'} was added to ${department.name}`);
      setUserId('');
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <section className="rounded-lg border bg-muted/30 p-4">
      <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold">
        <UserPlus className="size-4" aria-hidden />
        Add someone from another department
      </h3>
      <div className="grid gap-2 sm:grid-cols-[1fr_11rem_auto]">
        <Select aria-label="Person" value={userId} onChange={(event) => setUserId(event.target.value)}>
          <option value="">Choose a person…</option>
          {candidates.map((person) => (
            <option key={person.id} value={person.id}>
              {person.fullName} — {person.department.name}
            </option>
          ))}
        </Select>
        <Select aria-label="Membership" value={role} onChange={(event) => setRole(event.target.value as MemberRole)}>
          <option value="MEMBER">Member</option>
          {canAssignManager && <option value="MANAGER">Co-manager</option>}
        </Select>
        <Button disabled={!userId || upsert.isPending} onClick={() => void add()}>
          {upsert.isPending && <LoaderCircle className="animate-spin" aria-hidden />}
          Add
        </Button>
      </div>
      <p className="mt-2 text-xs text-muted-foreground">Co-managers can see and manage this department's work. Only administrators can assign them.</p>
    </section>
  );
}
