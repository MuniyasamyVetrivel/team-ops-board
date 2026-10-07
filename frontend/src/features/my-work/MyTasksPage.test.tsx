import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';
import { taskItem } from '@/test/task-fixtures';

import MyTasksPage from './MyTasksPage';

const originalAdapter = api.defaults.adapter;

const summary = { today: '2026-10-07', active: 5, overdue: 2, dueToday: 1, upcoming: 2, inProgress: 2, blocked: 1, completedThisWeek: 3 };

describe('MyTasksPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows summary counts and lists my open tasks by default', async () => {
    const adapter = mockApi({
      'GET /tasks/my/summary': () => summary,
      'GET /tasks': () => ({ content: [taskItem()], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
    });

    renderPage(<MyTasksPage />, webEmployee);

    const overdueCard = await screen.findByRole('tab', { name: /Overdue/ });
    await waitFor(() => expect(within(overdueCard).getByText('2')).toBeInTheDocument());
    expect(await screen.findByRole('row', { name: /TSK-000042/ })).toBeInTheDocument();
    expect(screen.getByText(/Overdue ·/)).toBeInTheDocument();

    const params = adapter.mock.calls.find(([config]) => config.url === '/tasks')?.[0].params;
    expect(params).toMatchObject({ view: 'ASSIGNED_TO_ME', status: ['TODO', 'IN_PROGRESS', 'BLOCKED', 'IN_REVIEW'] });
  });

  it('switches to the due-today view when its card is clicked', async () => {
    const adapter = mockApi({
      'GET /tasks/my/summary': () => summary,
      'GET /tasks': () => ({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    });

    renderPage(<MyTasksPage />, webEmployee);
    await userEvent.click(await screen.findByRole('tab', { name: /Due today/ }));

    expect(await screen.findByText('Nothing due today.')).toBeInTheDocument();
    const last = adapter.mock.calls.filter(([config]) => config.url === '/tasks').at(-1)?.[0].params;
    expect(last).toMatchObject({ view: 'ASSIGNED_TO_ME', due: 'TODAY' });
  });
});
