import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { UserListItem } from './api';
import UsersPage from './UsersPage';

const originalAdapter = api.defaults.adapter;

const priya: UserListItem = {
  id: 7,
  email: 'priya.menon@teamops.local',
  firstName: 'Priya',
  lastName: 'Menon',
  fullName: 'Priya Menon',
  jobTitle: 'Digital Marketing Manager',
  department: { id: 7, name: 'Digital Marketing', code: 'DM' },
  roles: ['DEPARTMENT_MANAGER'],
  status: 'ACTIVE',
  lastLoginAt: null,
};

const formerStaff: UserListItem = { ...priya, id: 8, email: 'gone@teamops.local', fullName: 'Former Person', roles: ['EMPLOYEE'], status: 'DISABLED' };

function page(content: UserListItem[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

describe('UsersPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists users with role and status labels', async () => {
    mockApi({
      'GET /users': () => page([priya, formerStaff]),
      'GET /departments': () => [],
    });

    renderPage(<UsersPage />, superAdmin);

    const row = await screen.findByRole('row', { name: 'Open Priya Menon' });
    expect(within(row).getByText('Department Manager')).toBeInTheDocument();
    expect(within(row).getByText('Active')).toBeInTheDocument();
    expect(within(screen.getByRole('row', { name: 'Open Former Person' })).getByText('Disabled')).toBeInTheDocument();
  });

  it('sends filters to the API and resets to the first page', async () => {
    const adapter = mockApi({
      'GET /users': () => page([]),
      'GET /departments': () => [],
    });

    renderPage(<UsersPage />, superAdmin);
    await screen.findByText('No users yet');

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'DISABLED');
    await userEvent.type(screen.getByLabelText('Search users'), 'priya');

    await waitFor(() => {
      const last = adapter.mock.calls.filter(([config]) => config.url === '/users').at(-1)?.[0];
      expect(last?.params).toMatchObject({ status: 'DISABLED', search: 'priya', page: 0, sort: 'name,asc' });
    });
    expect(await screen.findByText('No users match these filters')).toBeInTheDocument();
  });

  it('shows an error state with retry when loading fails', async () => {
    mockApi({
      'GET /users': () => {
        throw new Error('boom');
      },
      'GET /departments': () => [],
    });

    renderPage(<UsersPage />, superAdmin);

    expect(await screen.findByText("Couldn't load users")).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });
});
