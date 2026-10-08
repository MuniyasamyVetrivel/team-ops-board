import { Archive, CircleCheck, CircleDashed, CirclePause, CircleSlash, type LucideIcon } from 'lucide-react';

import { Badge, type BadgeProps } from '@/components/ui/badge';

import { RankingBadge, RankingChangeIndicator } from '../components/RankingBadges';
import { periodLabel } from '../marketing-format';
import type { KeywordStanding, KeywordStatus, PageStatus } from './api';
import { KEYWORD_STATUS_LABELS, PAGE_STATUS_LABELS } from './seo-meta';

const PAGE_STATUS_STYLE: Record<PageStatus, { tone: BadgeProps['tone']; icon: LucideIcon }> = {
  ACTIVE: { tone: 'success', icon: CircleCheck },
  INACTIVE: { tone: 'neutral', icon: CircleSlash },
  ARCHIVED: { tone: 'neutral', icon: Archive },
};

const KEYWORD_STATUS_STYLE: Record<KeywordStatus, { tone: BadgeProps['tone']; icon: LucideIcon }> = {
  ACTIVE: { tone: 'success', icon: CircleCheck },
  PAUSED: { tone: 'warning', icon: CirclePause },
  ARCHIVED: { tone: 'neutral', icon: Archive },
};

export function PageStatusBadge({ status }: { status: PageStatus }) {
  const { tone, icon: Icon } = PAGE_STATUS_STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {PAGE_STATUS_LABELS[status]}
    </Badge>
  );
}

export function KeywordStatusBadge({ status }: { status: KeywordStatus }) {
  const { tone, icon: Icon } = KEYWORD_STATUS_STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {KEYWORD_STATUS_LABELS[status]}
    </Badge>
  );
}

/**
 * The month's position: the ranking badge when a ranking was recorded (a recorded Not Ranked shows "NR"), otherwise a
 * neutral "No data" badge, so a missing entry is never mistaken for a measured result.
 */
export function StandingBadge({ standing, month, year }: { standing: KeywordStanding; month: number; year: number }) {
  if (!standing.recorded) {
    return (
      <Badge tone="neutral" title={`No ranking recorded for ${periodLabel(month, year)}; counted as not ranked`}>
        <CircleDashed aria-hidden />
        No data
      </Badge>
    );
  }
  return <RankingBadge position={standing.position} status={standing.status} />;
}

/** Previous month's position as text: "#12", "NR" (recorded, not ranked) or "—" (nothing recorded). */
export function PreviousPosition({ standing }: { standing: KeywordStanding }) {
  if (!standing.previousRecorded) return <span className="text-muted-foreground">—</span>;
  return <span className="tabular-nums">{standing.previousPosition === null ? 'NR' : `#${standing.previousPosition}`}</span>;
}

export function StandingChange({ standing }: { standing: KeywordStanding }) {
  if (!standing.change) return <span className="text-sm text-muted-foreground">—</span>;
  return <RankingChangeIndicator change={standing.change} />;
}
