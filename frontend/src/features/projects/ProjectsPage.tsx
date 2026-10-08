import { AlarmClock, FolderKanban, Flag, Plus, ShieldAlert } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { cn } from '@/lib/utils';

import { PROJECT_STATUSES, useProjects, type ProjectStatus } from './api';
import { ProgressBar, ProjectStatusBadge } from './ProjectBadges';
import { ProjectFormDialog } from './ProjectFormDialog';
import { dateRange, PROJECT_STATUS_LABELS } from './project-meta';

const STATUS_OPTIONS: { value: string; label: string; statuses: ProjectStatus[] }[] = [
  { value: 'open', label: 'In flight', statuses: ['PLANNING', 'ACTIVE', 'ON_HOLD'] },
  { value: 'all', label: 'All statuses', statuses: [] },
  ...PROJECT_STATUSES.map((s) => ({ value: s, label: PROJECT_STATUS_LABELS[s], statuses: [s] })),
];

const SORTS = [
  { value: 'end,asc', label: 'End date' },
  { value: 'name,asc', label: 'Name' },
  { value: 'updated,desc', label: 'Recently updated' },
];

/** Projects the viewer can see, with progress computed from their tasks. */
export default function ProjectsPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const departments = useDepartments();
  const [search, setSearch] = useState('');
  const [statusPreset, setStatusPreset] = useState('open');
  const [departmentId, setDepartmentId] = useState('');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const debounced = useDebouncedValue(search.trim());

  const projects = useProjects({
    search: debounced,
    status: STATUS_OPTIONS.find((o) => o.value === statusPreset)?.statuses,
    departmentId: departmentId ? Number(departmentId) : undefined,
    sort,
    page,
    size: 25,
  });

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  const filtered = Boolean(search || statusPreset !== 'open' || departmentId);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Projects"
        description="Milestones, risks and progress for every project you work on."
        actions={
          hasPermission(user, 'PROJECT_EDIT') && (
            <Button onClick={() => setCreating(true)}>
              <Plus aria-hidden />
              New project
            </Button>
          )
        }
      />
      <Card>
        <div className="grid gap-3 border-b p-4 sm:grid-cols-2 lg:grid-cols-[1fr_repeat(3,minmax(0,11rem))]">
          <SearchInput placeholder="Search name or code" aria-label="Search projects" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
          <Select aria-label="Status" value={statusPreset} onChange={(e) => filter(setStatusPreset)(e.target.value)}>
            {STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
          <Select aria-label="Department" value={departmentId} onChange={(e) => filter(setDepartmentId)(e.target.value)}>
            <option value="">All departments</option>
            {departments.data?.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </Select>
          <Select aria-label="Sort" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>
        {projects.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading projects">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-14" />
            ))}
          </div>
        ) : projects.isError ? (
          <ErrorState error={projects.error} title="Couldn't load projects" onRetry={() => void projects.refetch()} />
        ) : projects.data.content.length === 0 ? (
          <EmptyState
            icon={FolderKanban}
            title={filtered ? 'No projects match these filters' : 'No projects in flight'}
            description={filtered ? 'Try a different search or clear some filters.' : 'Create a project to group tasks, milestones and risks.'}
          />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Project</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Owner</TableHead>
                  <TableHead>Timeline</TableHead>
                  <TableHead className="min-w-44">Progress</TableHead>
                  <TableHead>Signals</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={cn(projects.isPlaceholderData && 'opacity-60')}>
                {projects.data.content.map((project) => (
                  <TableRow
                    key={project.id}
                    data-clickable="true"
                    tabIndex={0}
                    aria-label={`Open ${project.code} ${project.name}`}
                    onClick={() => void navigate(`/projects/${project.id}`)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') void navigate(`/projects/${project.id}`);
                    }}
                  >
                    <TableCell className="max-w-sm">
                      <p className="truncate font-medium">{project.name}</p>
                      <p className="text-xs text-muted-foreground">
                        <span className="font-mono">{project.code}</span> · {project.department.name}
                      </p>
                    </TableCell>
                    <TableCell>
                      <ProjectStatusBadge status={project.status} />
                    </TableCell>
                    <TableCell className="max-w-48">{project.owner ? <UserCell name={project.owner.fullName} /> : <span className="text-sm text-muted-foreground">No owner</span>}</TableCell>
                    <TableCell className="text-sm whitespace-nowrap text-muted-foreground">{dateRange(project.startDate, project.endDate)}</TableCell>
                    <TableCell>
                      <ProgressBar value={project.progress} label={`${project.name} progress`} />
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">
                        <span className="inline-flex items-center gap-1" title="Milestones completed">
                          <Flag className="size-3.5" aria-hidden />
                          {project.milestones.completed}/{project.milestones.total} milestones
                        </span>
                        {project.tasks.overdue > 0 && (
                          <span className="inline-flex items-center gap-1 font-medium text-status-danger">
                            <AlarmClock className="size-3.5" aria-hidden />
                            {project.tasks.overdue} overdue
                          </span>
                        )}
                        {project.openRisks > 0 && (
                          <span className="inline-flex items-center gap-1 text-status-warning">
                            <ShieldAlert className="size-3.5" aria-hidden />
                            {project.openRisks} open {project.openRisks === 1 ? 'risk' : 'risks'}
                          </span>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination {...projects.data} onPageChange={setPage} />
          </>
        )}
      </Card>
      <ProjectFormDialog open={creating} onOpenChange={setCreating} onSaved={(p) => void navigate(`/projects/${p.id}`)} />
    </div>
  );
}
