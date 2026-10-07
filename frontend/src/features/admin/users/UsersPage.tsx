import { Plus, UsersRound } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { StatusBadge } from '@/components/common/StatusBadge';
import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ROLE_LABELS, type RoleCode } from '@/features/auth/permissions';
import { useDepartments } from '@/features/departments/api';
import type { UserStatus } from '@/lib/api/types';
import { formatRelative } from '@/lib/format';
import { useDebouncedValue } from '@/lib/use-debounced-value';

import { useUsers } from './api';
import { CreateUserDialog } from './CreateUserDialog';
import { RoleBadges } from './RoleBadges';
import { UserDrawer } from './UserDrawer';

const PAGE_SIZE = 20;

const SORTS = [
  { value: 'name,asc', label: 'Name A–Z' },
  { value: 'department,asc', label: 'Department' },
  { value: 'lastLogin,desc', label: 'Recently active' },
  { value: 'created,desc', label: 'Newest first' },
];

export default function UsersPage() {
  const departments = useDepartments();
  const [search, setSearch] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [role, setRole] = useState<RoleCode | ''>('');
  const [status, setStatus] = useState<UserStatus | ''>('');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);

  const debouncedSearch = useDebouncedValue(search.trim());
  const users = useUsers({
    search: debouncedSearch,
    departmentId: departmentId ? Number(departmentId) : undefined,
    role: role || undefined,
    status: status || undefined,
    sort,
    page,
    size: PAGE_SIZE,
  });

  /** Any filter change goes back to the first page. */
  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  const hasFilters = Boolean(search || departmentId || role || status);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Users"
        description="Create accounts, assign roles and departments, and control access."
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus aria-hidden />
            Add user
          </Button>
        }
      />

      <Card>
        <div className="grid gap-3 border-b p-4 sm:grid-cols-2 lg:grid-cols-[1fr_repeat(4,minmax(0,11rem))]">
          <SearchInput
            placeholder="Search name, email or job title"
            aria-label="Search users"
            value={search}
            onChange={(event) => filter(setSearch)(event.target.value)}
          />
          <Select aria-label="Department" value={departmentId} onChange={(event) => filter(setDepartmentId)(event.target.value)}>
            <option value="">All departments</option>
            {departments.data?.map((department) => (
              <option key={department.id} value={department.id}>
                {department.name}
              </option>
            ))}
          </Select>
          <Select aria-label="Role" value={role} onChange={(event) => filter(setRole)(event.target.value as RoleCode | '')}>
            <option value="">All roles</option>
            {(Object.keys(ROLE_LABELS) as RoleCode[]).map((code) => (
              <option key={code} value={code}>
                {ROLE_LABELS[code]}
              </option>
            ))}
          </Select>
          <Select aria-label="Status" value={status} onChange={(event) => filter(setStatus)(event.target.value as UserStatus | '')}>
            <option value="">Any status</option>
            <option value="ACTIVE">Active</option>
            <option value="DISABLED">Disabled</option>
          </Select>
          <Select aria-label="Sort" value={sort} onChange={(event) => filter(setSort)(event.target.value)}>
            {SORTS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>
        </div>

        {users.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading users">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : users.isError ? (
          <ErrorState error={users.error} onRetry={() => void users.refetch()} title="Couldn't load users" />
        ) : users.data.content.length === 0 ? (
          <EmptyState
            icon={UsersRound}
            title={hasFilters ? 'No users match these filters' : 'No users yet'}
            description={hasFilters ? 'Try a different search or clear the filters.' : 'Add the first team member to get started.'}
          />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>User</TableHead>
                  <TableHead>Department</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Last sign-in</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={users.isPlaceholderData ? 'opacity-60' : undefined}>
                {users.data.content.map((user) => (
                  <TableRow
                    key={user.id}
                    data-clickable="true"
                    tabIndex={0}
                    onClick={() => setSelectedId(user.id)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        setSelectedId(user.id);
                      }
                    }}
                    aria-label={`Open ${user.fullName}`}
                  >
                    <TableCell>
                      <UserCell name={user.fullName} detail={user.email} />
                    </TableCell>
                    <TableCell>
                      <p>{user.department.name}</p>
                      {user.jobTitle && <p className="text-xs text-muted-foreground">{user.jobTitle}</p>}
                    </TableCell>
                    <TableCell>
                      <RoleBadges roles={user.roles} />
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={user.status} />
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-muted-foreground">{formatRelative(user.lastLoginAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination {...users.data} onPageChange={setPage} />
          </>
        )}
      </Card>

      <CreateUserDialog open={creating} onOpenChange={setCreating} onCreated={(user) => setSelectedId(user.id)} />
      <UserDrawer userId={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  );
}
