import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';

import { onPasswordChangeRequired, onSessionExpired, refreshSession, setAccessToken } from '@/lib/api/client';

import * as authApi from './api';
import { AuthContext, type AuthContextValue, type AuthStatus } from './auth-context';
import type { ChangePasswordInput, CurrentUser, LoginCredentials } from './types';

interface AuthState {
  status: AuthStatus;
  user: CurrentUser | null;
}

const SIGNED_OUT: AuthState = { status: 'unauthenticated', user: null };

/**
 * Owns the session. On first load it restores the session from the httpOnly refresh cookie, so a page reload keeps
 * the user signed in without storing tokens in the browser.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [state, setState] = useState<AuthState>({ status: 'loading', user: null });

  useEffect(() => {
    let cancelled = false;
    refreshSession()
      .then((response) => {
        if (!cancelled) setState({ status: 'authenticated', user: response.user });
      })
      .catch(() => {
        if (!cancelled) setState(SIGNED_OUT);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(
    () =>
      onSessionExpired(() => {
        setAccessToken(null);
        queryClient.clear();
        setState(SIGNED_OUT);
        navigate('/login?expired=1', { replace: true });
      }),
    [navigate, queryClient],
  );

  useEffect(
    () =>
      onPasswordChangeRequired(() =>
        setState((current) => (current.user && !current.user.mustChangePassword ? { ...current, user: { ...current.user, mustChangePassword: true } } : current)),
      ),
    [],
  );

  const login = useCallback(async (credentials: LoginCredentials) => {
    const response = await authApi.login(credentials);
    setState({ status: 'authenticated', user: response.user });
    return response.user;
  }, []);

  const changePassword = useCallback(async (input: ChangePasswordInput) => {
    const response = await authApi.changePassword(input);
    setState({ status: 'authenticated', user: response.user });
    return response.user;
  }, []);

  const logout = useCallback(async () => {
    await authApi.logout().catch(() => undefined);
    queryClient.clear();
    setState(SIGNED_OUT);
    navigate('/login', { replace: true });
  }, [navigate, queryClient]);

  const updateUser = useCallback((patch: Partial<CurrentUser>) => {
    setState((current) => (current.user ? { ...current, user: { ...current.user, ...patch } } : current));
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ status: state.status, user: state.user, login, changePassword, logout, updateUser }),
    [state, login, changePassword, logout, updateUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
