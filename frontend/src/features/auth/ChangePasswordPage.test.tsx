import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import { useState, type ReactNode } from 'react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import { ProtectedRoute } from '@/routes/ProtectedRoute';
import { webEmployee } from '@/test/fixtures';

import { AuthContext, type AuthContextValue } from './auth-context';
import ChangePasswordPage from './ChangePasswordPage';
import type { CurrentUser } from './types';

const newcomer: CurrentUser = { ...webEmployee, firstName: 'Asha', mustChangePassword: true };

/** Like AuthProvider: a successful change replaces the signed-in user. */
function TestAuth({ initial, changePassword, children }: { initial: CurrentUser; changePassword: AuthContextValue['changePassword']; children: ReactNode }) {
  const [user, setUser] = useState(initial);
  const value: AuthContextValue = {
    status: 'authenticated',
    user,
    login: vi.fn(),
    updateUser: vi.fn(),
    changePassword: async (input) => {
      const updated = await changePassword(input);
      setUser(updated);
      return updated;
    },
    logout: vi.fn(),
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function renderApp(user: CurrentUser, changePassword: AuthContextValue['changePassword'] = vi.fn(), initialPath = '/tasks') {
  const router = createMemoryRouter(
    [
      {
        element: <ProtectedRoute />,
        children: [
          { path: '/change-password', element: <ChangePasswordPage /> },
          { path: '/tasks', element: <p>Tasks page</p> },
          { path: '/dashboard', element: <p>Dashboard page</p> },
        ],
      },
    ],
    { initialEntries: [initialPath] },
  );
  render(
    <TestAuth initial={user} changePassword={changePassword}>
      <RouterProvider router={router} />
    </TestAuth>,
  );
}

function apiError(code: string, message: string) {
  const config = { headers: new AxiosHeaders() };
  return new AxiosError(message, '400', config, null, {
    status: 400,
    statusText: '',
    headers: {},
    config,
    data: { timestamp: '', status: 400, error: '', code, message, path: '/api/auth/change-password' },
  });
}

async function fill(current: string, next: string, confirm = next) {
  await userEvent.type(screen.getByLabelText(/Temporary password/), current);
  await userEvent.type(screen.getByLabelText(/^New password/), next);
  await userEvent.type(screen.getByLabelText(/Confirm new password/), confirm);
  await userEvent.click(screen.getByRole('button', { name: 'Save password' }));
}

describe('ChangePasswordPage', () => {
  it('sends a user with an admin-set password here before anything else', async () => {
    renderApp(newcomer);

    expect(await screen.findByRole('heading', { name: 'Choose your password' })).toBeInTheDocument();
    expect(screen.getByText(/Welcome, Asha/)).toBeInTheDocument();
    expect(screen.queryByText('Tasks page')).not.toBeInTheDocument();
  });

  it('lets other users through', async () => {
    renderApp(webEmployee);

    expect(await screen.findByText('Tasks page')).toBeInTheDocument();
  });

  it('checks the password rules before calling the API', async () => {
    const changePassword = vi.fn();
    renderApp(newcomer, changePassword);

    await screen.findByRole('heading', { name: 'Choose your password' });
    await fill('Temp2345abcd', 'short1');
    expect(await screen.findByText('At least 10 characters', { selector: '#newPassword-message' })).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText(/^New password/));
    await userEvent.type(screen.getByLabelText(/^New password/), 'Temp2345abcd');
    await userEvent.clear(screen.getByLabelText(/Confirm new password/));
    await userEvent.type(screen.getByLabelText(/Confirm new password/), 'Temp2345abcd');
    await userEvent.click(screen.getByRole('button', { name: 'Save password' }));
    expect(await screen.findByText('Choose a password different from the current one')).toBeInTheDocument();
    expect(changePassword).not.toHaveBeenCalled();
  });

  it('shows a wrong temporary password next to its field', async () => {
    const changePassword = vi.fn().mockRejectedValue(apiError('WRONG_PASSWORD', 'Your current password is not correct'));
    renderApp(newcomer, changePassword);

    await screen.findByRole('heading', { name: 'Choose your password' });
    await fill('guess', 'MyOwnPass2026');

    expect(await screen.findByText('Your current password is not correct')).toBeInTheDocument();
    expect(screen.getByLabelText(/Temporary password/)).toHaveAttribute('aria-invalid', 'true');
  });

  it('saves the new password and continues to where the user was going', async () => {
    const changePassword = vi.fn().mockResolvedValue({ ...newcomer, mustChangePassword: false });
    renderApp(newcomer, changePassword);

    await screen.findByRole('heading', { name: 'Choose your password' });
    await fill('Temp2345abcd', 'MyOwnPass2026');

    expect(changePassword).toHaveBeenCalledWith({ currentPassword: 'Temp2345abcd', newPassword: 'MyOwnPass2026' });
    expect(await screen.findByText('Tasks page')).toBeInTheDocument();
  });
});
