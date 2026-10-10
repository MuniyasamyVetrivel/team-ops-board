import { screen, within } from '@testing-library/react';
import type { InternalAxiosRequestConfig } from 'axios';
import { Route, Routes } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import type { WorkloadResponse } from '@/features/workload/api';
import { api } from '@/lib/api/client';
import { superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';
import { taskItem } from '@/test/task-fixtures';
import { slaStatus, ticketItem } from '@/test/ticket-fixtures';

import type { TeamMemberProfile } from './api';
import TeamProfilePage from './TeamProfilePage';

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
  today: '2026-10-07',
  windowDays: 14,
  defaultTaskHours: 4,
  from: null,
  to: null,
  summary: { people: 1, byLevel: { LOW: 0, NORMAL: 1, HIGH: 0, OVERLOADED: 0 }, activeTasks: 1, overdue: 0, dueToday: 0, averagePercent: 50 },
  rows: [],
};

function page<T>(content: T[]) {
  return { content, page: 0, size: content.length, totalElements: content.length, totalPages: 1 };
}

/** Answers the three task lists on the profile by their query: current, recent activity and recently completed. */
function tasks(config: InternalAxiosRequestConfig) {
  const params = config.params as { status?: string[]; sort?: string };
  if (params.status?.length === 1 && params.status[0] === 'COMPLETED') {
    return page([taskItem({ id: 3, code: 'TSK-000003', title: 'Ship the pricing page', status: 'COMPLETED' })]);
  }
  if (params.sort === 'updated,desc') return page([taskItem({ id: 2, code: 'TSK-000002', title: 'Review the header', status: 'IN_REVIEW' })]);
  return page([]);
}

function renderProfile(viewer = superAdmin) {
  return renderPage(
    <Routes>
      <Route path="/team/:id" element={<TeamProfilePage />} />
    </Routes>,
    viewer,
    '/team/4',
  );
}

function card(title: string) {
  return screen.getByText(title).closest<HTMLElement>('[data-slot="card"]')!;
}

describe('TeamProfilePage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows open tickets, recent activity and recently completed work', async () => {
    const breached = { firstResponse: slaStatus(), resolution: slaStatus({ state: 'BREACHED' }), overall: 'BREACHED' as const };
    const adapter = mockApi({
      'GET /team/4': () => profile,
      'GET /workload': () => workload,
      'GET /tasks': tasks,
      'GET /tickets': () => page([ticketItem({ subject: 'VPN keeps dropping', sla: breached })]),
    });
    renderProfile();

    await screen.findByText('Open tickets');
    expect(await within(card('Open tickets')).findByText('VPN keeps dropping')).toBeInTheDocument();
    expect(within(card('Open tickets')).getByText('Breached')).toBeInTheDocument();
    expect(await within(card('Recent activity')).findByText('Review the header')).toBeInTheDocument();
    expect(await within(card('Recently completed')).findByText('Ship the pricing page')).toBeInTheDocument();

    const ticketCall = adapter.mock.calls.map(([config]) => config).find((config) => config.url === '/tickets');
    expect(ticketCall?.params).toMatchObject({ assigneeId: 4, sort: 'due,asc' });
    expect(screen.queryByText(/Phase/)).not.toBeInTheDocument();
  });

  it('leaves tickets out for a viewer without ticket access', async () => {
    mockApi({ 'GET /team/4': () => profile, 'GET /workload': () => workload, 'GET /tasks': tasks });
    renderProfile({ ...webEmployee, permissions: webEmployee.permissions.filter((p) => p !== 'TICKET_VIEW') });

    expect(await screen.findByText('Recent activity')).toBeInTheDocument();
    expect(screen.queryByText('Open tickets')).not.toBeInTheDocument();
  });
});
