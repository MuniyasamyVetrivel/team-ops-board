import { ArrowDown, ArrowUp, CircleOff, Minus, Sparkles, TrendingUp, Trophy, type LucideIcon } from 'lucide-react';

import { Badge, type BadgeProps } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

import type { RankingChange, RankingStatus } from '../api';
import { RANKING_STATUS_LABELS } from '../marketing-meta';

const STATUS_STYLE: Record<RankingStatus, { tone: BadgeProps['tone']; icon: LucideIcon }> = {
  TOP_10: { tone: 'success', icon: Trophy },
  RANKING: { tone: 'warning', icon: TrendingUp },
  NOT_RANKED: { tone: 'danger', icon: CircleOff },
};

/** "#7 · TOP 10" (green), "#15 · RANKING" (orange), "NR · NOT RANKED" (red). The status comes from the server. */
export function RankingBadge({ position, status }: { position: number | null; status: RankingStatus }) {
  const { tone, icon: Icon } = STATUS_STYLE[status];
  return (
    <Badge tone={tone} className="tabular-nums">
      <Icon aria-hidden />
      {position === null ? 'NR' : `#${position}`} <span aria-hidden>·</span> {RANKING_STATUS_LABELS[status]}
    </Badge>
  );
}

/**
 * Month-over-month movement: green up arrow (improved), red down arrow (declined), gray dash (no change). Change is
 * previous − current, so +5 means five places higher.
 */
export function RankingChangeIndicator({ change, className }: { change: RankingChange; className?: string }) {
  const { value, movement } = change;
  const { icon: Icon, text, tone, description } = describe(value, movement);
  return (
    <span className={cn('inline-flex items-center gap-1 text-xs font-medium tabular-nums', tone, className)} title={description}>
      <Icon className="size-3.5" aria-hidden />
      <span aria-hidden>{text}</span>
      <span className="sr-only">{description}</span>
    </span>
  );
}

function describe(value: number | null, movement: RankingChange['movement']) {
  switch (movement) {
    case 'IMPROVED':
      return value === null
        ? { icon: ArrowUp, text: 'Entered', tone: 'text-status-success', description: 'Improved: now ranked' }
        : { icon: ArrowUp, text: `+${value}`, tone: 'text-status-success', description: `Improved by ${value} ${value === 1 ? 'place' : 'places'}` };
    case 'DECLINED':
      return value === null
        ? { icon: ArrowDown, text: 'Dropped', tone: 'text-status-danger', description: 'Declined: no longer ranked' }
        : { icon: ArrowDown, text: `−${Math.abs(value)}`, tone: 'text-status-danger', description: `Declined by ${Math.abs(value)} ${Math.abs(value) === 1 ? 'place' : 'places'}` };
    case 'NEW':
      return { icon: Sparkles, text: 'New', tone: 'text-muted-foreground', description: 'New: no ranking last month' };
    default:
      return { icon: Minus, text: '0', tone: 'text-muted-foreground', description: 'No change' };
  }
}
