import { CalendarDays, History, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { UserAvatar, UserCell } from '@/components/common/UserAvatar';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import type { CalendarEventType, CalendarItem } from '@/features/calendar/api';
import { DueBadge, PriorityIndicator } from '@/features/tasks/TaskBadges';
import { historyLabel, historyValue, parseLocalDate } from '@/features/tasks/task-meta';
import type { TaskListItem } from '@/features/tasks/types';
import type { WorkloadRow } from '@/features/workload/api';
import { WorkloadLevelBadge, WorkloadMeter } from '@/features/workload/WorkloadMeter';
import { formatRelative } from '@/lib/format';
import { cn } from '@/lib/utils';

import type { ActivityItem, DepartmentRow } from './api';

/** Compact task list (overdue, upcoming). Rows open the task drawer. */
export function TaskList({ tasks, empty, emptyIcon, onOpen, showAssignee }: { tasks: TaskListItem[]; empty: string; emptyIcon: LucideIcon; onOpen: (task: TaskListItem) => void; showAssignee: boolean }) {
  if (tasks.length === 0) return <EmptyState icon={emptyIcon} title={empty} className="py-10" />;
  return (
    <ul className="divide-y">
      {tasks.map((task) => (
        <li key={task.id}>
          <button type="button" onClick={() => onOpen(task)} className="flex w-full items-center gap-3 px-5 py-3 text-left transition-colors hover:bg-muted/40 focus-visible:bg-muted/40 focus-visible:outline-none">
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{task.title}</p>
              <p className="mt-0.5 flex items-center gap-2 text-xs text-muted-foreground">
                <span className="font-mono">{task.code}</span>
                <span aria-hidden>·</span>
                <PriorityIndicator priority={task.priority} className="text-xs" />
                {showAssignee && (
                  <>
                    <span aria-hidden>·</span>
                    <span className="truncate">{task.assignee?.fullName ?? 'Unassigned'}</span>
                  </>
                )}
              </p>
            </div>
            <DueBadge dueDate={task.dueDate} state={task.dueState} />
          </button>
        </li>
      ))}
    </ul>
  );
}

export function EmployeeWorkloadTable({ rows, onOpenPerson }: { rows: WorkloadRow[]; onOpenPerson?: (row: WorkloadRow) => void }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Employee</TableHead>
          <TableHead className="text-right">Active</TableHead>
          <TableHead className="text-right">Overdue</TableHead>
          <TableHead>Workload</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.user.id} data-clickable={Boolean(onOpenPerson)} onClick={() => onOpenPerson?.(row)}>
            <TableCell className="max-w-56">
              <UserCell name={row.user.fullName} detail={row.department.name} />
            </TableCell>
            <TableCell className="text-right tabular-nums">{row.activeTasks}</TableCell>
            <TableCell className={cn('text-right tabular-nums', row.overdue > 0 && 'font-semibold text-status-danger')}>{row.overdue}</TableCell>
            <TableCell>
              <WorkloadMeter percent={row.workloadPercent} level={row.level} className="min-w-52" />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function percentText(value: number | null): string {
  return value === null ? '—' : `${value}%`;
}

export function DepartmentPerformanceTable({ departments, completedWindowDays }: { departments: DepartmentRow[]; completedWindowDays: number }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Department</TableHead>
          <TableHead className="text-right">People</TableHead>
          <TableHead className="text-right">Open</TableHead>
          <TableHead className="text-right">Overdue</TableHead>
          <TableHead className="text-right" title={`Completed in the last ${completedWindowDays} days`}>
            Completed ({completedWindowDays}d)
          </TableHead>
          <TableHead className="text-right">On time</TableHead>
          <TableHead>Workload</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {departments.map((row) => (
          <TableRow key={row.department.id}>
            <TableCell className="font-medium">{row.department.name}</TableCell>
            <TableCell className="text-right tabular-nums">{row.people}</TableCell>
            <TableCell className="text-right tabular-nums">{row.openTasks}</TableCell>
            <TableCell className={cn('text-right tabular-nums', row.overdue > 0 && 'font-semibold text-status-danger')}>{row.overdue}</TableCell>
            <TableCell className="text-right tabular-nums">{row.completed}</TableCell>
            <TableCell className="text-right tabular-nums">{percentText(row.onTimePercent)}</TableCell>
            <TableCell>
              {row.workloadPercent === null || row.level === null ? (
                <span className="text-muted-foreground">—</span>
              ) : (
                <span className="flex items-center gap-2">
                  <span className="w-11 text-right tabular-nums">{row.workloadPercent}%</span>
                  <WorkloadLevelBadge level={row.level} />
                </span>
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function activityText(item: ActivityItem): ReactNode {
  if (item.field === 'status' || item.field === 'reopened' || item.field === 'priority') {
    return (
      <>
        {historyLabel(item.field)} to <span className="font-medium text-foreground">{historyValue(item.newValue)}</span>
      </>
    );
  }
  if (item.field === 'assignee') {
    return item.newValue ? (
      <>
        assigned it to <span className="font-medium text-foreground">{item.newValue}</span>
      </>
    ) : (
      'unassigned it'
    );
  }
  return historyLabel(item.field);
}

export function RecentActivity({ items, onOpenTask }: { items: ActivityItem[]; onOpenTask: (taskId: number) => void }) {
  if (items.length === 0) return <EmptyState icon={History} title="No activity yet" description="Task changes from your team appear here." className="py-10" />;
  return (
    <ol className="divide-y">
      {items.map((item) => (
        <li key={item.id} className="flex items-start gap-3 px-5 py-3">
          <UserAvatar name={item.actorName ?? 'System'} size="sm" />
          <div className="min-w-0 flex-1 text-sm">
            <p className="text-muted-foreground">
              <span className="font-medium text-foreground">{item.actorName ?? 'System'}</span> {activityText(item)}
            </p>
            <button type="button" onClick={() => onOpenTask(item.taskId)} className="mt-0.5 block max-w-full truncate text-left text-xs text-primary hover:underline">
              <span className="font-mono">{item.taskCode}</span> {item.taskTitle}
            </button>
          </div>
          <time className="shrink-0 text-xs text-muted-foreground" dateTime={item.at}>
            {formatRelative(item.at)}
          </time>
        </li>
      ))}
    </ol>
  );
}

const EVENT_LABELS: Record<CalendarEventType, string> = {
  TEAM_EVENT: 'Team event',
  MEETING: 'Meeting',
  IMPORTANT_DATE: 'Important date',
  LEAVE: 'Leave',
};

const dayFormat = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
const timeFormat = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' });

function eventWhen(item: CalendarItem): string {
  const start = dayFormat.format(parseLocalDate(item.startDate));
  if (!item.allDay && item.startAt) return `${start}, ${timeFormat.format(new Date(item.startAt))}`;
  return item.endDate !== item.startDate ? `${start} – ${dayFormat.format(parseLocalDate(item.endDate))}` : start;
}

/** Upcoming events and leave from the calendar API (task deadlines have their own list). */
export function UpcomingEvents({ items }: { items: CalendarItem[] }) {
  const events = items.filter((item) => item.kind === 'EVENT');
  if (events.length === 0) return <EmptyState icon={CalendarDays} title="Nothing scheduled" description="Team events, meetings and leave for the next two weeks appear here." className="py-10" />;
  return (
    <ul className="divide-y">
      {events.map((item) => (
        <li key={item.key} className="flex items-center gap-3 px-5 py-3">
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{item.title}</p>
            <p className="mt-0.5 text-xs text-muted-foreground">
              {eventWhen(item)}
              {item.department && ` · ${item.department.name}`}
            </p>
          </div>
          <Badge tone={item.eventType === 'LEAVE' ? 'warning' : item.eventType === 'IMPORTANT_DATE' ? 'primary' : 'neutral'}>
            {item.eventType ? EVENT_LABELS[item.eventType] : 'Event'}
          </Badge>
        </li>
      ))}
    </ul>
  );
}
