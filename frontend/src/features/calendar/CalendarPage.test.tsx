import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { CalendarItem, CalendarResponse } from './api';
import CalendarPage from './CalendarPage';
import { toIsoDate } from './calendar-dates';

const originalAdapter = api.defaults.adapter;

function item(overrides: Partial<CalendarItem>): CalendarItem {
  return {
    key: 'EVENT-1',
    kind: 'EVENT',
    id: 1,
    title: 'Sprint review',
    eventType: 'MEETING',
    allDay: true,
    startDate: '',
    endDate: '',
    startAt: null,
    endAt: null,
    department: null,
    user: null,
    task: null,
    reference: null,
    parentId: null,
    ...overrides,
  };
}

/** Items dated relative to the browser's today, so they fall inside the default (current month) view. */
function response(): CalendarResponse {
  const today = toIsoDate(new Date());
  return {
    today,
    from: today,
    to: today,
    items: [
      item({ startDate: today, endDate: today }),
      item({ key: 'TASK-42', kind: 'TASK_DEADLINE', id: 42, title: 'Fix contact form', eventType: null, startDate: today, endDate: today, reference: 'TSK-000042', task: { code: 'TSK-000042', status: 'TODO', priority: 'HIGH', dueState: 'OVERDUE' } }),
      item({ key: 'MILESTONE-3', kind: 'MILESTONE', id: 3, title: 'Beta launch', eventType: null, startDate: today, endDate: today, reference: 'PRJ-0001', parentId: 1 }),
    ],
  };
}

describe('CalendarPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('labels each item by kind (never colour alone) and asks for my items by default', async () => {
    const adapter = mockApi({ 'GET /calendar': () => response() });

    renderPage(<CalendarPage />, webEmployee);

    expect(await screen.findByRole('button', { name: 'Overdue task: Fix contact form' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Milestone: Beta launch' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Meeting: Sprint review' })).toBeInTheDocument();
    expect(adapter.mock.calls[0]?.[0].params).toMatchObject({ mine: true });
    expect(screen.getByLabelText(/today, 3 items/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New event' })).not.toBeInTheDocument();
  });

  it('switches to the agenda and toggles "only my tasks"', async () => {
    const adapter = mockApi({ 'GET /calendar': () => response() });

    renderPage(<CalendarPage />, superAdmin);
    await screen.findByRole('button', { name: 'Milestone: Beta launch' });
    await userEvent.click(screen.getByRole('button', { name: 'Agenda' }));
    await userEvent.click(screen.getByLabelText('Only my tasks'));

    await waitFor(() => expect(adapter.mock.calls.at(-1)?.[0].params).toMatchObject({ mine: false }));
    const agenda = await screen.findByText('Beta launch');
    expect(within(agenda.closest('button')!).getByText(/Milestone · PRJ-0001/)).toBeInTheDocument();
  });

  it('opens events in the event dialog', async () => {
    mockApi({
      'GET /calendar': () => response(),
      'GET /calendar/events/1': () => ({
        id: 1,
        title: 'Sprint review',
        description: null,
        eventType: 'MEETING',
        allDay: true,
        startDate: '2026-10-08',
        endDate: '2026-10-08',
        startAt: '2026-10-07T18:30:00Z',
        endAt: '2026-10-08T18:30:00Z',
        department: null,
        user: null,
        createdBy: null,
        version: 0,
        canEdit: false,
      }),
      'GET /departments': () => [],
      'GET /team': () => ({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
    });

    renderPage(<CalendarPage />, webEmployee);
    await userEvent.click(await screen.findByRole('button', { name: 'Meeting: Sprint review' }));

    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByRole('heading', { name: 'Sprint review' })).toBeInTheDocument();
    expect(within(dialog).getByLabelText(/Title/)).toBeDisabled();
    expect(within(dialog).getByRole('button', { name: 'Done' })).toBeInTheDocument();
  });
});
