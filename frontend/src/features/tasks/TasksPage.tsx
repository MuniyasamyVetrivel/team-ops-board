import { ClipboardList, Plus } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useInitialSearch } from '@/lib/use-initial-search';

import { useTasks } from './api';
import { CreateTaskDialog } from './CreateTaskDialog';
import { TaskDrawer } from './TaskDrawer';
import { TaskTable } from './TaskTable';
import { PRIORITY_LABELS, STATUS_LABELS } from './task-meta';
import { ACTIVE_STATUSES, ALL_STATUSES, PRIORITIES, type DueFilter, type TaskPriority, type TaskStatus, type TaskView } from './types';
import { useTaskParam } from './use-task-param';

const PAGE_SIZE = 25;

/** Status presets: "open" (active) is the default working view. */
const STATUS_OPTIONS: { value: string; label: string; statuses: TaskStatus[] }[] = [
  { value: 'open', label: 'Open', statuses: ACTIVE_STATUSES },
  { value: 'all', label: 'All statuses', statuses: [] },
  ...ALL_STATUSES.map((s) => ({ value: s, label: STATUS_LABELS[s], statuses: [s] })),
];

const DUE_OPTIONS: { value: DueFilter | ''; label: string }[] = [
  { value: '', label: 'Any due date' },
  { value: 'OVERDUE', label: 'Overdue' },
  { value: 'TODAY', label: 'Due today' },
  { value: 'UPCOMING', label: 'Next 7 days' },
  { value: 'NO_DUE_DATE', label: 'No due date' },
];

const VIEW_OPTIONS: { value: TaskView; label: string }[] = [
  { value: 'ALL', label: 'All I can see' },
  { value: 'ASSIGNED_TO_ME', label: 'Assigned to me' },
  { value: 'CREATED_BY_ME', label: 'Created by me' },
  { value: 'WATCHING', label: 'Watching' },
];

const SORTS = [
  { value: 'due,asc', label: 'Due date' },
  { value: 'priority,desc', label: 'Priority' },
  { value: 'updated,desc', label: 'Recently updated' },
  { value: 'created,desc', label: 'Newest' },
];

export default function TasksPage() {
  const { user } = useAuth();
  const departments = useDepartments();
  const [taskId, setTaskId] = useTaskParam();
  const [creating, setCreating] = useState(false);
  const initialSearch = useInitialSearch();
  const [search, setSearch] = useState(initialSearch);
  const [statusPreset, setStatusPreset] = useState('open');
  const [priority, setPriority] = useState<TaskPriority | ''>('');
  const [due, setDue] = useState<DueFilter | ''>('');
  const [view, setView] = useState<TaskView>('ALL');
  const [departmentId, setDepartmentId] = useState('');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebouncedValue(search.trim());

  const tasks = useTasks({
    search: debouncedSearch,
    status: STATUS_OPTIONS.find((o) => o.value === statusPreset)?.statuses,
    priority: priority ? [priority] : undefined,
    due: due || undefined,
    view,
    departmentId: departmentId ? Number(departmentId) : undefined,
    sort,
    page,
    size: PAGE_SIZE,
  });

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  const filtered = Boolean(search || statusPreset !== 'open' || priority || due || view !== 'ALL' || departmentId);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Tasks"
        description="Everything your role lets you see, across the team."
        actions={
          hasPermission(user, 'TASK_CREATE') && (
            <Button onClick={() => setCreating(true)}>
              <Plus aria-hidden />
              New task
            </Button>
          )
        }
      />
      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-40 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
          <SearchInput placeholder="Search title or code" aria-label="Search tasks" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
          <Select aria-label="Status" value={statusPreset} onChange={(e) => filter(setStatusPreset)(e.target.value)}>
            {STATUS_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
          <Select aria-label="Priority" value={priority} onChange={(e) => filter(setPriority)(e.target.value as TaskPriority | '')}>
            <option value="">Any priority</option>
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {PRIORITY_LABELS[p]}
              </option>
            ))}
          </Select>
          <Select aria-label="Due" value={due} onChange={(e) => filter(setDue)(e.target.value as DueFilter | '')}>
            {DUE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
          <Select aria-label="View" value={view} onChange={(e) => filter(setView)(e.target.value as TaskView)}>
            {VIEW_OPTIONS.map((o) => (
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

        {tasks.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading tasks">
            {Array.from({ length: 8 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : tasks.isError ? (
          <ErrorState error={tasks.error} title="Couldn't load tasks" onRetry={() => void tasks.refetch()} />
        ) : tasks.data.content.length === 0 ? (
          <EmptyState
            icon={ClipboardList}
            title={filtered ? 'No tasks match these filters' : 'No open tasks'}
            description={filtered ? 'Try a different search or clear some filters.' : 'Create a task to get work moving.'}
          />
        ) : (
          <>
            <TaskTable tasks={tasks.data.content} onOpen={(t) => setTaskId(t.id)} dimmed={tasks.isPlaceholderData} />
            <Pagination {...tasks.data} onPageChange={setPage} />
          </>
        )}
      </Card>

      <CreateTaskDialog open={creating} onOpenChange={setCreating} onCreated={(t) => setTaskId(t.id)} />
      <TaskDrawer taskId={taskId} onClose={() => setTaskId(null)} />
    </div>
  );
}
