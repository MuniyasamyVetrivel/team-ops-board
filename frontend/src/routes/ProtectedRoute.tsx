import { Navigate, Outlet, useLocation } from 'react-router';

import { FullPageLoader } from '@/components/common/FullPageLoader';
import { useAuth } from '@/features/auth/use-auth';

export const CHANGE_PASSWORD_PATH = '/change-password';

/**
 * Renders child routes only for signed-in users; otherwise redirects to /login and remembers the target. A user who
 * still has an admin-chosen password is sent to choose their own first (the backend refuses everything else anyway).
 */
export function ProtectedRoute() {
  const { status, user } = useAuth();
  const location = useLocation();

  if (status === 'loading') return <FullPageLoader label="Restoring your session…" />;
  if (status === 'unauthenticated') return <Navigate to="/login" replace state={{ from: location }} />;
  if (user?.mustChangePassword && location.pathname !== CHANGE_PASSWORD_PATH) {
    return <Navigate to={CHANGE_PASSWORD_PATH} replace state={{ from: location }} />;
  }
  return <Outlet />;
}
