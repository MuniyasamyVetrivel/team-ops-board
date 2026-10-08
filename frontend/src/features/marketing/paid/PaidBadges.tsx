import { CircleAlert, CircleCheck, CirclePause, PencilLine, Play, type LucideIcon } from 'lucide-react';

import { Badge, type BadgeProps } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

import { formatInr, formatPercent } from '../marketing-format';
import type { BudgetProgress, PaidCampaignStatus } from './api';
import { PAID_STATUS_LABELS } from './paid-meta';

const STATUS_STYLE: Record<PaidCampaignStatus, { tone: BadgeProps['tone']; icon: LucideIcon }> = {
  DRAFT: { tone: 'neutral', icon: PencilLine },
  ACTIVE: { tone: 'primary', icon: Play },
  PAUSED: { tone: 'warning', icon: CirclePause },
  COMPLETED: { tone: 'success', icon: CircleCheck },
};

export function PaidStatusBadge({ status }: { status: PaidCampaignStatus }) {
  const { tone, icon: Icon } = STATUS_STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {PAID_STATUS_LABELS[status]}
    </Badge>
  );
}

/** Near the end of the budget (90% used) the bar turns amber; over budget it turns red, always with words. */
const NEARLY_SPENT_PCT = 90;

/**
 * Spend against budget as a bar (capped at 100%) with the figures in words: "84% used · ₹8,000 left", or
 * "Over budget by ₹3,000" with an icon. All values come from the server.
 */
export function BudgetBar({ label, progress, className, compact = false }: { label: string; progress: BudgetProgress; className?: string; compact?: boolean }) {
  const used = progress.usedPct ?? 0;
  const filled = Math.min(Math.max(used, 0), 100);
  const tone = progress.overBudget ? 'bg-status-danger' : used >= NEARLY_SPENT_PCT ? 'bg-status-warning' : 'bg-primary';
  const remaining = progress.remaining ?? 0;
  return (
    <div className={cn('space-y-1', className)}>
      <div
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(filled)}
        aria-valuetext={progress.overBudget ? `Over budget, ${formatPercent(progress.usedPct)} used` : `${formatPercent(progress.usedPct)} used`}
        className="h-2 overflow-hidden rounded-full bg-muted"
      >
        <div className={cn('h-full rounded-full', tone)} style={{ width: `${filled}%` }} />
      </div>
      {progress.overBudget ? (
        <p className="flex items-center gap-1 text-xs font-medium text-status-danger">
          <CircleAlert className="size-3.5" aria-hidden />
          Over budget by {formatInr(Math.abs(remaining))}
        </p>
      ) : (
        <p className="flex justify-between gap-2 text-xs text-muted-foreground tabular-nums">
          <span>{formatPercent(progress.usedPct)} used</span>
          {!compact && <span>{formatInr(remaining)} left</span>}
        </p>
      )}
    </div>
  );
}
