import { Shield, ShieldCheck, User } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { ROLE_LABELS, type RoleCode } from '@/features/auth/permissions';

const ICONS = { SUPER_ADMIN: ShieldCheck, DEPARTMENT_MANAGER: Shield, EMPLOYEE: User } as const;

export function RoleBadges({ roles }: { roles: readonly RoleCode[] }) {
  return (
    <div className="flex flex-wrap gap-1">
      {roles.map((role) => {
        const Icon = ICONS[role];
        return (
          <Badge key={role} tone={role === 'SUPER_ADMIN' ? 'primary' : 'neutral'}>
            <Icon aria-hidden />
            {ROLE_LABELS[role]}
          </Badge>
        );
      })}
    </div>
  );
}
