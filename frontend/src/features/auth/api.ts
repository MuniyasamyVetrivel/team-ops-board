import { api, setAccessToken } from '@/lib/api/client';

import type { AuthResponse, ChangePasswordInput, LoginCredentials } from './types';

export async function login(credentials: LoginCredentials): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/login', credentials);
  setAccessToken(data.accessToken);
  return data;
}

/** Replaces the signed-in user's password; the server signs out other devices and returns a fresh session. */
export async function changePassword(input: ChangePasswordInput): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/change-password', input);
  setAccessToken(data.accessToken);
  return data;
}

/** Revokes the refresh token server-side and clears the cookie. Always clears the local token. */
export async function logout(): Promise<void> {
  try {
    await api.post('/auth/logout');
  } finally {
    setAccessToken(null);
  }
}
