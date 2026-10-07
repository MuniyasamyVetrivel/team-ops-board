import { FolderKanban } from 'lucide-react';

import { UserAvatar } from '@/components/common/UserAvatar';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from '@/lib/utils';

import { DueBadge, PriorityIndicator, TaskStatusBadge } from './TaskBadges';
import type { TaskListItem } from './types';

interface TaskTableProps {
  tasks: TaskListItem[];
  onOpen: (task: TaskListItem) => void;
  /** Hide the assignee column (e.g. on My Tasks). */
  hideAssignee?: boolean;
  dimmed?: boolean;
}

/** Dense task list in the style of the UI references: code + title, inline status/priority/assignee/due. */
export function TaskTable({ tasks, onOpen, hideAssignee = false, dimmed = false }: TaskTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Task</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Priority</TableHead>
          {!hideAssignee && <TableHead>Assignee</TableHead>}
          <TableHead>Due</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody className={cn(dimmed && 'opacity-60')}>
        {tasks.map((task) => (
          <TableRow
            key={task.id}
            data-clickable="true"
            tabIndex={0}
            aria-label={`Open ${task.code} ${task.title}`}
            onClick={() => onOpen(task)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onOpen(task);
              }
            }}
          >
            <TableCell className="max-w-md">
              <p className="truncate font-medium">{task.title}</p>
              <p className="flex items-center gap-2 text-xs text-muted-foreground">
                <span className="font-mono">{task.code}</span>
                <span aria-hidden>·</span>
                <span className="truncate">{task.department.name}</span>
                {task.project && (
                  <span className="inline-flex items-center gap-1 truncate">
                    <FolderKanban className="size-3" aria-hidden />
                    {task.project.name}
                  </span>
                )}
              </p>
            </TableCell>
            <TableCell>
              <TaskStatusBadge status={task.status} />
            </TableCell>
            <TableCell>
              <PriorityIndicator priority={task.priority} />
            </TableCell>
            {!hideAssignee && (
              <TableCell>
                {task.assignee ? (
                  <span className="flex items-center gap-2">
                    <UserAvatar name={task.assignee.fullName} size="sm" />
                    <span className="truncate text-sm">{task.assignee.fullName}</span>
                  </span>
                ) : (
                  <span className="text-sm text-muted-foreground">Unassigned</span>
                )}
              </TableCell>
            )}
            <TableCell>
              <DueBadge dueDate={task.dueDate} state={task.dueState} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
