import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { Route, Routes } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import type { WorkloadResponse } from '@/features/workload/api';
import { api } from '@/lib/api/client';
import { superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';
import { taskDetail } from '@/test/task-fixtures';

import type { TeamMemberProfile } from './api';
import TeamProfilePage from './TeamProfilePage';
import type { TaskHistoryRow, TicketHistoryRow, WorkSummary } from './work-history-api';

const originalAdapter = api.defaults.adapter;

const profile: TeamMemberProfile = {
  member: {
    id: 4,
    fullName: 'Karthik Raj',
    firstName: 'Karthik',
    lastName: 'Raj',
    email: 'karthik.raj@teamops.local',
    jobTitle: 'Frontend Developer',
    department: { id: 5, name: 'Web Development', code: 'WEBDEV' },
    manager: null,
    phone: null,
    location: null,
    workingHours: null,
    status: 'ACTIVE',
  },
  roles: ['EMPLOYEE'],
  memberSince: '2025-01-06T00:00:00Z',
  directReports: [],
  canViewWork: true,
};

const workload: WorkloadResponse = {
  today: '2026-10-12',
  windowDays: 14,
  defaultTaskHours: 4,
  from: null,
  to: null,
  summary: { people: 1, byLevel: { LOW: 0, NORMAL: 1, HIGH: 0, OVERLOADED: 0 }, activeTasks: 1, overdue: 0, dueToday: 0, averagePercent: 50 },
  rows: [],
};

const karthik = { id: 4, fullName: 'Karthik Raj', email: 'karthik.raj@teamops.local', jobTitle: 'Frontend Developer', status: 'ACTIVE' as const };

function summary(overrides: Partial<WorkSummary> = {}): WorkSummary {
  return {
    person: karthik,
    today: '2026-10-12',
    from: null,
    to: null,
    total: 9,
    byStatus: { TODO: 2, IN_PROGRESS: 1, BLOCKED: 0, IN_REVIEW: 1, COMPLETED: 4, CANCELLED: 1 },
    overdue: 3,
    completed: 4,
    completedOnTimePct: 75,
    averageDaysToComplete: 4.5,
    totalActualHours: 26.5,
    completedPerWeek: [
      { weekStart: '2026-09-28', completed: 1 },
      { weekStart: '2026-10-05', completed: 2 },
      { weekStart: '2026-10-12', completed: 1 },
    ],
    ...overrides,
  };
}

function taskRow(overrides: Partial<TaskHistoryRow>): TaskHistoryRow {
  return {
    id: 7,
    code: 'TSK-000007',
    title: 'Ship the release',
    project: { id: 3, code: 'PRJ-0003', name: 'Website relaunch' },
    priority: 'URGENT',
    status: 'COMPLETED',
    startDate: null,
    dueDate: '2026-09-20',
    completedDate: '2026-09-23',
    estimatedHours: 8,
    actualHours: 10,
    overdue: false,
    onTime: false,
    daysLate: 3,
    ...overrides,
  };
}

const ticketRow: TicketHistoryRow = {
  id: 21,
  code: 'TKT-000021',
  subject: 'VPN keeps dropping',
  category: 'Network',
  priority: 'HIGH',
  status: 'RESOLVED',
  createdDate: '2026-10-01',
  dueAt: '2026-10-01T12:00:00Z',
  resolvedDate: '2026-10-01',
  slaState: 'ON_TRACK',
  overdue: false,
  onTime: true,
  daysLate: null,
};

function page<T>(content: T[]) {
  return { content, page: 0, size: 25, totalElements: content.length, totalPages: 1 };
}

const rows = [
  taskRow({}),
  taskRow({ id: 8, code: 'TSK-000008', title: 'Plan the audit', priority: 'LOW', onTime: true, daysLate: null, completedDate: '2026-09-20' }),
  taskRow({ id: 9, code: 'TSK-000009', title: 'Fix the login bug', priority: 'HIGH', status: 'IN_PROGRESS', dueDate: '2026-10-10', completedDate: null, overdue: true, onTime: null, daysLate: 2 }),
];

function routes(extra: Record<string, (config: InternalAxiosRequestConfig) => unknown> = {}) {
  return {
    'GET /team/4': () => profile,
    'GET /workload': () => workload,
    'GET /projects': () => page([]),
    'GET /team/4/tasks/summary': () => summary(),
    'GET /team/4/tasks': () => page(rows),
    ...extra,
  };
}

function renderProfile(viewer = superAdmin, path = '/team/4') {
  return renderPage(
    <Routes>
      <Route path="/team/:id" element={<TeamProfilePage />} />
    </Routes>,
    viewer,
    path,
  );
}

const callsTo = (adapter: ReturnType<typeof mockApi>, url: string) =>
  adapter.mock.calls.map(([config]) => config).filter((config) => config.url === url);

describe('TeamProfilePage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows the work history with status tabs, the summary strip and on-time badges', async () => {
    mockApi(routes());
    renderProfile();

    const tabs = await screen.findByRole('tablist', { name: 'Task status' });
    await within(tabs).findByText('9');
    const names = within(tabs)
      .getAllByRole('tab')
      .map((tab) => tab.textContent);
    expect(names).toEqual(['All9', 'To do2', 'In progress1', 'Blocked0', 'In review1', 'Overdue3', 'Completed4']);
    expect(within(tabs).getByRole('tab', { name: /All/ })).toHaveAttribute('aria-selected', 'true');

    expect(screen.getByText('75%')).toBeInTheDocument();
    expect(screen.getByText('4.5')).toBeInTheDocument();
    expect(screen.getByText('26.5 h')).toBeInTheDocument();
    expect(screen.getByText('Completed per week')).toBeInTheDocument();

    const release = (await screen.findByRole('button', { name: 'Ship the release' })).closest('tr')!;
    expect(within(release).getByText('Late by 3 days')).toBeInTheDocument();
    expect(within(release).getByText('Website relaunch')).toBeInTheDocument();
    const audit = screen.getByRole('button', { name: 'Plan the audit' }).closest('tr')!;
    expect(within(audit).getByText('On time')).toBeInTheDocument();
    const bug = screen.getByRole('button', { name: 'Fix the login bug' }).closest('tr')!;
    expect(within(bug).getByText('Overdue by 2 days')).toBeInTheDocument();
  });

  it('takes the status from the URL, sends filters to the server and keeps the counts unfiltered by status', async () => {
    const adapter = mockApi(routes());
    const user = userEvent.setup();
    renderProfile(superAdmin, '/team/4?status=OVERDUE');

    const tabs = await screen.findByRole('tablist', { name: 'Task status' });
    expect(within(tabs).getByRole('tab', { name: /Overdue/ })).toHaveAttribute('aria-selected', 'true');
    await waitFor(() => expect(callsTo(adapter, '/team/4/tasks')[0]?.params).toMatchObject({ status: ['OVERDUE'], sort: 'updated,desc', page: 0 }));
    expect(callsTo(adapter, '/team/4/tasks/summary')[0]?.params.status).toBeUndefined();

    await user.selectOptions(screen.getByRole('combobox', { name: 'Date field' }), 'DUE');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Date range' }), 'LAST_MONTH');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Priority' }), 'URGENT');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Sort' }), 'priority,desc');
    await waitFor(() =>
      expect(callsTo(adapter, '/team/4/tasks').at(-1)?.params).toMatchObject({ status: ['OVERDUE'], dateField: 'DUE', preset: 'LAST_MONTH', priority: ['URGENT'], sort: 'priority,desc' }),
    );

    await user.selectOptions(screen.getByRole('combobox', { name: 'Date range' }), 'CUSTOM');
    await user.type(screen.getByLabelText('From'), '2026-09-10');
    await user.type(screen.getByLabelText('To'), '2026-09-20');
    await waitFor(() => expect(callsTo(adapter, '/team/4/tasks/summary').at(-1)?.params).toMatchObject({ dateField: 'DUE', from: '2026-09-10', to: '2026-09-20' }));
    expect(callsTo(adapter, '/team/4/tasks/summary').at(-1)?.params.preset).toBeUndefined();

    await user.click(within(tabs).getByRole('tab', { name: /Completed/ }));
    await waitFor(() => expect(callsTo(adapter, '/team/4/tasks').at(-1)?.params).toMatchObject({ status: ['COMPLETED'] }));
  });

  it('opens a task in the drawer and exports the filtered view', async () => {
    const adapter = mockApi(
      routes({
        'GET /tasks/7': () => taskDetail({ id: 7, title: 'Ship the release' }),
        'GET /team/4/tasks/export': () => new Blob(['Task ID\n']),
      }),
    );
    URL.createObjectURL = () => 'blob:export';
    URL.revokeObjectURL = () => undefined;
    const user = userEvent.setup();
    renderProfile(superAdmin, '/team/4?status=COMPLETED');

    await user.click(await screen.findByRole('button', { name: 'Ship the release' }));
    const drawer = await screen.findByRole('dialog');
    expect(await within(drawer).findByText('Ship the release')).toBeInTheDocument();
    await user.keyboard('{Escape}');

    await user.click(screen.getByRole('button', { name: 'Export CSV' }));
    await waitFor(() => expect(adapter.mock.calls.some(([config]) => config.url?.startsWith('/team/4/tasks/export?status=COMPLETED'))).toBe(true));
  });

  it('switches to tickets with SLA met or breached', async () => {
    const adapter = mockApi(
      routes({
        'GET /tickets/categories': () => [{ id: 2, name: 'Network', description: null, departmentId: 1, active: true }],
        'GET /team/4/tickets/summary': () =>
          summary({ total: 1, byStatus: { NEW: 0, OPEN: 0, IN_PROGRESS: 0, WAITING_FOR_REQUESTER: 0, RESOLVED: 1, CLOSED: 0 }, overdue: 0, completed: 1, completedOnTimePct: 100, totalActualHours: null }),
        'GET /team/4/tickets': () => page([ticketRow]),
      }),
    );
    const user = userEvent.setup();
    renderProfile();

    await user.click(await screen.findByRole('button', { name: 'Tickets' }));
    const tabs = await screen.findByRole('tablist', { name: 'Ticket status' });
    expect(within(tabs).getAllByRole('tab')).toHaveLength(8);
    const row = (await screen.findByRole('button', { name: 'VPN keeps dropping' })).closest('tr')!;
    expect(within(row).getByText('SLA met')).toBeInTheDocument();
    expect(screen.queryByText('Hours logged')).not.toBeInTheDocument();
    await user.selectOptions(screen.getByRole('combobox', { name: 'Category' }), '2');
    await waitFor(() => expect(callsTo(adapter, '/team/4/tickets').at(-1)?.params).toMatchObject({ categoryId: 2 }));
  });

  it('shows only tasks to a viewer without ticket access, and no work at all without scope', async () => {
    mockApi(routes());
    renderProfile({ ...webEmployee, id: 4, permissions: webEmployee.permissions.filter((p) => p !== 'TICKET_VIEW') });
    expect(await screen.findByRole('tablist', { name: 'Task status' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Tickets' })).not.toBeInTheDocument();
  });

  it('explains when the viewer may not see this person’s work', async () => {
    mockApi({ 'GET /team/4': () => ({ ...profile, canViewWork: false }) });
    renderProfile(webEmployee);
    expect(await screen.findByText(/visible to Karthik, their department manager and administrators/)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Work history' })).not.toBeInTheDocument();
  });
});
