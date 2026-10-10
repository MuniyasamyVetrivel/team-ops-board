import { AlarmClock, CalendarClock, Gauge, Info } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { KpiTile } from '@/components/common/KpiCard';
import { PageHeader } from '@/components/common/PageHeader';
import { SearchInput } from '@/components/common/SearchInput';
import { UserCell } from '@/components/common/UserAvatar';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useDepartments } from '@/features/departments/api';
import { PRIORITY_LABELS, STATUS_LABELS } from '@/features/tasks/task-meta';
import { ACTIVE_STATUSES, PRIORITIES, type TaskPriority, type TaskStatus } from '@/features/tasks/types';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { cn } from '@/lib/utils';

import { useWorkload, type WorkloadLevel, type WorkloadSort } from './api';
import { LEVEL_META } from './levels';
import { WorkloadMeter } from './WorkloadMeter';

const SORTS: { value: WorkloadSort; label: string }[] = [
  { value: 'HIGHEST', label: 'Highest workload' },
  { value: 'LOWEST', label: 'Lowest workload' },
  { value: 'MOST_OVERDUE', label: 'Most overdue' },
  { value: 'MOST_COMPLETED', label: 'Most completed' },
  { value: 'MOST_ACTIVE', label: 'Most active' },
  { value: 'NAME', label: 'Name' },
];

const LEVELS: WorkloadLevel[] = ['OVERLOADED', 'HIGH', 'NORMAL', 'LOW'];

/** Brief section 9: who is overloaded, who has capacity, and what is overdue. */
export default function WorkloadPage() {
  const departments = useDepartments();
  const [search, setSearch] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [status, setStatus] = useState<TaskStatus | ''>('');
  const [priority, setPriority] = useState<TaskPriority | ''>('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [sort, setSort] = useState<WorkloadSort>('HIGHEST');
  const debouncedSearch = useDebouncedValue(search.trim());
  const rangeComplete = Boolean(from && to);
  const rangeInvalid = rangeComplete && to < from;

  const workload = useWorkload(
    {
      search: debouncedSearch,
      departmentId: departmentId ? Number(departmentId) : undefined,
      status: status ? [status] : undefined,
      priority: priority ? [priority] : undefined,
      from: rangeComplete ? from : undefined,
      to: rangeComplete ? to : undefined,
      sort,
    },
    !rangeInvalid,
  );
  const data = workload.data;

  return (
    <div className="space-y-6">
      <PageHeader title="Workload" description="Who is overloaded, who has capacity, and what needs attention." />

      <section aria-label="Workload summary" className="grid grid-cols-2 gap-4 sm:grid-cols-3 sm:gap-6 2xl:grid-cols-6">
        {LEVELS.map((level) => {
          const meta = LEVEL_META[level];
          return (
            <KpiTile
              key={level}
              label={meta.label}
              icon={meta.icon}
              tone={level === 'OVERLOADED' ? 'text-status-danger' : level === 'HIGH' ? 'text-status-warning' : level === 'NORMAL' ? 'text-status-success' : 'text-status-neutral'}
              value={data?.summary.byLevel[level]}
              hint={`${meta.range} of capacity`}
              loading={!data}
            />
          );
        })}
        <KpiTile label="Overdue tasks" icon={AlarmClock} tone="text-status-danger" value={data?.summary.overdue} alert={(data?.summary.overdue ?? 0) > 0} loading={!data} />
        <KpiTile label="Due today" icon={CalendarClock} tone="text-status-warning" value={data?.summary.dueToday} loading={!data} />
      </section>

      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-40 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
          <SearchInput placeholder="Search people" aria-label="Search people" value={search} onChange={(e) => setSearch(e.target.value)} />
          <Select aria-label="Department" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
            <option value="">All departments</option>
            {departments.data?.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </Select>
          <Select aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value as TaskStatus | '')}>
            <option value="">Any status</option>
            {[...ACTIVE_STATUSES, 'COMPLETED' as const].map((s) => (
              <option key={s} value={s}>
                {STATUS_LABELS[s]}
              </option>
            ))}
          </Select>
          <Select aria-label="Priority" value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority | '')}>
            <option value="">Any priority</option>
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {PRIORITY_LABELS[p]}
              </option>
            ))}
          </Select>
          <Input type="date" aria-label="Due from" value={from} onChange={(e) => setFrom(e.target.value)} aria-invalid={rangeInvalid || undefined} />
          <Input type="date" aria-label="Due to" value={to} onChange={(e) => setTo(e.target.value)} aria-invalid={rangeInvalid || undefined} />
          <Select aria-label="Sort" value={sort} onChange={(e) => setSort(e.target.value as WorkloadSort)}>
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>
        {data && (
          <p className="flex items-start gap-2 border-b bg-muted/30 px-4 py-2.5 text-xs text-muted-foreground">
            <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden />
            <span>
              Workload % = remaining estimated hours of open tasks due within {data.windowDays} days (including overdue and undated) ÷ weekly capacity
              for that period. Unestimated tasks count as {data.defaultTaskHours} h. Filters narrow the task counts; workload % always uses every
              open task.{' '}
              {rangeInvalid
                ? 'The end date is before the start date.'
                : rangeComplete
                  ? 'Counts show tasks due in the chosen range, and tasks completed in it.'
                  : 'Completed = last 30 days.'}
            </span>
          </p>
        )}

        {workload.isPending && !rangeInvalid ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading workload">
            {Array.from({ length: 8 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : workload.isError ? (
          <ErrorState error={workload.error} title="Couldn't load workload" onRetry={() => void workload.refetch()} />
        ) : !data || data.rows.length === 0 ? (
          <EmptyState icon={Gauge} title="Nobody to show" description="No active people match these filters." />
        ) : (
          <Table className={workload.isPlaceholderData ? 'opacity-60' : undefined}>
            <TableHeader>
              <TableRow>
                <TableHead>Employee</TableHead>
                <TableHead numeric>Total</TableHead>
                <TableHead numeric>To do</TableHead>
                <TableHead numeric>In progress</TableHead>
                <TableHead numeric>Blocked</TableHead>
                <TableHead numeric>Completed</TableHead>
                <TableHead numeric>Overdue</TableHead>
                <TableHead numeric>Due today</TableHead>
                <TableHead>Workload</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.rows.map((row) => (
                <TableRow key={row.user.id}>
                  <TableCell>
                    <Link to={`/team/${row.user.id}`} className="block rounded-md hover:underline focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none">
                      <UserCell name={row.user.fullName} detail={`${row.department.name}${row.user.jobTitle ? ` · ${row.user.jobTitle}` : ''}`} />
                    </Link>
                  </TableCell>
                  <Num value={row.totalTasks} />
                  <Num value={row.todo} />
                  <Num value={row.inProgress} />
                  <Num value={row.blocked} tone={row.blocked > 0 ? 'text-status-danger' : undefined} />
                  <Num value={row.completed} />
                  <Num value={row.overdue} tone={row.overdue > 0 ? 'font-semibold text-status-danger' : undefined} />
                  <Num value={row.dueToday} tone={row.dueToday > 0 ? 'text-status-warning' : undefined} />
                  <TableCell>
                    <WorkloadMeter percent={row.workloadPercent} level={row.level} />
                    <p className="mt-1 text-xs text-muted-foreground tabular-nums">
                      {row.remainingHours} h of {row.capacityHours} h
                    </p>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  );
}

function Num({ value, tone }: { value: number; tone?: string }) {
  return <TableCell numeric className={cn(value === 0 && 'text-muted-foreground', tone)}>{value}</TableCell>;
}
