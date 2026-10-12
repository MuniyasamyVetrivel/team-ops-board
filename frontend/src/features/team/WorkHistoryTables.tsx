import { CircleAlert, CircleCheck, Clock } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { PriorityIndicator, TaskStatusBadge } from '@/features/tasks/TaskBadges';
import { parseLocalDate } from '@/features/tasks/task-meta';
import { SlaStateBadge, TicketStatusBadge } from '@/features/tickets/TicketBadges';
import { formatDateTime } from '@/lib/format';
import { cn } from '@/lib/utils';

import type { TaskHistoryRow, TicketHistoryRow } from './work-history-api';

const dateFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

function day(iso: string | null) {
  return iso ? dateFormat.format(parseLocalDate(iso)) : <span className="text-muted-foreground">—</span>;
}

function days(n: number) {
  return `${n} ${n === 1 ? 'day' : 'days'}`;
}

/** On time, late by N days (completed), overdue by N days (open), or nothing to say yet. */
export function TimelinessBadge({ onTime, daysLate, overdue }: { onTime: boolean | null; daysLate: number | null; overdue: boolean }) {
  if (overdue && daysLate !== null) {
    return (
      <Badge tone="danger">
        <CircleAlert aria-hidden />
        Overdue by {days(daysLate)}
      </Badge>
    );
  }
  if (onTime === true) {
    return (
      <Badge tone="success">
        <CircleCheck aria-hidden />
        On time
      </Badge>
    );
  }
  if (onTime === false || daysLate !== null) {
    return (
      <Badge tone="warning">
        <Clock aria-hidden />
        Late by {days(daysLate ?? 1)}
      </Badge>
    );
  }
  return <span className="text-sm text-muted-foreground">—</span>;
}

/** Resolved tickets: SLA met or breached. Open ones: overdue, or their current SLA state. */
export function SlaOutcomeBadge({ row }: { row: TicketHistoryRow }) {
  if (row.onTime === true) {
    return (
      <Badge tone="success">
        <CircleCheck aria-hidden />
        SLA met
      </Badge>
    );
  }
  if (row.onTime === false) {
    return (
      <Badge tone="danger">
        <CircleAlert aria-hidden />
        SLA breached{row.daysLate ? ` · ${days(row.daysLate)}` : ''}
      </Badge>
    );
  }
  if (row.overdue) {
    return (
      <Badge tone="danger">
        <CircleAlert aria-hidden />
        Overdue by {days(row.daysLate ?? 1)}
      </Badge>
    );
  }
  return <SlaStateBadge state={row.slaState} />;
}

export function TaskHistoryTable({ rows, faded, onOpen }: { rows: TaskHistoryRow[]; faded: boolean; onOpen: (id: number) => void }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Task ID</TableHead>
          <TableHead>Title</TableHead>
          <TableHead>Project</TableHead>
          <TableHead>Priority</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Due date</TableHead>
          <TableHead>Completed</TableHead>
          <TableHead>On time</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody className={cn(faded && 'opacity-60')}>
        {rows.map((row) => (
          <TableRow key={row.id} className="cursor-pointer" onClick={() => onOpen(row.id)}>
            <TableCell className="font-mono text-xs whitespace-nowrap text-muted-foreground">{row.code}</TableCell>
            <TableCell className="max-w-xs">
              <button type="button" className="block max-w-full truncate text-left font-medium hover:underline" onClick={(event) => {
                  event.stopPropagation();
                  onOpen(row.id);
                }}>
                {row.title}
              </button>
            </TableCell>
            <TableCell className="max-w-40 truncate text-sm">{row.project?.name ?? <span className="text-muted-foreground">—</span>}</TableCell>
            <TableCell>
              <PriorityIndicator priority={row.priority} />
            </TableCell>
            <TableCell>
              <TaskStatusBadge status={row.status} />
            </TableCell>
            <TableCell className="text-sm whitespace-nowrap">{day(row.dueDate)}</TableCell>
            <TableCell className="text-sm whitespace-nowrap">{day(row.completedDate)}</TableCell>
            <TableCell>
              <TimelinessBadge onTime={row.onTime} daysLate={row.daysLate} overdue={row.overdue} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function TicketHistoryTable({ rows, faded, onOpen }: { rows: TicketHistoryRow[]; faded: boolean; onOpen: (id: number) => void }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Ticket ID</TableHead>
          <TableHead>Subject</TableHead>
          <TableHead>Category</TableHead>
          <TableHead>Priority</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Resolution due</TableHead>
          <TableHead>Resolved</TableHead>
          <TableHead>SLA</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody className={cn(faded && 'opacity-60')}>
        {rows.map((row) => (
          <TableRow key={row.id} className="cursor-pointer" onClick={() => onOpen(row.id)}>
            <TableCell className="font-mono text-xs whitespace-nowrap text-muted-foreground">{row.code}</TableCell>
            <TableCell className="max-w-xs">
              <button type="button" className="block max-w-full truncate text-left font-medium hover:underline" onClick={(event) => {
                  event.stopPropagation();
                  onOpen(row.id);
                }}>
                {row.subject}
              </button>
            </TableCell>
            <TableCell className="max-w-40 truncate text-sm">{row.category ?? <span className="text-muted-foreground">—</span>}</TableCell>
            <TableCell>
              <PriorityIndicator priority={row.priority} />
            </TableCell>
            <TableCell>
              <TicketStatusBadge status={row.status} />
            </TableCell>
            <TableCell className="text-sm whitespace-nowrap">{row.dueAt ? formatDateTime(row.dueAt) : <span className="text-muted-foreground">—</span>}</TableCell>
            <TableCell className="text-sm whitespace-nowrap">{day(row.resolvedDate)}</TableCell>
            <TableCell>
              <SlaOutcomeBadge row={row} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
