import { Navigate, Outlet, useLocation } from 'react-router';

import { FullPageLoader } from '@/components/common/FullPageLoader';
import { useAuth } from '@/features/auth/use-auth';

/** Renders child routes only for signed-in users; otherwise redirects to /login and remembers the target. */
export function ProtectedRoute() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') return <FullPageLoader label="Restoring your session…" />;
  if (status === 'unauthenticated') return <Navigate to="/login" replace state={{ from: location }} />;
  return <Outlet />;
}
