import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import type { ReactElement } from 'react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/features/auth/auth-context';
import type { CurrentUser } from '@/features/auth/types';
import { api } from '@/lib/api/client';

type Handler = (config: InternalAxiosRequestConfig) => unknown;

/**
 * Routes API calls to in-memory handlers keyed by "METHOD /path" (path without query string). Returns the mock so
 * tests can assert on requests. Unknown routes fail the test loudly.
 */
export function mockApi(routes: Record<string, Handler>) {
  const adapter = vi.fn(async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    const key = `${(config.method ?? 'get').toUpperCase()} ${config.url}`;
    const handler = routes[key];
    if (!handler) throw new Error(`Unmocked API call: ${key}`);
    return { data: handler(config), status: 200, statusText: 'OK', headers: {}, config };
  });
  api.defaults.adapter = adapter;
  return adapter;
}

/** Renders a page with React Query, an authenticated user and a router. */
export function renderPage(element: ReactElement, user: CurrentUser, path = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const auth: AuthContextValue = { status: 'authenticated', user, login: vi.fn(), logout: vi.fn() };
  const router = createMemoryRouter([{ path: '*', element }], { initialEntries: [path] });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthContext.Provider value={auth}>
        <RouterProvider router={router} />
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}
