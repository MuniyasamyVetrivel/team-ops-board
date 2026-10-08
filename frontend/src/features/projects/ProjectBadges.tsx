import { CircleCheck, CircleDashed, CircleDot, CirclePause, CircleSlash, type LucideIcon } from 'lucide-react';

import { Badge, type BadgeProps } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

import type { ProjectStatus } from './api';
import { PROJECT_STATUS_LABELS } from './project-meta';

type Tone = BadgeProps['tone'];

const PROJECT_STYLE: Record<ProjectStatus, { tone: Tone; icon: LucideIcon }> = {
  PLANNING: { tone: 'neutral', icon: CircleDashed },
  ACTIVE: { tone: 'primary', icon: CircleDot },
  ON_HOLD: { tone: 'warning', icon: CirclePause },
  COMPLETED: { tone: 'success', icon: CircleCheck },
  CANCELLED: { tone: 'neutral', icon: CircleSlash },
};

export function ProjectStatusBadge({ status }: { status: ProjectStatus }) {
  const { tone, icon: Icon } = PROJECT_STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {PROJECT_STATUS_LABELS[status]}
    </Badge>
  );
}

/** Progress bar with the number; "—" when there is nothing to measure yet. */
export function ProgressBar({ value, className, label = 'Progress' }: { value: number | null; className?: string; label?: string }) {
  return (
    <div className={cn('flex items-center gap-3', className)}>
      <div
        className="h-2 flex-1 overflow-hidden rounded-full bg-muted"
        role="progressbar"
        aria-label={label}
        aria-valuenow={value ?? undefined}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuetext={value === null ? 'No tasks yet' : `${value}%`}
      >
        {value !== null && <div className={cn('h-full rounded-full', value >= 100 ? 'bg-status-success' : 'bg-primary')} style={{ width: `${Math.min(value, 100)}%` }} />}
      </div>
      <span className="w-10 text-right text-sm font-medium tabular-nums">{value === null ? '—' : `${value}%`}</span>
    </div>
  );
}
