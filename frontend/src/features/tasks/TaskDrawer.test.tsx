import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';
import { taskDetail } from '@/test/task-fixtures';

import { TaskDrawer } from './TaskDrawer';

const originalAdapter = api.defaults.adapter;

const common = {
  'GET /departments': () => [{ id: 5, name: 'Web Development', code: 'WEBDEV', description: null, status: 'ACTIVE', manager: null, memberCount: 3, secondaryMemberCount: 0 }],
  'GET /projects/options': () => [],
};

describe('TaskDrawer', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows the task with its overdue state and changes status through the API', async () => {
    let sent: unknown;
    mockApi({
      ...common,
      'GET /tasks/42': () => taskDetail(),
      'PUT /tasks/42/status': (config) => {
        sent = JSON.parse(config.data as string);
        return taskDetail({ status: 'COMPLETED', dueState: 'NONE', completedAt: '2026-10-07T10:00:00Z', version: 4 });
      },
    });

    renderPage(<TaskDrawer taskId={42} onClose={() => undefined} />, webEmployee);

    expect(await screen.findByRole('heading', { name: 'Fix contact form validation' })).toBeInTheDocument();
    expect(screen.getByText(/Overdue ·/)).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'COMPLETED');

    await waitFor(() => expect(sent).toEqual({ status: 'COMPLETED' }));
    expect(screen.queryByRole('option', { name: 'Cancelled' })).not.toBeInTheDocument();
  });

  it('is read-only for watchers without edit rights', async () => {
    mockApi({
      ...common,
      'GET /tasks/42': () => taskDetail({ permissions: { canEdit: false, canAssign: false, canCancel: false, canComment: true, canWatch: true } }),
    });

    renderPage(<TaskDrawer taskId={42} onClose={() => undefined} />, webEmployee);

    expect(await screen.findByText('View only')).toBeInTheDocument();
    expect(screen.getByLabelText('Status')).toBeDisabled();
    expect(screen.getByLabelText('Priority')).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Edit title' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('New comment')).toBeInTheDocument();
  });

  it('shows the audit timeline in the History tab, loading it only when opened', async () => {
    const user = userEvent.setup();
    let historyRequests = 0;
    const entry = {
      module: 'TASKS',
      moduleLabel: 'Tasks',
      entityType: 'TASK',
      entityTypeLabel: 'Task',
      entityId: 42,
      entityLabel: 'TSK-000042',
      parentType: null,
      parentId: null,
      ipAddress: null,
      userAgent: null,
    };
    mockApi({
      ...common,
      'GET /tasks/42': () => taskDetail(),
      'GET /history/TASK/42': () => {
        historyRequests += 1;
        return {
          content: [
            {
              ...entry,
              id: 2,
              action: 'RECORD_UPDATED',
              actionLabel: 'Task edited',
              actor: null,
              details: { changes: { priority: { from: 'MEDIUM', to: 'HIGH' } } },
              createdAt: '2026-10-08T09:00:00.300Z',
            },
            { ...entry, id: 1, action: 'TASK_STATUS_CHANGED', actionLabel: 'Task status changed', actor: null, details: null, createdAt: '2026-10-08T09:00:00Z' },
          ],
          page: 0,
          size: 50,
          totalElements: 2,
          totalPages: 1,
        };
      },
    });

    renderPage(<TaskDrawer taskId={42} onClose={() => undefined} />, superAdmin);
    await screen.findByRole('heading', { name: 'Fix contact form validation' });
    expect(historyRequests).toBe(0);

    await user.click(screen.getByRole('tab', { name: 'History' }));
    const item = await screen.findByRole('article', { name: 'Task status changed by System' });
    expect(item).toHaveTextContent('Priorityfrom MEDIUMto HIGH');
    expect(screen.getAllByRole('article')).toHaveLength(1);
  });

  it('has no History tab for a normal employee (Super Admin only by default)', async () => {
    let historyRequests = 0;
    mockApi({
      ...common,
      'GET /tasks/42': () => taskDetail(),
      'GET /history/TASK/42': () => {
        historyRequests += 1;
        return { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 };
      },
    });
    renderPage(<TaskDrawer taskId={42} onClose={() => undefined} />, webEmployee);

    await screen.findByRole('heading', { name: 'Fix contact form validation' });
    expect(screen.getByRole('tab', { name: /Comments/ })).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'History' })).not.toBeInTheDocument();
    expect(historyRequests).toBe(0);
  });
});
