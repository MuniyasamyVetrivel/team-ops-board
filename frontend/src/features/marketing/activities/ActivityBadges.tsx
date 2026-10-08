import { Ban, CircleCheck, CircleDashed, CircleSlash, LoaderCircle, Repeat, type LucideIcon } from 'lucide-react';

import { Badge, type BadgeProps } from '@/components/ui/badge';

import type { Frequency, OccurrenceStatus } from './api';
import { FREQUENCY_LABELS, OCCURRENCE_STATUS_LABELS } from './activity-meta';

const STATUS_STYLE: Record<OccurrenceStatus, { tone: BadgeProps['tone']; icon: LucideIcon }> = {
  PENDING: { tone: 'neutral', icon: CircleDashed },
  IN_PROGRESS: { tone: 'primary', icon: LoaderCircle },
  COMPLETED: { tone: 'success', icon: CircleCheck },
  SKIPPED: { tone: 'neutral', icon: Ban },
};

export function OccurrenceStatusBadge({ status }: { status: OccurrenceStatus }) {
  const { tone, icon: Icon } = STATUS_STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {OCCURRENCE_STATUS_LABELS[status]}
    </Badge>
  );
}

export function FrequencyBadge({ frequency }: { frequency: Frequency }) {
  return (
    <Badge tone="primary">
      <Repeat aria-hidden />
      {FREQUENCY_LABELS[frequency]}
    </Badge>
  );
}

export function InactiveBadge() {
  return (
    <Badge tone="neutral">
      <CircleSlash aria-hidden />
      Inactive
    </Badge>
  );
}
