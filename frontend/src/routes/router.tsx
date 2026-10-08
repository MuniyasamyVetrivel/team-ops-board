import { lazy, type ComponentType } from 'react';
import { createBrowserRouter, Navigate, type RouteObject } from 'react-router';

import { NAV_ITEMS, type RouteItem } from '@/config/navigation';
import { MarketingLayout } from '@/features/marketing/MarketingLayout';
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
const TasksPage = lazy(() => import('@/features/tasks/TasksPage'));
const MyTasksPage = lazy(() => import('@/features/my-work/MyTasksPage'));
const WorkloadPage = lazy(() => import('@/features/workload/WorkloadPage'));
const TicketsPage = lazy(() => import('@/features/tickets/TicketsPage'));
const MyTicketsPage = lazy(() => import('@/features/my-work/MyTicketsPage'));
const SlaPage = lazy(() => import('@/features/sla/SlaPage'));
const ProjectsPage = lazy(() => import('@/features/projects/ProjectsPage'));
const ProjectDetailPage = lazy(() => import('@/features/projects/ProjectDetailPage'));
const ApprovalsPage = lazy(() => import('@/features/approvals/ApprovalsPage'));
const AnnouncementsPage = lazy(() => import('@/features/announcements/AnnouncementsPage'));
const KnowledgeBasePage = lazy(() => import('@/features/knowledge/KnowledgeBasePage'));
const ArticlePage = lazy(() => import('@/features/knowledge/ArticlePage'));
const DocumentsPage = lazy(() => import('@/features/documents/DocumentsPage'));
const CalendarPage = lazy(() => import('@/features/calendar/CalendarPage'));
const MarketingHomePage = lazy(() => import('@/features/marketing/MarketingHomePage'));
const SeoRankingsPage = lazy(() => import('@/features/marketing/seo/SeoRankingsPage'));
const SeoPageDetailPage = lazy(() => import('@/features/marketing/seo/SeoPageDetailPage'));
const TargetsPage = lazy(() => import('@/features/marketing/targets/TargetsPage'));
const ActivitiesPage = lazy(() => import('@/features/marketing/activities/ActivitiesPage'));
const ActivityDetailPage = lazy(() => import('@/features/marketing/activities/ActivityDetailPage'));
const EmailCampaignsPage = lazy(() => import('@/features/marketing/email/EmailCampaignsPage'));

/** Pages that exist so far. Every other sidebar entry renders a placeholder naming the phase that builds it. */
const IMPLEMENTED_PAGES: Record<string, ComponentType> = {
  '/dashboard': DashboardPage,
  '/admin/users': UsersPage,
  '/admin/departments': DepartmentsPage,
  '/team': TeamPage,
  '/tasks': TasksPage,
  '/my/tasks': MyTasksPage,
  '/workload': WorkloadPage,
  '/tickets': TicketsPage,
  '/my/tickets': MyTicketsPage,
  '/sla': SlaPage,
  '/projects': ProjectsPage,
  '/approvals': ApprovalsPage,
  '/announcements': AnnouncementsPage,
  '/knowledge-base': KnowledgeBasePage,
  '/documents': DocumentsPage,
  '/my/calendar': CalendarPage,
  // The marketing home until the executive dashboard replaces it in Phase 19.
  '/digital-marketing': MarketingHomePage,
  '/digital-marketing/seo': SeoRankingsPage,
  '/digital-marketing/targets': TargetsPage,
  '/digital-marketing/activities': ActivitiesPage,
  '/digital-marketing/email-campaigns': EmailCampaignsPage,
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
  {
    path: '/projects/:id',
    element: (
      <RequirePermission permission="PROJECT_VIEW">
        <ProjectDetailPage />
      </RequirePermission>
    ),
  },
  {
    path: '/knowledge-base/:slug',
    element: (
      <RequirePermission permission="KB_VIEW">
        <ArticlePage />
      </RequirePermission>
    ),
  },
];

/** Marketing detail routes sit inside MarketingLayout too, so the selected month carries over. */
const marketingDetailRoutes: RouteObject[] = [
  {
    path: '/digital-marketing/seo/pages/:id',
    element: (
      <RequirePermission permission={['MARKETING_VIEW', 'SEO_VIEW']}>
        <SeoPageDetailPage />
      </RequirePermission>
    ),
  },
  {
    path: '/digital-marketing/activities/:id',
    element: (
      <RequirePermission permission="MARKETING_VIEW">
        <ActivityDetailPage />
      </RequirePermission>
    ),
  },
];

function moduleRoute(item: RouteItem): RouteObject {
  const Page = IMPLEMENTED_PAGES[item.path];
  return {
    path: item.path,
    element: (
      <RequirePermission permission={item.requires}>
        {Page ? <Page /> : <ComingSoonPage item={item} />}
      </RequirePermission>
    ),
  };
}

/** Digital Marketing pages share one layout, which keeps the month/year/owner filters while moving between them. */
const moduleRoutes: RouteObject[] = [
  ...NAV_ITEMS.filter((item) => item.sectionId !== 'digital-marketing').map(moduleRoute),
  { element: <MarketingLayout />, children: [...NAV_ITEMS.filter((item) => item.sectionId === 'digital-marketing').map(moduleRoute), ...marketingDetailRoutes] },
];

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
