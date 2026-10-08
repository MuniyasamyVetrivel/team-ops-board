import { CircleAlert, CircleCheck, Hourglass, type LucideIcon } from 'lucide-react';

import { Badge, type BadgeProps } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

import type { TargetStatus } from '../api';
import { formatMetric, formatPercent, type MetricFormat } from '../marketing-format';
import { TARGET_STATUS_LABELS } from '../marketing-meta';

const STATUS_STYLE: Record<TargetStatus, { tone: BadgeProps['tone']; icon: LucideIcon; bar: string }> = {
  ACHIEVED: { tone: 'success', icon: CircleCheck, bar: 'bg-status-success' },
  IN_PROGRESS: { tone: 'warning', icon: Hourglass, bar: 'bg-status-warning' },
  BEHIND: { tone: 'danger', icon: CircleAlert, bar: 'bg-status-danger' },
};

export function TargetStatusBadge({ status }: { status: TargetStatus }) {
  const { tone, icon: Icon } = STATUS_STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {TARGET_STATUS_LABELS[status]}
    </Badge>
  );
}

interface TargetProgressProps {
  label: string;
  target: number;
  actual: number;
  /** Server-computed; null when the target is zero. */
  achievementPct: number | null;
  remaining: number;
  status: TargetStatus;
  format?: MetricFormat;
}

/** "200 of 250 · 80% · 50 remaining" with a progress bar and a labelled status (all values come from the server). */
export function TargetProgress({ label, target, actual, achievementPct, remaining, status, format = 'count' }: TargetProgressProps) {
  const filled = Math.min(Math.max(achievementPct ?? 0, 0), 100);
  return (
    <div className="rounded-xl border bg-card p-4 shadow-xs">
      <div className="flex items-start justify-between gap-2">
        <span className="text-sm font-medium">{label}</span>
        <TargetStatusBadge status={status} />
      </div>
      <p className="mt-2 text-2xl font-semibold tracking-tight tabular-nums">
        {formatMetric(actual, format)}
        <span className="ml-1 text-sm font-normal text-muted-foreground">of {formatMetric(target, format)}</span>
      </p>
      <div
        role="progressbar"
        aria-label={`${label} achievement`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(filled)}
        aria-valuetext={formatPercent(achievementPct)}
        className="mt-3 h-2 overflow-hidden rounded-full bg-muted"
      >
        <div className={cn('h-full rounded-full', STATUS_STYLE[status].bar)} style={{ width: `${filled}%` }} />
      </div>
      <p className="mt-2 flex justify-between text-xs text-muted-foreground tabular-nums">
        <span>{formatPercent(achievementPct)} achieved</span>
        <span>{formatMetric(remaining, format)} remaining</span>
      </p>
    </div>
  );
}
