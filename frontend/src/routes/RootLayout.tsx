import { Suspense } from 'react';
import { Outlet } from 'react-router';

import { FullPageLoader } from '@/components/common/FullPageLoader';
import { AuthProvider } from '@/features/auth/AuthProvider';

/** Top-level route element: AuthProvider needs router context (it navigates on logout and session expiry). */
export function RootLayout() {
  return (
    <AuthProvider>
      <Suspense fallback={<FullPageLoader />}>
        <Outlet />
      </Suspense>
    </AuthProvider>
  );
}
