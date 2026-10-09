import { CircleCheck, CircleX, PhoneCall, Sparkles, Star, type LucideIcon } from 'lucide-react';

import { Badge, type BadgeProps } from '@/components/ui/badge';

import type { LeadStatus } from './api';
import { LEAD_STATUS_LABELS } from './lead-meta';

const STATUS_STYLE: Record<LeadStatus, { tone: BadgeProps['tone']; icon: LucideIcon }> = {
  NEW: { tone: 'primary', icon: Sparkles },
  CONTACTED: { tone: 'neutral', icon: PhoneCall },
  QUALIFIED: { tone: 'warning', icon: Star },
  CONVERTED: { tone: 'success', icon: CircleCheck },
  LOST: { tone: 'danger', icon: CircleX },
};

export function LeadStatusBadge({ status }: { status: LeadStatus }) {
  const { tone, icon: Icon } = STATUS_STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {LEAD_STATUS_LABELS[status]}
    </Badge>
  );
}
