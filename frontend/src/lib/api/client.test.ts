import { AxiosError, AxiosHeaders, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { webEmployee } from '@/test/fixtures';

import { api, getAccessToken, onSessionExpired, refreshSession, setAccessToken } from './client';

type Handler = (config: InternalAxiosRequestConfig) => Promise<AxiosResponse>;

function ok(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { data, status: 200, statusText: 'OK', headers: {}, config };
}

function fail(config: InternalAxiosRequestConfig, status: number): AxiosError {
  return new AxiosError('failed', String(status), config, null, {
    data: { code: 'X', message: 'failed' },
    status,
    statusText: '',
    headers: new AxiosHeaders(),
    config,
  });
}

function authResponse(token: string) {
  return { accessToken: token, tokenType: 'Bearer', expiresIn: 3600, expiresAt: '', user: webEmployee };
}

describe('api client', () => {
  const originalAdapter = api.defaults.adapter;
  let adapter: ReturnType<typeof vi.fn<Handler>>;

  beforeEach(() => {
    setAccessToken(null);
    adapter = vi.fn<Handler>();
    api.defaults.adapter = adapter;
  });

  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shares one refresh request between concurrent callers', async () => {
    adapter.mockImplementation(async (config) => ok(config, authResponse('fresh')));

    const [first, second] = await Promise.all([refreshSession(), refreshSession()]);

    expect(adapter).toHaveBeenCalledTimes(1);
    expect(first).toBe(second);
    expect(getAccessToken()).toBe('fresh');
  });

  it('attaches the bearer token except on auth endpoints', async () => {
    setAccessToken('abc');
    adapter.mockImplementation(async (config) => ok(config, {}));

    await api.get('/tasks');
    await api.post('/auth/refresh');

    expect(adapter.mock.calls[0]?.[0].headers.Authorization).toBe('Bearer abc');
    expect(adapter.mock.calls[1]?.[0].headers.Authorization).toBeUndefined();
  });

  it('refreshes once on 401 and retries the original request with the new token', async () => {
    setAccessToken('expired');
    let taskCalls = 0;
    adapter.mockImplementation(async (config) => {
      if (config.url === '/auth/refresh') return ok(config, authResponse('renewed'));
      taskCalls += 1;
      if (taskCalls === 1) throw fail(config, 401);
      return ok(config, { items: [] });
    });

    const response = await api.get('/tasks');

    expect(response.data).toEqual({ items: [] });
    expect(adapter.mock.calls.at(-1)?.[0].headers.Authorization).toBe('Bearer renewed');
  });

  it('signals session expiry when the refresh fails', async () => {
    setAccessToken('expired');
    const expired = vi.fn();
    const unsubscribe = onSessionExpired(expired);
    adapter.mockImplementation(async (config) => {
      throw fail(config, 401);
    });

    await expect(api.get('/tasks')).rejects.toBeInstanceOf(AxiosError);

    expect(expired).toHaveBeenCalledTimes(1);
    expect(getAccessToken()).toBeNull();
    unsubscribe();
  });

  it('does not refresh on 403', async () => {
    setAccessToken('valid');
    adapter.mockImplementation(async (config) => {
      throw fail(config, 403);
    });

    await expect(api.get('/admin/users')).rejects.toBeInstanceOf(AxiosError);

    expect(adapter).toHaveBeenCalledTimes(1);
  });
});
