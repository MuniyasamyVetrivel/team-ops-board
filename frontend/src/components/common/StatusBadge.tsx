import { CircleCheck, CircleMinus, CircleSlash } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import type { UserStatus } from '@/lib/api/types';

/** Active / disabled for users; active / inactive for departments. Icon + label, never colour alone. */
export function StatusBadge({ status }: { status: UserStatus | 'INACTIVE' }) {
  if (status === 'ACTIVE') {
    return (
      <Badge tone="success">
        <CircleCheck aria-hidden />
        Active
      </Badge>
    );
  }
  return (
    <Badge tone="neutral">
      {status === 'DISABLED' ? <CircleSlash aria-hidden /> : <CircleMinus aria-hidden />}
      {status === 'DISABLED' ? 'Disabled' : 'Inactive'}
    </Badge>
  );
}
