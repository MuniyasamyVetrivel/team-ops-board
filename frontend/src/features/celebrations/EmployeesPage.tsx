import { Award, Cake, Plus, UsersRound } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useIdParam } from '@/lib/use-id-param';
import { useInitialSearch } from '@/lib/use-initial-search';
import { cn } from '@/lib/utils';

import { EMPLOYEE_IMPORT, exportEmployees, useEmployees, type EmployeeQuery, type EmployeeStatus } from './api';
import { EmployeeAvatar } from './CelebrationBadges';
import { dayMonth, EMPLOYEE_STATUS_LABELS, formatShortDate, yearsLabel } from './celebration-meta';
import { ExportCsvButton, ImportCsvButton } from './CsvButtons';
import { EmployeeFormDialog } from './EmployeeFormDialog';
import { EmployeeSheet } from './EmployeeSheet';

const SORTS = [
  { value: 'name,asc', label: 'Name' },
  { value: 'code,asc', label: 'Employee ID' },
  { value: 'joined,asc', label: 'Longest serving' },
  { value: 'updated,desc', label: 'Recently updated' },
];

/**
 * Employees and celebrations: the people whose birthdays and work anniversaries the company celebrates. Everyone
 * sees the day and month; CELEBRATIONS_EDIT holders add, edit, import and export (with full birth dates).
 */
export default function EmployeesPage() {
  const { user } = useAuth();
  const canEdit = hasPermission(user, 'CELEBRATIONS_EDIT');
  const departments = useDepartments();
  const [openId, setOpenId] = useIdParam('employee');
  const [adding, setAdding] = useState(false);
  const [search, setSearch] = useState(useInitialSearch());
  const [departmentId, setDepartmentId] = useState('');
  const [status, setStatus] = useState<EmployeeStatus | ''>('ACTIVE');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(search.trim());

  const filters: Omit<EmployeeQuery, 'page' | 'size' | 'sort'> = {
    search: debounced,
    departmentId: departmentId ? Number(departmentId) : undefined,
    status: canEdit ? status || undefined : undefined,
  };
  const employees = useEmployees({ ...filters, sort, page, size: 25 });

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Employees & celebrations"
        description="Birthdays and work anniversaries, repeating every year from each person's dates."
        actions={
          canEdit && (
            <>
              <ImportCsvButton type={EMPLOYEE_IMPORT} />
              <ExportCsvButton onExport={() => exportEmployees(filters)} disabled={!employees.data?.totalElements} />
              <Button onClick={() => setAdding(true)}>
                <Plus aria-hidden />
                New employee
              </Button>
            </>
          )
        }
      />
      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-40 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
          <SearchInput placeholder="Search name, employee ID or designation" aria-label="Search employees" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
          <Select aria-label="Department" value={departmentId} onChange={(e) => filter(setDepartmentId)(e.target.value)}>
            <option value="">All departments</option>
            {departments.data?.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </Select>
          {canEdit && (
            <Select aria-label="Employee status" value={status} onChange={(e) => filter(setStatus)(e.target.value as EmployeeStatus | '')}>
              <option value="ACTIVE">Active</option>
              <option value="LEFT">Left</option>
              <option value="">Everyone</option>
            </Select>
          )}
          <Select aria-label="Sort employees" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>

        {employees.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading employees">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : employees.isError ? (
          <ErrorState error={employees.error} title="Couldn't load employees" onRetry={() => void employees.refetch()} />
        ) : employees.data.content.length === 0 ? (
          <EmptyState
            icon={UsersRound}
            title={debounced || departmentId ? 'No employees match these filters' : 'No employees yet'}
            description={canEdit ? 'Add people one by one or import a CSV from HR.' : undefined}
          />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Employee</TableHead>
                  <TableHead>Employee ID</TableHead>
                  <TableHead>Birthday</TableHead>
                  <TableHead>Next work anniversary</TableHead>
                  <TableHead>Department</TableHead>
                  <TableHead>Reports to</TableHead>
                  {canEdit && <TableHead>Status</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody className={cn(employees.isPlaceholderData && 'opacity-60')}>
                {employees.data.content.map((e) => (
                  <TableRow key={e.id}>
                    <TableCell className="max-w-xs">
                      <button type="button" className="flex min-w-0 items-center gap-3 text-left" onClick={() => setOpenId(e.id)}>
                        <EmployeeAvatar employee={e} />
                        <span className="min-w-0">
                          <span className="block truncate font-medium hover:underline">{e.fullName}</span>
                          <span className="block truncate text-xs text-muted-foreground">{e.designation}</span>
                        </span>
                      </button>
                    </TableCell>
                    <TableCell className="text-sm tabular-nums">{e.employeeCode}</TableCell>
                    <TableCell className="text-sm whitespace-nowrap">
                      <span className="inline-flex items-center gap-1.5">
                        <Cake className="size-3.5 text-occasion-birthday" aria-hidden />
                        {dayMonth(e.birthMonth, e.birthDay)}
                      </span>
                    </TableCell>
                    <TableCell className="text-sm whitespace-nowrap">
                      {e.nextAnniversary && e.nextAnniversaryYears !== null ? (
                        <span className="inline-flex items-center gap-1.5">
                          <Award className="size-3.5 text-occasion-anniversary" aria-hidden />
                          {formatShortDate(e.nextAnniversary)} · {yearsLabel(e.nextAnniversaryYears)}
                        </span>
                      ) : (
                        <span className="text-muted-foreground">{e.dateOfJoining ? 'Joined recently' : 'No joining date'}</span>
                      )}
                    </TableCell>
                    <TableCell className="text-sm">{e.department?.name ?? <span className="text-muted-foreground">—</span>}</TableCell>
                    <TableCell className="text-sm">{e.reportingManager}</TableCell>
                    {canEdit && (
                      <TableCell>
                        <Badge tone={e.status === 'ACTIVE' ? 'success' : 'neutral'}>{EMPLOYEE_STATUS_LABELS[e.status]}</Badge>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination {...employees.data} onPageChange={setPage} />
          </>
        )}
      </Card>
      <EmployeeSheet employeeId={openId} onClose={() => setOpenId(null)} />
      <EmployeeFormDialog open={adding} onOpenChange={setAdding} onSaved={setOpenId} />
    </div>
  );
}
