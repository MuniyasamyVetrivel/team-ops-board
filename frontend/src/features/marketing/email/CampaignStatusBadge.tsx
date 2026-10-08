import { CalendarClock, CircleSlash, MailCheck, PencilLine, type LucideIcon } from 'lucide-react';

import { Badge, type BadgeProps } from '@/components/ui/badge';

import type { EmailCampaignStatus } from './api';
import { CAMPAIGN_STATUS_LABELS } from './email-meta';

const STYLE: Record<EmailCampaignStatus, { tone: BadgeProps['tone']; icon: LucideIcon }> = {
  DRAFT: { tone: 'neutral', icon: PencilLine },
  SCHEDULED: { tone: 'primary', icon: CalendarClock },
  SENT: { tone: 'success', icon: MailCheck },
  CANCELLED: { tone: 'neutral', icon: CircleSlash },
};

export function CampaignStatusBadge({ status }: { status: EmailCampaignStatus }) {
  const { tone, icon: Icon } = STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {CAMPAIGN_STATUS_LABELS[status]}
    </Badge>
  );
}
