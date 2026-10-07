import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { WorkloadResponse, WorkloadRow } from './api';
import WorkloadPage from './WorkloadPage';

const originalAdapter = api.defaults.adapter;

function row(id: number, name: string, percent: number, level: WorkloadRow['level'], overdue: number): WorkloadRow {
  return {
    user: { id, fullName: name, email: `${id}@teamops.local`, jobTitle: null, status: 'ACTIVE' },
    department: { id: 5, name: 'Web Development', code: 'WEBDEV' },
    totalTasks: 10,
    todo: 3,
    inProgress: 2,
    blocked: 1,
    inReview: 0,
    completed: 4,
    overdue,
    dueToday: 1,
    activeTasks: 6,
    remainingHours: percent * 0.8,
    capacityHours: 80,
    workloadPercent: percent,
    level,
  };
}

const response: WorkloadResponse = {
  today: '2026-10-07',
  windowDays: 14,
  defaultTaskHours: 4,
  from: null,
  to: null,
  summary: { people: 2, byLevel: { LOW: 1, NORMAL: 0, HIGH: 0, OVERLOADED: 1 }, activeTasks: 12, overdue: 3, dueToday: 2, averagePercent: 70 },
  rows: [row(4, 'Karthik Raj', 120, 'OVERLOADED', 3), row(9, 'Manoj Thomas', 5, 'LOW', 0)],
};

describe('WorkloadPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows each person with workload %, a level label and overdue counts', async () => {
    mockApi({ 'GET /workload': () => response, 'GET /departments': () => [] });

    renderPage(<WorkloadPage />, superAdmin);

    const karthik = (await screen.findByText('Karthik Raj')).closest('tr');
    expect(karthik).not.toBeNull();
    expect(within(karthik!).getByText('120%')).toBeInTheDocument();
    expect(within(karthik!).getByText('Overloaded')).toBeInTheDocument();
    expect(within(karthik!).getByRole('meter')).toHaveAttribute('aria-valuenow', '120');
    const manoj = screen.getByText('Manoj Thomas').closest('tr');
    expect(within(manoj!).getByText('Low')).toBeInTheDocument();
  });

  it('sends the chosen sort and only sends a complete date range', async () => {
    const adapter = mockApi({ 'GET /workload': () => response, 'GET /departments': () => [] });

    renderPage(<WorkloadPage />, superAdmin);
    await screen.findByText('Karthik Raj');

    await userEvent.selectOptions(screen.getByLabelText('Sort'), 'MOST_OVERDUE');
    await userEvent.type(screen.getByLabelText('Due from'), '2026-10-01');

    await waitFor(() => {
      const last = adapter.mock.calls.at(-1)?.[0].params;
      expect(last).toMatchObject({ sort: 'MOST_OVERDUE' });
      expect(last).not.toHaveProperty('from');
    });

    await userEvent.type(screen.getByLabelText('Due to'), '2026-10-31');
    await waitFor(() => expect(adapter.mock.calls.at(-1)?.[0].params).toMatchObject({ from: '2026-10-01', to: '2026-10-31' }));
  });
});
