import { CalendarHeart, Flag, ListTodo, Plane, Presentation, Star, Users, Workflow, type LucideIcon } from 'lucide-react';

import type { CalendarEventType, CalendarItem } from './api';

export const EVENT_TYPE_LABELS: Record<CalendarEventType, string> = {
  TEAM_EVENT: 'Team event',
  MEETING: 'Meeting',
  IMPORTANT_DATE: 'Important date',
  LEAVE: 'Leave',
};

const EVENT_ICONS: Record<CalendarEventType, LucideIcon> = {
  TEAM_EVENT: Users,
  MEETING: Presentation,
  IMPORTANT_DATE: Star,
  LEAVE: Plane,
};

/**
 * How an item looks: an icon and a label for its kind (so colour is never the only signal), plus a colour class.
 * Overdue task deadlines are shown in the danger colour.
 */
export function itemVisual(item: CalendarItem): { icon: LucideIcon; label: string; className: string } {
  switch (item.kind) {
    case 'TASK_DEADLINE': {
      const overdue = item.task?.dueState === 'OVERDUE';
      return {
        icon: ListTodo,
        label: overdue ? 'Overdue task' : 'Task due',
        className: overdue ? 'border-status-danger/30 bg-status-danger/10 text-status-danger' : 'border-primary/20 bg-accent text-accent-foreground',
      };
    }
    case 'MILESTONE':
      return { icon: Flag, label: 'Milestone', className: 'border-status-success/30 bg-status-success/10 text-status-success' };
    case 'APPROVAL_DUE':
      return { icon: Workflow, label: 'Approval due', className: 'border-status-warning/30 bg-status-warning/10 text-status-warning' };
    default: {
      const type = item.eventType ?? 'TEAM_EVENT';
      return {
        icon: EVENT_ICONS[type] ?? CalendarHeart,
        label: EVENT_TYPE_LABELS[type],
        className: type === 'LEAVE' ? 'border-border bg-muted text-muted-foreground' : 'border-border bg-card text-foreground',
      };
    }
  }
}
