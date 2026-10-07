import {
  AlarmClock,
  CalendarClock,
  CalendarDays,
  ChevronDown,
  ChevronUp,
  ChevronsUp,
  Circle,
  CircleCheck,
  CircleDot,
  CircleMinus,
  CircleSlash,
  Eye,
  Minus,
  OctagonX,
  type LucideIcon,
} from 'lucide-react';

import { Badge, type BadgeProps } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

import { dueLabel, PRIORITY_LABELS, STATUS_LABELS } from './task-meta';
import type { DueState, TaskPriority, TaskStatus } from './types';

type Tone = BadgeProps['tone'];

const STATUS_STYLE: Record<TaskStatus, { tone: Tone; icon: LucideIcon }> = {
  TODO: { tone: 'neutral', icon: Circle },
  IN_PROGRESS: { tone: 'primary', icon: CircleDot },
  BLOCKED: { tone: 'danger', icon: OctagonX },
  IN_REVIEW: { tone: 'warning', icon: Eye },
  COMPLETED: { tone: 'success', icon: CircleCheck },
  CANCELLED: { tone: 'neutral', icon: CircleSlash },
};

/** Status with icon + label (brief section 62: never colour alone). */
export function TaskStatusBadge({ status }: { status: TaskStatus }) {
  const { tone, icon: Icon } = STATUS_STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {STATUS_LABELS[status]}
    </Badge>
  );
}

const PRIORITY_STYLE: Record<TaskPriority, { className: string; icon: LucideIcon }> = {
  URGENT: { className: 'text-status-danger', icon: ChevronsUp },
  HIGH: { className: 'text-status-warning', icon: ChevronUp },
  MEDIUM: { className: 'text-muted-foreground', icon: Minus },
  LOW: { className: 'text-muted-foreground', icon: ChevronDown },
};

export function PriorityIndicator({ priority, className }: { priority: TaskPriority; className?: string }) {
  const { className: tone, icon: Icon } = PRIORITY_STYLE[priority];
  return (
    <span className={cn('inline-flex items-center gap-1 text-sm whitespace-nowrap', className)}>
      <Icon className={cn('size-4', tone)} aria-hidden />
      {PRIORITY_LABELS[priority]}
    </span>
  );
}

const DUE_STYLE: Record<DueState, { tone: Tone; icon: LucideIcon }> = {
  OVERDUE: { tone: 'danger', icon: AlarmClock },
  DUE_TODAY: { tone: 'warning', icon: CalendarClock },
  DUE_SOON: { tone: 'neutral', icon: CalendarDays },
  SCHEDULED: { tone: 'neutral', icon: CalendarDays },
  NONE: { tone: 'neutral', icon: CircleMinus },
};

/** Due date with urgency from the server's due state. Plain text when there is nothing to flag. */
export function DueBadge({ dueDate, state }: { dueDate: string | null; state: DueState }) {
  if (state === 'OVERDUE' || state === 'DUE_TODAY') {
    const { tone, icon: Icon } = DUE_STYLE[state];
    return (
      <Badge tone={tone}>
        <Icon aria-hidden />
        {dueLabel(dueDate, state)}
      </Badge>
    );
  }
  return <span className={cn('text-sm whitespace-nowrap', dueDate ? '' : 'text-muted-foreground')}>{dueLabel(dueDate, state)}</span>;
}
