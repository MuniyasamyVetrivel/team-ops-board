import type { ReactNode } from 'react';
import { Navigate } from 'react-router';

import { hasPermission, type PermissionCode } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';

interface RequirePermissionProps {
  permission?: PermissionCode;
  children: ReactNode;
}

/** UX guard only - the API independently returns 403 for the same rule. */
export function RequirePermission({ permission, children }: RequirePermissionProps) {
  const { user } = useAuth();
  if (permission && !hasPermission(user, permission)) {
    return <Navigate to="/unauthorized" replace />;
  }
  return children;
}
