import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { superAdmin, webEmployee } from '@/test/fixtures';
import { celebrationEditor, linkedInPost, occasionItem, page } from '@/test/celebration-fixtures';
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
    occasion: null,
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
        createdBy: null,
        version: 0,
        canEdit: false,
      }),
      'GET /departments': () => [],
    });

    renderPage(<CalendarPage />, webEmployee);
    await userEvent.click(await screen.findByRole('button', { name: 'Meeting: Sprint review' }));

    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByRole('heading', { name: 'Sprint review' })).toBeInTheDocument();
    expect(within(dialog).getByLabelText(/Title/)).toBeDisabled();
    expect(within(dialog).getByRole('button', { name: 'Done' })).toBeInTheDocument();
  });

  it('has no celebration filters or legend entries for employees outside Digital Marketing', async () => {
    mockApi({ 'GET /calendar': () => response() });

    renderPage(<CalendarPage />, webEmployee);

    await screen.findByRole('button', { name: 'Milestone: Beta launch' });
    expect(screen.queryByRole('group', { name: 'Show categories' })).not.toBeInTheDocument();
    expect(within(screen.getByRole('list', { name: 'Legend' })).queryByText('Birthday')).not.toBeInTheDocument();
    expect(screen.getByText('Deadlines, milestones, approvals and events.')).toBeInTheDocument();
  });

  it('shows celebrations with their own label, hides a category with its chip and opens the drawer', async () => {
    const today = toIsoDate(new Date());
    const diwali = occasionItem(
      today,
      { key: `IMPORTANT_DAY-4-${today}`, kind: 'IMPORTANT_DAY', id: 4, title: 'Diwali', department: null },
      { category: 'IMPORTANT_DAY', subjectType: 'IMPORTANT_DAY', subjectId: 4, detail: 'India', region: 'INDIA', dayCategory: 'FESTIVAL', linkedIn: linkedInPost() },
    );
    mockApi({
      'GET /calendar': () => ({ ...response(), items: [...response().items, occasionItem(today), diwali] }),
      'GET /team': () => page([]),
    });
    const user = userEvent.setup();
    renderPage(<CalendarPage />, celebrationEditor);
    await screen.findByRole('button', { name: 'Milestone: Beta launch' });
    // The week view shows every item of a day.
    await user.click(screen.getByRole('button', { name: 'Week' }));

    const birthday = await screen.findByRole('button', { name: "Birthday: Priya Menon's birthday" });
    expect(screen.getByRole('list', { name: 'Legend' })).toHaveTextContent('Work anniversary');

    const chip = screen.getByRole('button', { name: /^Birthday/, pressed: true });
    await user.click(chip);
    expect(birthday).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Birthday/, pressed: false })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /^Work and team events/ }));
    expect(screen.queryByRole('button', { name: 'Milestone: Beta launch' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Important day: Diwali' }));
    const drawer = await screen.findByRole('dialog');
    expect(within(drawer).getByText('India')).toBeInTheDocument();
    expect(within(drawer).getByText('Festival')).toBeInTheDocument();
    expect(within(drawer).getByText('Not started')).toBeInTheDocument();
    expect(within(drawer).getByRole('link', { name: /Open the important day/ })).toHaveAttribute('href', '/celebrations/important-days?day=4');
    await user.click(within(drawer).getByRole('button', { name: 'Update LinkedIn post' }));
    expect(await screen.findByRole('heading', { name: 'LinkedIn post' })).toBeInTheDocument();
  });
});
