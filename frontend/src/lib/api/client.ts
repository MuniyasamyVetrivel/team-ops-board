import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';

import type { AuthResponse } from '@/features/auth/types';

/**
 * Shared Axios instance.
 * - The access token is kept in memory only (never localStorage), so it is not exposed to persistent XSS theft.
 * - The refresh token is an httpOnly cookie the browser sends to /api/auth/* automatically.
 * - On a 401 the client refreshes once (single-flight) and retries the original request.
 */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  withCredentials: true,
  headers: { Accept: 'application/json' },
});

const AUTH_ENDPOINTS = ['/auth/login', '/auth/refresh', '/auth/logout'];

let accessToken: string | null = null;
let refreshInFlight: Promise<AuthResponse> | null = null;
let sessionExpiredHandler: (() => void) | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

/** Registers the callback run when the session cannot be refreshed. Returns an unsubscribe function. */
export function onSessionExpired(handler: () => void): () => void {
  sessionExpiredHandler = handler;
  return () => {
    if (sessionExpiredHandler === handler) sessionExpiredHandler = null;
  };
}

/** Exchanges the refresh cookie for a new access token. Concurrent callers share one request. */
export function refreshSession(): Promise<AuthResponse> {
  refreshInFlight ??= api
    .post<AuthResponse>('/auth/refresh')
    .then((response) => {
      setAccessToken(response.data.accessToken);
      return response.data;
    })
    .finally(() => {
      refreshInFlight = null;
    });
  return refreshInFlight;
}

function isAuthEndpoint(url: string | undefined): boolean {
  return url !== undefined && AUTH_ENDPOINTS.some((endpoint) => url.endsWith(endpoint));
}

api.interceptors.request.use((config) => {
  if (accessToken && !isAuthEndpoint(config.url)) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

type RetriableRequest = InternalAxiosRequestConfig & { _retried?: boolean };

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetriableRequest | undefined;
    if (error.response?.status === 401 && original && !original._retried && !isAuthEndpoint(original.url)) {
      original._retried = true;
      try {
        await refreshSession();
        return api(original);
      } catch {
        setAccessToken(null);
        sessionExpiredHandler?.();
      }
    }
    return Promise.reject(error);
  },
);
