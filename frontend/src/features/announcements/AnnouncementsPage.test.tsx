import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { Announcement } from './api';
import AnnouncementsPage from './AnnouncementsPage';

const originalAdapter = api.defaults.adapter;

function announcement(overrides: Partial<Announcement>): Announcement {
  return {
    id: 1,
    title: 'Updated leave policy',
    body: 'Please read the new carry-over rules.',
    priority: 'IMPORTANT',
    targetDepartment: null,
    publishAt: '2026-10-07T05:00:00Z',
    expiresAt: null,
    ackRequired: true,
    createdBy: null,
    state: 'ACTIVE',
    read: false,
    acknowledged: false,
    stats: null,
    canManage: false,
    version: 0,
    ...overrides,
  };
}

const page = (content: Announcement[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });

describe('AnnouncementsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('marks new announcements and lets people acknowledge', async () => {
    const adapter = mockApi({
      'GET /announcements': () => page([announcement({}), announcement({ id: 2, title: 'VPN maintenance', priority: 'URGENT', ackRequired: false, targetDepartment: { id: 1, name: 'IT', code: 'IT' } })]),
      'POST /announcements/1/acknowledge': () => '',
      'POST /announcements/2/read': () => '',
    });

    renderPage(<AnnouncementsPage />, webEmployee);

    const policy = (await screen.findByRole('heading', { name: 'Updated leave policy' })).closest('article')!;
    expect(within(policy).getByText('Important')).toBeInTheDocument();
    expect(within(policy).getByText('New')).toBeInTheDocument();
    expect(within(policy).getByText('Everyone')).toBeInTheDocument();
    const vpn = screen.getByRole('heading', { name: 'VPN maintenance' }).closest('article')!;
    expect(within(vpn).getByText('Urgent')).toBeInTheDocument();

    await userEvent.click(within(policy).getByRole('button', { name: 'Acknowledge' }));
    await userEvent.click(within(vpn).getByRole('button', { name: 'Mark as read' }));
    await waitFor(() => {
      const urls = adapter.mock.calls.filter(([c]) => c.method === 'post').map(([c]) => c.url);
      expect(urls).toEqual(expect.arrayContaining(['/announcements/1/acknowledge', '/announcements/2/read']));
    });
    expect(screen.queryByRole('tab', { name: 'Scheduled' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New announcement' })).not.toBeInTheDocument();
  });

  it('shows managers read and acknowledgement counts and the scheduled tab', async () => {
    const adapter = mockApi({
      'GET /announcements': () => page([announcement({ read: true, acknowledged: true, canManage: true, stats: { audience: 12, read: 9, acknowledged: 7 } })]),
    });

    renderPage(<AnnouncementsPage />, makeUser(['DEPARTMENT_MANAGER'], ['DASHBOARD_VIEW', 'ANNOUNCEMENT_MANAGE']));

    expect(await screen.findByText('Read by 9 of 12 · 7 acknowledged')).toBeInTheDocument();
    expect(screen.getByText('You acknowledged this')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'Scheduled' }));
    await waitFor(() => expect(adapter.mock.calls.at(-1)?.[0].params).toMatchObject({ state: 'SCHEDULED' }));
  });
});
