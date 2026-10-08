import type { ReactNode } from 'react';
import { Navigate } from 'react-router';

import { hasPermission, type PermissionCode } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';

interface RequirePermissionProps {
  /** One permission, or several that are all required. */
  permission?: PermissionCode | readonly PermissionCode[];
  children: ReactNode;
}

/** UX guard only - the API independently returns 403 for the same rule. */
export function RequirePermission({ permission, children }: RequirePermissionProps) {
  const { user } = useAuth();
  const required = permission === undefined ? [] : typeof permission === 'string' ? [permission] : permission;
  if (required.some((code) => !hasPermission(user, code))) {
    return <Navigate to="/unauthorized" replace />;
  }
  return children;
}
