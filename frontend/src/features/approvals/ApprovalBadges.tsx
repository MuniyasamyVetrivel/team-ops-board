import { CircleCheck, CircleDashed, CircleMinus, CircleSlash, CircleX, Clock, Hourglass, type LucideIcon } from 'lucide-react';

import { Badge, type BadgeProps } from '@/components/ui/badge';

import type { ApprovalStatus, StepStatus } from './api';
import { APPROVAL_STATUS_LABELS, STEP_STATUS_LABELS } from './approval-meta';

type Tone = BadgeProps['tone'];

const STATUS_STYLE: Record<ApprovalStatus, { tone: Tone; icon: LucideIcon }> = {
  PENDING: { tone: 'warning', icon: Clock },
  APPROVED: { tone: 'success', icon: CircleCheck },
  REJECTED: { tone: 'danger', icon: CircleX },
  CANCELLED: { tone: 'neutral', icon: CircleSlash },
};

export function ApprovalStatusBadge({ status }: { status: ApprovalStatus }) {
  const { tone, icon: Icon } = STATUS_STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {APPROVAL_STATUS_LABELS[status]}
    </Badge>
  );
}

const STEP_STYLE: Record<StepStatus, { tone: Tone; icon: LucideIcon }> = {
  WAITING: { tone: 'neutral', icon: CircleDashed },
  PENDING: { tone: 'warning', icon: Hourglass },
  APPROVED: { tone: 'success', icon: CircleCheck },
  REJECTED: { tone: 'danger', icon: CircleX },
  SKIPPED: { tone: 'neutral', icon: CircleMinus },
};

export function StepStatusBadge({ status }: { status: StepStatus }) {
  const { tone, icon: Icon } = STEP_STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {STEP_STATUS_LABELS[status]}
    </Badge>
  );
}
