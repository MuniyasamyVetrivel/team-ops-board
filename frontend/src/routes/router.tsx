import { lazy, type ComponentType } from 'react';
import { createBrowserRouter, Navigate, type RouteObject } from 'react-router';

import { NAV_ITEMS } from '@/config/navigation';
import { AppShell } from '@/layouts/AppShell';

import { ProtectedRoute } from './ProtectedRoute';
import { RequirePermission } from './RequirePermission';
import { RootLayout } from './RootLayout';

const LoginPage = lazy(() => import('@/features/auth/LoginPage'));
const DashboardPage = lazy(() => import('@/features/dashboard/DashboardPage'));
const ComingSoonPage = lazy(() => import('@/pages/ComingSoonPage'));
const UnauthorizedPage = lazy(() => import('@/pages/UnauthorizedPage'));
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'));
const UsersPage = lazy(() => import('@/features/admin/users/UsersPage'));
const DepartmentsPage = lazy(() => import('@/features/admin/departments/DepartmentsPage'));
const TeamPage = lazy(() => import('@/features/team/TeamPage'));
const TeamProfilePage = lazy(() => import('@/features/team/TeamProfilePage'));

/** Pages that exist so far. Every other sidebar entry renders a placeholder naming the phase that builds it. */
const IMPLEMENTED_PAGES: Record<string, ComponentType> = {
  '/dashboard': DashboardPage,
  '/admin/users': UsersPage,
  '/admin/departments': DepartmentsPage,
  '/team': TeamPage,
};

/** Detail routes that are not sidebar entries. */
const detailRoutes: RouteObject[] = [
  {
    path: '/team/:id',
    element: (
      <RequirePermission permission="TEAM_VIEW">
        <TeamProfilePage />
      </RequirePermission>
    ),
  },
];

const moduleRoutes: RouteObject[] = NAV_ITEMS.map((item) => {
  const Page = IMPLEMENTED_PAGES[item.path];
  return {
    path: item.path,
    element: (
      <RequirePermission permission={item.permission}>
        {Page ? <Page /> : <ComingSoonPage item={item} />}
      </RequirePermission>
    ),
  };
});

export const routes: RouteObject[] = [
  {
    element: <RootLayout />,
    children: [
      { path: '/login', element: <LoginPage /> },
      {
        element: <ProtectedRoute />,
        children: [
          {
            element: <AppShell />,
            children: [
              { index: true, element: <Navigate to="/dashboard" replace /> },
              ...moduleRoutes,
              ...detailRoutes,
              { path: '/unauthorized', element: <UnauthorizedPage /> },
              { path: '*', element: <NotFoundPage /> },
            ],
          },
        ],
      },
    ],
  },
];

export const router = createBrowserRouter(routes);
