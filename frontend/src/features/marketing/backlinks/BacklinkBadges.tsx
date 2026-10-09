import { Badge } from '@/components/ui/badge';

import type { BacklinkStatus } from './api';
import { BACKLINK_STATUS_LABELS, BACKLINK_STATUS_STYLE } from './backlink-meta';

export function BacklinkStatusBadge({ status }: { status: BacklinkStatus }) {
  const { tone, icon: Icon } = BACKLINK_STATUS_STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {BACKLINK_STATUS_LABELS[status]}
    </Badge>
  );
}
