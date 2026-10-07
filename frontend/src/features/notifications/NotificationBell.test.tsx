import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import type { PageResponse } from '@/lib/api/types';
import { webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { AppNotification } from './api';
import { NotificationBell } from './NotificationBell';

const originalAdapter = api.defaults.adapter;

const notifications: PageResponse<AppNotification> = {
  content: [
    { id: 1, type: 'TASK_OVERDUE', title: 'TSK-000042 is overdue', body: 'Fix contact form validation', entityType: 'TASK', entityId: 42, read: false, createdAt: '2026-10-08T02:30:00Z' },
    { id: 2, type: 'TASK_ASSIGNED', title: 'TSK-000043 assigned to you', body: 'Sanjay Varma assigned you "Footer update"', entityType: 'TASK', entityId: 43, read: true, createdAt: '2026-10-07T10:00:00Z' },
  ],
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
};

describe('NotificationBell', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows the unread count and lists notifications with a type label', async () => {
    mockApi({ 'GET /notifications/unread-count': () => ({ unread: 1 }), 'GET /notifications': () => notifications });

    renderPage(<NotificationBell />, webEmployee);

    const bell = await screen.findByRole('button', { name: 'Notifications, 1 unread' });
    await userEvent.click(bell);

    expect(await screen.findByText('TSK-000042 is overdue')).toBeInTheDocument();
    expect(screen.getByText(/^Overdue ·/)).toBeInTheDocument();
    expect(screen.getByText('Unread')).toBeInTheDocument();
  });

  it('marks a notification read when it is opened, and can mark all read', async () => {
    const adapter = mockApi({
      'GET /notifications/unread-count': () => ({ unread: 1 }),
      'GET /notifications': () => notifications,
      'POST /notifications/1/read': () => ({ ...notifications.content[0], read: true }),
      'POST /notifications/read-all': () => ({ unread: 0 }),
    });

    renderPage(<NotificationBell />, webEmployee);
    await userEvent.click(await screen.findByRole('button', { name: 'Notifications, 1 unread' }));
    await userEvent.click(await screen.findByText('TSK-000042 is overdue'));
    await waitFor(() => expect(adapter.mock.calls.some(([config]) => config.url === '/notifications/1/read')).toBe(true));

    await userEvent.click(screen.getByRole('button', { name: 'Notifications, 1 unread' }));
    await userEvent.click(await screen.findByRole('button', { name: /Mark all read/ }));
    await waitFor(() => expect(adapter.mock.calls.some(([config]) => config.url === '/notifications/read-all')).toBe(true));
  });

  it('shows an empty state', async () => {
    mockApi({ 'GET /notifications/unread-count': () => ({ unread: 0 }), 'GET /notifications': () => ({ ...notifications, content: [], totalElements: 0 }) });

    renderPage(<NotificationBell />, webEmployee);
    await userEvent.click(await screen.findByRole('button', { name: 'Notifications' }));

    expect(await screen.findByText('No notifications yet')).toBeInTheDocument();
  });
});
