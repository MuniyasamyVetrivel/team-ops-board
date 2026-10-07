import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import { webEmployee } from '@/test/fixtures';

import { AuthContext, type AuthContextValue } from './auth-context';
import LoginPage from './LoginPage';

function renderLogin(login: AuthContextValue['login'], initialPath = '/login') {
  const value: AuthContextValue = { status: 'unauthenticated', user: null, login, logout: vi.fn() };
  const router = createMemoryRouter(
    [
      { path: '/login', element: <LoginPage /> },
      { path: '/dashboard', element: <p>Dashboard page</p> },
    ],
    { initialEntries: [initialPath] },
  );
  render(
    <AuthContext.Provider value={value}>
      <RouterProvider router={router} />
    </AuthContext.Provider>,
  );
}

function apiError(status: number, code: string, message: string) {
  const config = { headers: new AxiosHeaders() };
  return new AxiosError(message, String(status), config, null, {
    status,
    statusText: '',
    headers: {},
    config,
    data: { timestamp: '', status, error: '', code, message, path: '/api/auth/login' },
  });
}

describe('LoginPage', () => {
  it('validates required fields before calling the API', async () => {
    const login = vi.fn();
    renderLogin(login);

    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('rejects a malformed email', async () => {
    const login = vi.fn();
    renderLogin(login);

    await userEvent.type(screen.getByLabelText('Email'), 'not-an-email');
    await userEvent.type(screen.getByLabelText('Password'), 'secret');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('signs in with a trimmed email and redirects to the dashboard', async () => {
    const login = vi.fn().mockResolvedValue(webEmployee);
    renderLogin(login);

    await userEvent.type(screen.getByLabelText('Email'), '  rakesh@teamops.local ');
    await userEvent.type(screen.getByLabelText('Password'), 'correct-horse-battery');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(login).toHaveBeenCalledWith({ email: 'rakesh@teamops.local', password: 'correct-horse-battery' });
    expect(await screen.findByText('Dashboard page')).toBeInTheDocument();
  });

  it('shows the server message when credentials are rejected', async () => {
    const login = vi.fn().mockRejectedValue(apiError(401, 'INVALID_CREDENTIALS', 'Invalid email or password'));
    renderLogin(login);

    await userEvent.type(screen.getByLabelText('Email'), 'rakesh@teamops.local');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
  });

  it('explains when the session expired', () => {
    renderLogin(vi.fn(), '/login?expired=1');

    expect(screen.getByText('Your session expired. Please sign in again.')).toBeInTheDocument();
  });

  it('toggles password visibility', async () => {
    renderLogin(vi.fn());
    const password = screen.getByLabelText('Password');

    expect(password).toHaveAttribute('type', 'password');
    await userEvent.click(screen.getByRole('button', { name: 'Show password' }));
    expect(password).toHaveAttribute('type', 'text');
  });
});
