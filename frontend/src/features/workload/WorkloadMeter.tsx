import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

import type { WorkloadLevel } from './api';
import { LEVEL_META } from './levels';

export function WorkloadLevelBadge({ level }: { level: WorkloadLevel }) {
  const { label, tone, icon: Icon } = LEVEL_META[level];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {label}
    </Badge>
  );
}

/** Bar + percentage + level label. The bar is capped at 100% width; the number is not. */
export function WorkloadMeter({ percent, level, className }: { percent: number; level: WorkloadLevel; className?: string }) {
  return (
    <div className={cn('flex min-w-44 items-center gap-3', className)}>
      <div
        className="h-2 flex-1 overflow-hidden rounded-full bg-muted"
        role="meter"
        aria-valuenow={percent}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={`Workload ${percent}% (${LEVEL_META[level].label})`}
      >
        <div className={cn('h-full rounded-full', LEVEL_META[level].bar)} style={{ width: `${Math.min(percent, 100)}%` }} />
      </div>
      <span className="w-11 text-right text-sm font-semibold tabular-nums">{percent}%</span>
      <WorkloadLevelBadge level={level} />
    </div>
  );
}
