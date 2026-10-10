import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { ProjectReport, TaskReport, TicketReport, WorkloadReport } from './api';
import ReportsPage from './ReportsPage';

const originalAdapter = api.defaults.adapter;

const manager = makeUser(['DEPARTMENT_MANAGER'], ['TASK_VIEW', 'WORKLOAD_VIEW', 'TICKET_VIEW', 'PROJECT_VIEW', 'TEAM_VIEW', 'REPORT_VIEW', 'REPORT_EXPORT']);
const tasksOnly = makeUser(['DEPARTMENT_MANAGER'], ['TASK_VIEW', 'REPORT_VIEW']);

const web = { id: 3, name: 'Web Development', code: 'WEBDEV' };
const karthik = { id: 4, fullName: 'Karthik Raj', email: 'karthik.raj@teamops.local', jobTitle: 'Frontend Developer', status: 'ACTIVE' as const };

function taskReport(from: string, to: string, created: number, completed: number, onTimePct: number): TaskReport {
  return {
    range: { from, to },
    summary: { created, completed, completedWithDueDate: 6, completedOnTime: 5, onTimePct, open: 10, overdue: 2, overduePct: 20, hoursLogged: 31.5 },
    openByStatus: [
      { status: 'TODO', tasks: 5 },
      { status: 'IN_PROGRESS', tasks: 3 },
      { status: 'BLOCKED', tasks: 1 },
      { status: 'IN_REVIEW', tasks: 1 },
    ],
    departments: [{ department: web, created, completed, onTimePct, open: 10, overdue: 2, overduePct: 20 }],
    employees: [{ user: karthik, department: web, completed, onTimePct, open: 4, overdue: 1, hoursLogged: 12 }],
    trend: [
      { weekStart: '2026-09-28', created: 4, completed: 3 },
      { weekStart: '2026-10-05', created: 3, completed: 2 },
    ],
  };
}

const last30 = taskReport('2026-09-10', '2026-10-09', 12, 8, 83);
const before30 = taskReport('2026-08-11', '2026-09-09', 10, 6, 75);
const september = taskReport('2026-09-01', '2026-09-30', 11, 9, 90);
const august = taskReport('2026-08-01', '2026-08-31', 9, 9, 80);

const workload: WorkloadReport = {
  employees: {
    today: '2026-10-09',
    windowDays: 14,
    defaultTaskHours: 4,
    from: null,
    to: null,
    summary: { people: 1, byLevel: { LOW: 0, NORMAL: 0, HIGH: 1, OVERLOADED: 0 }, activeTasks: 4, overdue: 1, dueToday: 0, averagePercent: 85 },
    rows: [{ user: karthik, department: web, totalTasks: 6, todo: 2, inProgress: 2, blocked: 0, inReview: 0, completed: 2, overdue: 1, dueToday: 0, activeTasks: 4, remainingHours: 68, capacityHours: 80, workloadPercent: 85, level: 'HIGH' }],
  },
  departments: [{ department: web, people: 1, activeTasks: 4, overdue: 1, remainingHours: 68, capacityHours: 80, workloadPercent: 85, level: 'HIGH' }],
};

const tickets: TicketReport = {
  range: { from: '2026-09-10', to: '2026-10-09' },
  summary: { created: 19, resolved: 14, open: 6, openBreached: 2, firstResponseCompliance: 89, resolutionCompliance: 78, averageResolutionHours: 20.5 },
  priorities: [
    { priority: 'URGENT', created: 2, resolved: 2, open: 0, firstResponseCompliance: 100, resolutionCompliance: 50 },
    { priority: 'HIGH', created: 5, resolved: 4, open: 1, firstResponseCompliance: 80, resolutionCompliance: 75 },
    { priority: 'MEDIUM', created: 8, resolved: 6, open: 3, firstResponseCompliance: 88, resolutionCompliance: 88 },
    { priority: 'LOW', created: 4, resolved: 2, open: 2, firstResponseCompliance: null, resolutionCompliance: null },
  ],
  departments: [{ department: { id: 1, name: 'IT', code: 'IT' }, created: 19, resolved: 14, open: 6, openBreached: 2, resolutionCompliance: 78 }],
  ageing: [
    { label: 'Under 1 day', tickets: 1 },
    { label: '1–3 days', tickets: 2 },
    { label: '3–7 days', tickets: 2 },
    { label: '7–30 days', tickets: 1 },
    { label: 'Over 30 days', tickets: 0 },
  ],
  openByStatus: [
    { status: 'NEW', tickets: 1 },
    { status: 'OPEN', tickets: 3 },
    { status: 'IN_PROGRESS', tickets: 1 },
    { status: 'WAITING_FOR_REQUESTER', tickets: 1 },
  ],
};

const projects: ProjectReport = {
  today: '2026-10-09',
  byStatus: [
    { status: 'PLANNING', projects: 0 },
    { status: 'ACTIVE', projects: 1 },
    { status: 'ON_HOLD', projects: 0 },
    { status: 'COMPLETED', projects: 0 },
    { status: 'CANCELLED', projects: 0 },
  ],
  projects: [
    { id: 1, code: 'PRJ-0001', name: 'Website relaunch', status: 'ACTIVE', department: web, owner: karthik, startDate: '2026-08-01', endDate: '2026-10-01', progress: 64, tasks: 14, tasksCompleted: 9, tasksOverdue: 2, milestones: 4, milestonesCompleted: 2, milestonesOverdue: 1, openRisks: 1, pastEndDate: true },
  ],
};

type Handler = (config: InternalAxiosRequestConfig) => unknown;
function routes(requests: InternalAxiosRequestConfig[] = [], extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /departments': () => [{ ...web, description: null, status: 'ACTIVE', manager: null, memberCount: 3, secondaryMemberCount: 0 }],
    'GET /team': () => ({ content: [{ ...karthik, firstName: 'Karthik', lastName: 'Raj', department: web, manager: null, phone: null, location: null, workingHours: null }], page: 0, size: 100, totalElements: 1, totalPages: 1 }),
    'GET /projects/options': () => [{ id: 1, code: 'PRJ-0001', name: 'Website relaunch' }],
    'GET /reports/tasks': (config) => {
      requests.push(config);
      const from = (config.params as { from?: string }).from;
      return from === '2026-08-11' ? before30 : from === '2026-09-01' ? september : from === '2026-08-01' ? august : last30;
    },
    'GET /reports/workload': () => workload,
    'GET /reports/tickets': () => tickets,
    'GET /reports/projects': () => projects,
    ...extra,
  };
}

describe('ReportsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows the task report against the previous 30 days', async () => {
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes(requests));
    renderPage(<ReportsPage />, manager);

    const figures = await screen.findByRole('region', { name: 'Task figures' });
    expect(screen.getByText('10 Sep – 9 Oct 2026 · compared with 11 Aug – 9 Sep 2026')).toBeInTheDocument();
    // The default range comes from the server; the comparison asks for the 30 days before it.
    expect(requests[0]?.params).toEqual({});
    expect(requests.some((r) => (r.params as { from?: string }).from === '2026-08-11')).toBe(true);
    const completed = within(figures).getByText('Tasks completed').closest('div')!;
    expect(completed).toHaveTextContent('8');
    expect(await within(completed).findByText('33.3% vs 11 Aug – 9 Sep 2026')).toHaveClass('text-status-success');
    expect(within(figures).getByText('Overdue now').closest('div')).toHaveTextContent('20% of open tasks');
    // Rates change by points, not by a percentage of a percentage.
    expect(within(figures).getByText('On time').closest('div')).toHaveTextContent('Up 8 pts vs 11 Aug – 9 Sep 2026');

    expect(screen.getByRole('figure', { name: 'Tasks created and completed per week' })).toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Department performance' })).getByText('Web Development').closest('tr')).toHaveTextContent('Web Development12883%10220%');
    expect(within(screen.getByRole('table', { name: 'Employee productivity' })).getByText('Karthik Raj')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Export CSV' })).toBeEnabled();
  });

  it('compares last month with the month before (September → August)', async () => {
    const user = userEvent.setup();
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes(requests));
    renderPage(<ReportsPage />, manager);

    await screen.findByRole('region', { name: 'Task figures' });
    await user.selectOptions(screen.getByLabelText('Period'), 'LAST_MONTH');
    expect(await screen.findByText('September 2026 · compared with August 2026')).toBeInTheDocument();
    expect(requests.some((r) => JSON.stringify(r.params) === JSON.stringify({ from: '2026-09-01', to: '2026-09-30' }))).toBe(true);
    expect(requests.some((r) => (r.params as { from?: string; to?: string }).to === '2026-08-31')).toBe(true);

    await user.selectOptions(screen.getByLabelText('Department'), '3');
    await user.selectOptions(screen.getByLabelText('Task status'), 'TODO');
    expect(requests.at(-1)?.params).toMatchObject({ departmentId: 3, status: ['TODO'] });
  });

  it('shows workload, tickets and projects on their tabs', async () => {
    const user = userEvent.setup();
    mockApi(routes());
    renderPage(<ReportsPage />, manager);

    await user.click(await screen.findByRole('tab', { name: 'Workload' }));
    const departments = await screen.findByRole('table', { name: 'Department workload' });
    expect(within(departments).getByText('Web Development').closest('tr')).toHaveTextContent('85%');
    expect(within(screen.getByRole('table', { name: 'Employee workload' })).getAllByText('High').length).toBeGreaterThan(0);

    await user.click(screen.getByRole('tab', { name: 'Tickets' }));
    const ticketFigures = await screen.findByRole('region', { name: 'Ticket figures' });
    expect(within(ticketFigures).getByText('Resolution SLA').closest('div')).toHaveTextContent('78%');
    expect(within(screen.getByRole('list', { name: 'Open ticket ageing' })).getAllByRole('listitem')).toHaveLength(5);
    expect(within(screen.getByRole('table', { name: 'Tickets by priority' })).getByText('Urgent')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Projects' }));
    const table = await screen.findByRole('table', { name: 'Project progress' });
    const row = within(table).getByText('Website relaunch').closest('tr')!;
    expect(within(row).getByRole('progressbar', { name: 'Website relaunch progress' })).toHaveAttribute('aria-valuenow', '64');
    expect(within(row).getByText('Past end date')).toBeInTheDocument();
    expect(row).toHaveTextContent('9 / 14');
  });

  it('only offers the reports and export the viewer may use', async () => {
    mockApi(routes());
    renderPage(<ReportsPage />, tasksOnly);

    await screen.findByRole('region', { name: 'Task figures' });
    expect(screen.getAllByRole('tab').map((t) => t.textContent)).toEqual(['Tasks']);
    expect(screen.queryByRole('button', { name: 'Export CSV' })).not.toBeInTheDocument();
    // Without TEAM_VIEW there is no employee list to pick from.
    expect(screen.queryByLabelText('Employee')).not.toBeInTheDocument();
  });
});
