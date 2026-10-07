import { Users } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { StatusBadge } from '@/components/common/StatusBadge';
import { UserCell } from '@/components/common/UserAvatar';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useDepartments } from '@/features/departments/api';
import type { UserStatus } from '@/lib/api/types';
import { useDebouncedValue } from '@/lib/use-debounced-value';

import { useTeamDirectory } from './api';

const PAGE_SIZE = 24;

/** Team directory (brief section 18). */
export default function TeamPage() {
  const departments = useDepartments();
  const [search, setSearch] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [status, setStatus] = useState<UserStatus>('ACTIVE');
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebouncedValue(search.trim());

  const team = useTeamDirectory({
    search: debouncedSearch,
    departmentId: departmentId ? Number(departmentId) : undefined,
    status,
    page,
    size: PAGE_SIZE,
    sort: 'name,asc',
  });

  return (
    <div className="space-y-6">
      <PageHeader title="Team" description="Who's who: roles, departments, managers and how to reach them." />
      <Card>
        <div className="grid gap-3 border-b p-4 sm:grid-cols-[1fr_14rem_10rem]">
          <SearchInput
            placeholder="Search people"
            aria-label="Search people"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(0);
            }}
          />
          <Select
            aria-label="Department"
            value={departmentId}
            onChange={(event) => {
              setDepartmentId(event.target.value);
              setPage(0);
            }}
          >
            <option value="">All departments</option>
            {departments.data?.map((department) => (
              <option key={department.id} value={department.id}>
                {department.name}
              </option>
            ))}
          </Select>
          <Select
            aria-label="Status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as UserStatus);
              setPage(0);
            }}
          >
            <option value="ACTIVE">Active</option>
            <option value="DISABLED">Former staff</option>
          </Select>
        </div>

        {team.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading team">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : team.isError ? (
          <ErrorState error={team.error} onRetry={() => void team.refetch()} title="Couldn't load the team" />
        ) : team.data.content.length === 0 ? (
          <EmptyState icon={Users} title="Nobody matches" description="Try another name or department." />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Employee</TableHead>
                  <TableHead>Department</TableHead>
                  <TableHead>Manager</TableHead>
                  <TableHead>Contact</TableHead>
                  <TableHead>Location &amp; hours</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={team.isPlaceholderData ? 'opacity-60' : undefined}>
                {team.data.content.map((person) => (
                  <TableRow key={person.id}>
                    <TableCell>
                      <Link to={`/team/${person.id}`} className="block rounded-md hover:underline focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none">
                        <UserCell name={person.fullName} detail={person.jobTitle} />
                      </Link>
                    </TableCell>
                    <TableCell>{person.department.name}</TableCell>
                    <TableCell className="text-muted-foreground">{person.manager?.fullName ?? '—'}</TableCell>
                    <TableCell>
                      <a href={`mailto:${person.email}`} className="block text-primary hover:underline">
                        {person.email}
                      </a>
                      {person.phone && <span className="text-xs text-muted-foreground">{person.phone}</span>}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      <p>{person.location ?? '—'}</p>
                      {person.workingHours && <p className="text-xs">{person.workingHours}</p>}
                    </TableCell>
                    <TableCell>
                      <StatusBadge status={person.status} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination {...team.data} onPageChange={setPage} />
          </>
        )}
      </Card>
    </div>
  );
}
