import { CalendarClock, CircleAlert, CircleCheck, Hourglass, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';

import { KpiTile } from '@/components/common/KpiCard';
import { Badge, type BadgeProps } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

import type { TargetStatus } from '../api';
import { formatMetric, formatPercent, type MetricFormat } from '../marketing-format';
import { TARGET_STATUS_LABELS } from '../marketing-meta';

const STATUS_STYLE: Record<TargetStatus, { tone: BadgeProps['tone']; icon: LucideIcon }> = {
  ACHIEVED: { tone: 'success', icon: CircleCheck },
  IN_PROGRESS: { tone: 'warning', icon: Hourglass },
  BEHIND: { tone: 'danger', icon: CircleAlert },
};

/** A target's status with its label and icon. `null` is a planned (future) month: no actual and no status yet. */
export function TargetStatusBadge({ status }: { status: TargetStatus | null }) {
  if (status === null) {
    return (
      <Badge tone="neutral">
        <CalendarClock aria-hidden />
        Planned
      </Badge>
    );
  }
  const { tone, icon: Icon } = STATUS_STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {TARGET_STATUS_LABELS[status]}
    </Badge>
  );
}

/**
 * The achievement bar on its own (for tables); capped at 100% while the text keeps the real figure. The fill is the
 * brand amber accent; the status itself is always shown by the labelled badge beside it.
 */
export function TargetBar({ label, achievementPct, status, className }: { label: string; achievementPct: number | null; status: TargetStatus | null; className?: string }) {
  const filled = Math.min(Math.max(achievementPct ?? 0, 0), 100);
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(filled)}
      aria-valuetext={status === null ? 'Planned' : formatPercent(achievementPct)}
      className={cn('h-2 overflow-hidden rounded-full bg-muted', className)}
    >
      <div className={cn('h-full rounded-full', status ? 'bg-highlight' : 'bg-muted-foreground/30')} style={{ width: `${filled}%` }} />
    </div>
  );
}

interface TargetProgressProps {
  label: string;
  target: number;
  /** null when nothing is recorded yet, or for a planned month. */
  actual: number | null;
  /** Server-computed; null when the target is zero or the month is planned. */
  achievementPct: number | null;
  remaining: number;
  /** null for a planned (future) month. */
  status: TargetStatus | null;
  format?: MetricFormat;
  /** A short line under the figures, e.g. where the actual comes from. */
  hint?: ReactNode;
  /** E.g. an edit button. */
  action?: ReactNode;
}

/**
 * A target on the shared KPI tile: label with its status badge (and an optional action), "200 of 250" as the value,
 * "80% achieved · 50 remaining" as the helper line and an amber progress bar. All values come from the server.
 */
export function TargetProgress({ label, target, actual, achievementPct, remaining, status, format = 'count', hint, action }: TargetProgressProps) {
  return (
    <KpiTile
      label={label}
      aside={
        <>
          <TargetStatusBadge status={status} />
          {action}
        </>
      }
      value={
        <>
          {status === null ? <span className="text-muted-foreground">—</span> : formatMetric(actual ?? 0, format)}
          <span className="ml-1.5 text-label font-normal text-muted-foreground">of {formatMetric(target, format)}</span>
        </>
      }
      muted={status !== null && !actual}
      hint={
        <>
          <span>{status === null ? 'Planned month' : `${formatPercent(achievementPct)} achieved`}</span>
          <span aria-hidden> · </span>
          <span>{formatMetric(remaining, format)} remaining</span>
          {hint && (
            <>
              <span aria-hidden> · </span>
              {hint}
            </>
          )}
        </>
      }
      progress={<TargetBar label={`${label} achievement`} achievementPct={achievementPct} status={status} className="h-1" />}
    />
  );
}
