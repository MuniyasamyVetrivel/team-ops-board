import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import type { CalendarResponse } from '@/features/calendar/api';
import { api } from '@/lib/api/client';
import { superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';
import { taskDetail, taskItem } from '@/test/task-fixtures';

import type { DashboardResponse } from './api';
import DashboardPage from './DashboardPage';

const originalAdapter = api.defaults.adapter;

function dashboard(overrides: Partial<DashboardResponse> = {}): DashboardResponse {
  return {
    today: '2026-10-08',
    weekStart: '2026-10-05',
    scope: 'ALL',
    kpis: { openTasks: 142, dueToday: 6, overdue: 17, completedThisWeek: 23, inProgress: 40, blocked: 9, teamMembers: 22, openTickets: 9, slaBreaches: 2, pendingApprovals: null },
    statusDistribution: [
      { status: 'TODO', count: 60 },
      { status: 'IN_PROGRESS', count: 40 },
      { status: 'BLOCKED', count: 9 },
      { status: 'IN_REVIEW', count: 33 },
      { status: 'COMPLETED', count: 51 },
    ],
    completedWindowDays: 30,
    departments: [
      { department: { id: 5, name: 'Web Development', code: 'WEBDEV' }, people: 2, openTasks: 14, overdue: 3, completed: 9, onTimePercent: 78, workloadPercent: 112, level: 'OVERLOADED' },
      { department: { id: 3, name: 'HR', code: 'HR' }, people: 0, openTasks: 0, overdue: 0, completed: 0, onTimePercent: null, workloadPercent: null, level: null },
    ],
    workload: {
      people: 22,
      averagePercent: 48,
      byLevel: { LOW: 10, NORMAL: 6, HIGH: 4, OVERLOADED: 2 },
      windowDays: 14,
      rows: [
        {
          user: { id: 4, fullName: 'Karthik Raj', email: 'karthik.raj@teamops.local', jobTitle: null, status: 'ACTIVE' },
          department: { id: 5, name: 'Web Development', code: 'WEBDEV' },
          totalTasks: 12, todo: 3, inProgress: 3, blocked: 1, inReview: 1, completed: 4, overdue: 3, dueToday: 1, activeTasks: 8,
          remainingHours: 96, capacityHours: 80, workloadPercent: 120, level: 'OVERLOADED',
        },
      ],
    },
    weeklyCompletion: [
      { weekStart: '2026-09-28', completed: 12, onTime: 9, onTimePercent: 75 },
      { weekStart: '2026-10-05', completed: 23, onTime: 20, onTimePercent: 87 },
    ],
    overdueTasks: [taskItem()],
    upcomingTasks: [],
    recentActivity: [
      { id: 1, at: '2026-10-08T09:00:00Z', actorId: 4, actorName: 'Karthik Raj', taskId: 42, taskCode: 'TSK-000042', taskTitle: 'Fix contact form validation', field: 'status', oldValue: 'IN_REVIEW', newValue: 'COMPLETED' },
    ],
    ...overrides,
  };
}

const calendar: CalendarResponse = {
  today: '2026-10-08',
  from: '2026-10-08',
  to: '2026-10-21',
  items: [
    { key: 'EVENT-1', kind: 'EVENT', id: 1, title: 'Karthik Raj on leave', eventType: 'LEAVE', allDay: true, startDate: '2026-10-11', endDate: '2026-10-12', startAt: null, endAt: null, department: { id: 5, name: 'Web Development', code: 'WEBDEV' }, user: null, task: null },
    { key: 'TASK-9', kind: 'TASK_DEADLINE', id: 9, title: 'A deadline', eventType: null, allDay: true, startDate: '2026-10-09', endDate: '2026-10-09', startAt: null, endAt: null, department: null, user: null, task: null },
  ],
};

describe('DashboardPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows company KPIs, with ticket figures linked and unbuilt modules as "—"', async () => {
    mockApi({ 'GET /dashboard': () => dashboard(), 'GET /calendar': () => calendar });

    renderPage(<DashboardPage />, superAdmin);

    const kpis = await screen.findByRole('region', { name: 'Key figures' });
    expect(within(kpis).getByText('142')).toBeInTheDocument();
    expect(within(kpis).getByText('17')).toHaveClass('text-status-danger');
    const approvals = within(kpis).getByText('Pending approvals').closest('div');
    expect(within(approvals!).getByText('—')).toBeInTheDocument();
    expect(within(kpis).getByRole('link', { name: /SLA breaches/ })).toHaveAttribute('href', '/sla');
    expect(within(kpis).getByRole('link', { name: /Open tickets/ })).toHaveTextContent('9');
    expect(screen.getByText(/Company-wide view/)).toBeInTheDocument();
  });

  it('lists department performance with "—" for missing rates and labelled workload levels', async () => {
    mockApi({ 'GET /dashboard': () => dashboard(), 'GET /calendar': () => calendar });

    renderPage(<DashboardPage />, superAdmin);

    const table = (await screen.findByRole('heading', { name: 'Department performance' })).closest('[data-slot="card"]') as HTMLElement;
    const web = within(table).getByText('Web Development').closest('tr')!;
    expect(within(web).getByText('78%')).toBeInTheDocument();
    expect(within(web).getByText('Overloaded')).toBeInTheDocument();
    const hr = within(table).getByText('HR').closest('tr')!;
    expect(within(hr).getAllByText('—')).toHaveLength(2);
  });

  it('shows the employee workload table, recent activity and only calendar events (not deadlines)', async () => {
    mockApi({ 'GET /dashboard': () => dashboard(), 'GET /calendar': () => calendar });

    renderPage(<DashboardPage />, superAdmin);

    const workload = (await screen.findByRole('heading', { name: 'Employee workload' })).closest('[data-slot="card"]') as HTMLElement;
    expect(within(workload).getByRole('meter')).toHaveAttribute('aria-valuenow', '120');
    expect(screen.getByText(/changed status to/)).toHaveTextContent('Completed');
    expect(await screen.findByText('Karthik Raj on leave')).toBeInTheDocument();
    expect(screen.getByText('Leave')).toBeInTheDocument();
    expect(screen.queryByText('A deadline')).not.toBeInTheDocument();
  });

  it('opens a task from the overdue list in the drawer', async () => {
    mockApi({ 'GET /dashboard': () => dashboard(), 'GET /calendar': () => calendar, 'GET /tasks/42': () => taskDetail() });

    renderPage(<DashboardPage />, superAdmin);

    const overdue = (await screen.findByRole('heading', { name: 'Overdue tasks' })).closest('[data-slot="card"]') as HTMLElement;
    await userEvent.click(within(overdue).getByRole('button', { name: /Fix contact form validation/ }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
  });

  it('gives employees a personal dashboard without team sections', async () => {
    mockApi({
      'GET /dashboard': () =>
        dashboard({ scope: 'OWN', departments: [], kpis: { ...dashboard().kpis, teamMembers: null }, workload: { ...dashboard().workload, people: 1 } }),
      'GET /calendar': () => calendar,
    });

    renderPage(<DashboardPage />, webEmployee);

    expect(await screen.findByText('My open tasks')).toBeInTheDocument();
    expect(screen.getByText(/Your work/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'My workload' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Department performance' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Employee workload' })).not.toBeInTheDocument();
    expect(screen.queryByText('Team members')).not.toBeInTheDocument();
  });

  it('shows an error state with retry when the dashboard fails', async () => {
    mockApi({
      'GET /dashboard': () => {
        throw new Error('boom');
      },
    });

    renderPage(<DashboardPage />, superAdmin);

    expect(await screen.findByText("Couldn't load the dashboard")).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});
