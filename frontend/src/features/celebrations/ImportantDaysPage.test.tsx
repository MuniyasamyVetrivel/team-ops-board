import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { celebrationEditor, importantDay, linkedInPost, page } from '@/test/celebration-fixtures';
import { webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import ImportantDaysPage from './ImportantDaysPage';

const originalAdapter = api.defaults.adapter;

describe('ImportantDaysPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists days with region and LinkedIn status; viewers can export but not change', async () => {
    mockApi({
      'GET /celebrations/important-days': () => page([importantDay(), importantDay({ id: 5, name: 'Christmas', region: 'GLOBAL', recurrence: 'YEARLY', linkedIn: linkedInPost({ needed: false }) })]),
    });
    renderPage(<ImportantDaysPage />, webEmployee);

    const diwali = (await screen.findByRole('button', { name: 'Diwali' })).closest('tr')!;
    expect(within(diwali).getByText('India')).toBeInTheDocument();
    expect(within(diwali).getByText('Not started')).toBeInTheDocument();
    const christmas = screen.getByRole('button', { name: 'Christmas' }).closest('tr')!;
    expect(within(christmas).getByText('No post needed')).toBeInTheDocument();
    expect(within(christmas).getByText(/Every year/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /export csv/i })).toBeEnabled();
    expect(screen.queryByRole('button', { name: /new important day/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /import csv/i })).not.toBeInTheDocument();
  });

  it('shows the drawer with notes, the next post and an update button for editors', async () => {
    mockApi({
      'GET /celebrations/important-days': () => page([importantDay({ canEdit: true })]),
      'GET /celebrations/important-days/4': () => importantDay({ canEdit: true }),
      'GET /celebrations/important-days/4/attachments': () => [],
      'GET /marketing/imports': () => [],
      'GET /team': () => page([]),
    });
    const user = userEvent.setup();
    renderPage(<ImportantDaysPage />, celebrationEditor, '/celebrations/important-days?day=4');

    const drawer = await screen.findByRole('dialog');
    expect(await within(drawer).findByText('Greeting creative and a LinkedIn post.')).toBeInTheDocument();
    await user.click(within(drawer).getByRole('button', { name: 'Update LinkedIn post' }));
    expect(await screen.findByRole('heading', { name: 'LinkedIn post' })).toBeInTheDocument();
  });
});
