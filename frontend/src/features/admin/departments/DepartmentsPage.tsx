import { Building, Plus } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { StatusBadge } from '@/components/common/StatusBadge';
import { UserCell } from '@/components/common/UserAvatar';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useDepartments } from '@/features/departments/api';

import { DepartmentDrawer } from './DepartmentDrawer';
import { DepartmentFormDialog } from './DepartmentFormDialog';

export default function DepartmentsPage() {
  const departments = useDepartments();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Departments"
        description="Department managers, members and status."
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus aria-hidden />
            New department
          </Button>
        }
      />

      <Card>
        {departments.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading departments">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : departments.isError ? (
          <ErrorState error={departments.error} onRetry={() => void departments.refetch()} title="Couldn't load departments" />
        ) : departments.data.length === 0 ? (
          <EmptyState icon={Building} title="No departments yet" description="Create the first department to start organising people." />
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Department</TableHead>
                <TableHead>Manager</TableHead>
                <TableHead className="text-right">People</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {departments.data.map((department) => (
                <TableRow
                  key={department.id}
                  data-clickable="true"
                  tabIndex={0}
                  aria-label={`Open ${department.name}`}
                  onClick={() => setSelectedId(department.id)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      setSelectedId(department.id);
                    }
                  }}
                >
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{department.name}</span>
                      <Badge>{department.code}</Badge>
                    </div>
                    {department.description && <p className="mt-0.5 max-w-md truncate text-xs text-muted-foreground">{department.description}</p>}
                  </TableCell>
                  <TableCell>
                    {department.manager ? (
                      <UserCell name={department.manager.fullName} detail={department.manager.jobTitle} />
                    ) : (
                      <span className="text-sm text-muted-foreground">Not assigned</span>
                    )}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {department.memberCount}
                    {department.secondaryMemberCount > 0 && (
                      <span className="text-xs text-muted-foreground"> +{department.secondaryMemberCount}</span>
                    )}
                  </TableCell>
                  <TableCell>
                    <StatusBadge status={department.status} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      <DepartmentFormDialog open={creating} onOpenChange={setCreating} onSaved={(created) => setSelectedId(created.id)} />
      <DepartmentDrawer departmentId={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  );
}
