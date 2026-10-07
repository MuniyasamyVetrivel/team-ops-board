import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { webEmployee } from '@/test/fixtures';
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
});
