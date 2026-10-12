import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { celebrationEditor, linkedInPost, linkedInRow, page, priyaUser } from '@/test/celebration-fixtures';
import { webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import LinkedInPostsPage from './LinkedInPostsPage';

const originalAdapter = api.defaults.adapter;

const upcoming = { today: '2026-10-12', from: '2026-10-12', to: '2026-10-25', items: [] };

describe('LinkedInPostsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists what is coming up with its post status, asking for the next 30 days that need a post', async () => {
    const adapter = mockApi({
      'GET /celebrations/upcoming': () => upcoming,
      'GET /celebrations/linkedin-posts': () =>
        page([
          linkedInRow(),
          linkedInRow({
            key: 'EVENT-9-2026-10-18',
            subjectType: 'EVENT',
            subjectId: 9,
            category: 'WEBINAR',
            title: 'SAP webinar',
            occurrenceDate: '2026-10-18',
            region: null,
            linkedIn: linkedInPost({ saved: true, status: 'SCHEDULED', owner: priyaUser, postDate: '2026-10-16', version: 2 }),
          }),
        ]),
    });
    renderPage(<LinkedInPostsPage />, webEmployee);

    const webinar = (await screen.findByRole('link', { name: 'SAP webinar' })).closest('tr')!;
    expect(within(webinar).getByText('Scheduled')).toBeInTheDocument();
    expect(within(webinar).getByText('Priya Menon')).toBeInTheDocument();
    expect(within(webinar).getByText('In 6 days')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Diwali' })).toHaveAttribute('href', '/celebrations/important-days?day=4');
    expect(screen.queryByRole('button', { name: /update the post/i })).not.toBeInTheDocument();

    await waitFor(() => {
      const list = adapter.mock.calls.map(([config]) => config).find((c) => c.url === '/celebrations/linkedin-posts' && c.params?.from);
      expect(list?.params).toMatchObject({ from: '2026-10-12', to: '2026-11-10', needed: true });
    });
  });

  it('lets editors update a post, sending the saved version', async () => {
    const adapter = mockApi({
      'GET /celebrations/upcoming': () => upcoming,
      'GET /celebrations/linkedin-posts': () => page([linkedInRow({ linkedIn: linkedInPost({ saved: true, status: 'DRAFTED', version: 3 }) })]),
      'GET /team': () => page([]),
      'PUT /celebrations/linkedin-posts': (config: InternalAxiosRequestConfig) => ({ ...linkedInPost(), ...JSON.parse(config.data as string) }),
    });
    const user = userEvent.setup();
    renderPage(<LinkedInPostsPage />, celebrationEditor);

    await user.click(await screen.findByRole('button', { name: 'Update the post for Diwali' }));
    const dialog = await screen.findByRole('dialog');
    await user.selectOptions(within(dialog).getByLabelText(/Status/), 'POSTED');
    await user.type(within(dialog).getByLabelText(/Post link/), 'not a link');
    await user.click(within(dialog).getByRole('button', { name: 'Save post' }));
    expect(await within(dialog).findByText('Enter a full link (https://…)')).toBeInTheDocument();

    await user.clear(within(dialog).getByLabelText(/Post link/));
    await user.type(within(dialog).getByLabelText(/Post link/), 'https://www.linkedin.com/posts/tritusa-diwali');
    await user.click(within(dialog).getByRole('button', { name: 'Save post' }));

    await waitFor(() => expect(adapter.mock.calls.some(([config]) => config.method === 'put')).toBe(true));
    const put = adapter.mock.calls.find(([config]) => config.method === 'put')![0];
    expect(JSON.parse(put.data as string)).toEqual({
      subjectType: 'IMPORTANT_DAY',
      subjectId: 4,
      occurrenceDate: '2026-11-08',
      postNeeded: true,
      ownerId: null,
      status: 'POSTED',
      postDate: null,
      postLink: 'https://www.linkedin.com/posts/tritusa-diwali',
      version: 3,
    });
  });
});
