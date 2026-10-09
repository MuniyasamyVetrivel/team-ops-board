import { Badge } from '@/components/ui/badge';

import type { ContentStatus } from './api';
import { CONTENT_STATUS_LABELS, CONTENT_STATUS_STYLE } from './content-meta';

export function ContentStatusBadge({ status }: { status: ContentStatus }) {
  const { tone, icon: Icon } = CONTENT_STATUS_STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {CONTENT_STATUS_LABELS[status]}
    </Badge>
  );
}
