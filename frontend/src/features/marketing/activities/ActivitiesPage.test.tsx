import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { Route, Routes } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { MarketingContext } from '../api';
import ActivitiesPage from './ActivitiesPage';
import ActivityDetailPage from './ActivityDetailPage';
import type { ActivityDetail, ActivityListItem, OccurrenceItem } from './api';

const originalAdapter = api.defaults.adapter;

const priya = { id: 5, fullName: 'Priya Menon', email: 'priya.menon@teamops.local', jobTitle: 'Digital Marketing Manager', status: 'ACTIVE' as const };
const arun = { id: 12, fullName: 'Arun Kumar', email: 'arun.kumar@teamops.local', jobTitle: 'SEO Executive', status: 'ACTIVE' as const };
const dm = { id: 7, name: 'Digital Marketing', code: 'DM' };

const context: MarketingContext = {
  today: '2026-10-08',
  currentPeriod: { month: 10, year: 2026, label: 'October 2026' },
  years: [2027, 2026, 2025],
  owners: [priya, arun],
  behindThresholdPct: 60,
};

const marketer = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'MARKETING_VIEW'], { id: 12, department: dm });
const manager = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'MARKETING_VIEW', 'MARKETING_EDIT'], { id: 5, department: dm });

function occurrence(overrides: Partial<OccurrenceItem>): OccurrenceItem {
  return {
    id: 1,
    activityId: 1,
    activityName: 'Monthly SEO Ranking Update',
    frequency: 'MONTHLY',
    periodStart: '2026-10-01',
    periodEnd: '2026-10-31',
    periodLabel: 'October 2026',
    dueDate: '2026-10-05',
    dueState: 'OVERDUE',
    status: 'PENDING',
    task: { id: 301, code: 'TSK-000301', title: 'Update October keyword rankings', status: 'TODO' },
    assignee: arun,
    completedAt: null,
    completedBy: null,
    notes: null,
    version: 0,
    canAct: false,
    ...overrides,
  };
}

const taskless = occurrence({ id: 2, activityId: 5, activityName: 'Monthly competitor analysis', dueDate: '2026-10-15', dueState: 'SCHEDULED', task: null, assignee: priya, canAct: true });

const activities: ActivityListItem[] = [
  { id: 1, name: 'Monthly SEO Ranking Update', frequency: 'MONTHLY', department: dm, owner: priya, assignee: arun, startDate: '2026-07-01', endDate: null, active: true, generatesTasks: true, lastCompletedAt: '2026-09-30T06:00:00Z', nextOccurrence: occurrence({}), openCount: 1, overdueCount: 1, updatedAt: '2026-10-01T00:00:00Z' },
  { id: 5, name: 'Monthly competitor analysis', frequency: 'MONTHLY', department: dm, owner: priya, assignee: priya, startDate: '2026-07-01', endDate: null, active: false, generatesTasks: false, lastCompletedAt: null, nextOccurrence: taskless, openCount: 1, overdueCount: 0, updatedAt: '2026-10-01T00:00:00Z' },
];

function detail(overrides: Partial<ActivityDetail> = {}): ActivityDetail {
  return {
    id: 1,
    name: 'Monthly SEO Ranking Update',
    description: 'Record every keyword position for the month.',
    frequency: 'MONTHLY',
    department: dm,
    owner: priya,
    defaultAssignee: arun,
    startDate: '2026-07-01',
    endDate: null,
    dueOffsetDays: 4,
    taskTitleTemplate: 'Update {month} keyword rankings',
    nextTaskTitle: 'Update November keyword rankings',
    taskPriority: 'HIGH',
    active: true,
    checklist: ['Export positions from the rank tracker', 'Record the month on the SEO Rankings page'],
    lastCompletedAt: '2026-09-30T06:00:00Z',
    nextOccurrence: occurrence({}),
    recentOccurrences: [],
    occurrenceCount: 2,
    locked: true,
    version: 3,
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
    permissions: { canEdit: true },
    ...overrides,
  };
}

const page = <T,>(content: T[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });
const body = (config: InternalAxiosRequestConfig) => JSON.parse(String(config.data)) as Record<string, unknown>;

type Handler = (config: InternalAxiosRequestConfig) => unknown;
function routes(extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /marketing/context': () => context,
    'GET /marketing/activities': () => page(activities),
    'GET /marketing/activity-occurrences': () => page([occurrence({}), taskless]),
    'GET /departments': () => [{ ...dm, status: 'ACTIVE' }],
    ...extra,
  };
}

describe('ActivitiesPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists activities with their next due occurrence and overdue work', async () => {
    mockApi(routes());
    renderPage(<ActivitiesPage />, marketer);

    const rankings = (await screen.findByText('Monthly SEO Ranking Update')).closest('tr')!;
    expect(within(rankings).getByText('Monthly')).toBeInTheDocument();
    expect(within(rankings).getByText(/^Overdue/)).toBeInTheDocument();
    expect(within(rankings).getByText('1 overdue')).toBeInTheDocument();
    expect(within(rankings).getByText('Creates a task each period')).toBeInTheDocument();
    const competitors = screen.getByText('Monthly competitor analysis').closest('tr')!;
    expect(within(competitors).getByText('Inactive')).toBeInTheDocument();
    expect(within(competitors).getByText('Completed on the activity')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New activity' })).not.toBeInTheDocument();
  });

  it('completes an occurrence without a task, with notes', async () => {
    const user = userEvent.setup();
    const posts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'POST /marketing/activity-occurrences/2/complete': (config) => (posts.push(config), { ...taskless, status: 'COMPLETED' }) }));
    renderPage(<ActivitiesPage />, manager, '/?tab=due');

    // A task-backed occurrence links to its task and offers no actions here.
    const withTask = (await screen.findByRole('link', { name: /TSK-000301/ })).closest('tr')!;
    expect(within(withTask).getByRole('link', { name: /TSK-000301/ })).toHaveAttribute('href', '/tasks?task=301');
    expect(within(withTask).queryByRole('button', { name: /Complete/ })).not.toBeInTheDocument();

    const row = screen.getByText('Monthly competitor analysis').closest('tr')!;
    expect(within(row).getByText('No task')).toBeInTheDocument();
    await user.click(within(row).getByRole('button', { name: 'Complete October 2026' }));
    const dialog = await screen.findByRole('dialog', { name: 'Complete October 2026' });
    await user.type(within(dialog).getByLabelText('Notes'), 'Two new competitor pages');
    await user.click(within(dialog).getByRole('button', { name: 'Mark completed' }));

    expect(posts).toHaveLength(1);
    expect(body(posts[0]!)).toEqual({ notes: 'Two new competitor pages' });
  });

  it('creates an activity, with tasks, a checklist and a schedule', async () => {
    const user = userEvent.setup();
    const posts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'POST /marketing/activities': (config) => (posts.push(config), detail({ id: 9 })), 'GET /marketing/activities/9': () => detail({ id: 9 }), 'GET /marketing/activities/9/occurrences': () => page([]) }));
    renderPage(<ActivitiesPage />, manager);

    await user.click(await screen.findByRole('button', { name: 'New activity' }));
    const dialog = await screen.findByRole('dialog', { name: 'New recurring activity' });
    await user.type(within(dialog).getByLabelText(/Activity name/), 'Monthly backlink verification');
    await user.click(within(dialog).getByRole('button', { name: 'Create activity' }));
    expect(await within(dialog).findByText('Enter the task title, or turn off task generation')).toBeInTheDocument();
    expect(posts).toHaveLength(0);

    await user.type(within(dialog).getByLabelText(/Task title/), 'Verify {{month} backlinks');
    await user.clear(within(dialog).getByLabelText(/Due after/));
    await user.type(within(dialog).getByLabelText(/Due after/), '24');
    await user.selectOptions(within(dialog).getByLabelText('Assignee'), 'Arun Kumar');
    await user.click(within(dialog).getByRole('button', { name: 'Add checklist item' }));
    await user.type(within(dialog).getByLabelText('Checklist item 1'), 'Check every submitted link');
    await user.click(within(dialog).getByRole('button', { name: 'Add checklist item' }));
    await user.click(within(dialog).getByRole('button', { name: 'Create activity' }));

    expect(posts).toHaveLength(1);
    expect(body(posts[0]!)).toEqual({
      name: 'Monthly backlink verification',
      description: null,
      departmentId: 7,
      ownerId: 5,
      defaultAssigneeId: 12,
      frequency: 'MONTHLY',
      startDate: '2026-10-08',
      endDate: null,
      dueOffsetDays: 24,
      taskTitleTemplate: 'Verify {month} backlinks',
      taskPriority: 'MEDIUM',
      checklist: ['Check every submitted link'],
    });
  });
});

describe('ActivityDetailPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  function render(activity: ActivityDetail, user = manager, history: OccurrenceItem[] = []) {
    mockApi(routes({ 'GET /marketing/activities/1': () => activity, 'GET /marketing/activities/1/occurrences': () => page(history) }));
    renderPage(
      <Routes>
        <Route path="/digital-marketing/activities/:id" element={<ActivityDetailPage />} />
      </Routes>,
      user,
      '/digital-marketing/activities/1',
    );
  }

  it('shows the schedule, the next task, the checklist and the history', async () => {
    const completed = occurrence({ id: 7, periodStart: '2026-09-01', periodEnd: '2026-09-30', periodLabel: 'September 2026', dueDate: '2026-09-05', dueState: 'NONE', status: 'COMPLETED', completedAt: '2026-09-30T06:00:00Z', completedBy: arun, task: { id: 300, code: 'TSK-000300', title: 'Update September keyword rankings', status: 'COMPLETED' } });
    render(detail({ permissions: { canEdit: false } }), marketer, [occurrence({}), completed]);

    expect(await screen.findByRole('heading', { name: 'Monthly SEO Ranking Update' })).toBeInTheDocument();
    const summary = screen.getByRole('region', { name: 'Activity summary' });
    expect(within(summary).getByText(/^Overdue/)).toBeInTheDocument();
    expect(within(summary).getByText('Due 4 days after the start of each month')).toBeInTheDocument();
    expect(within(summary).getByText('Assignee: Arun Kumar')).toBeInTheDocument();
    expect(screen.getByText('Update November keyword rankings')).toBeInTheDocument();
    expect(within(screen.getByRole('list', { name: 'Checklist' })).getAllByRole('listitem')).toHaveLength(2);

    const september = (await screen.findByText('September 2026')).closest('tr')!;
    // The occurrence and its task both read Completed.
    expect(within(september).getAllByText('Completed')).toHaveLength(2);
    expect(within(september).getByText(/ · Arun Kumar$/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit activity' })).not.toBeInTheDocument();
  });

  it('keeps frequency and start date fixed once there is history', async () => {
    const user = userEvent.setup();
    render(detail());

    await user.click(await screen.findByRole('button', { name: 'Edit activity' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit activity' });
    expect(within(dialog).getByLabelText(/Frequency/)).toBeDisabled();
    expect(within(dialog).getByLabelText(/Start date/)).toBeDisabled();
    expect(within(dialog).getByText('This activity has 2 occurrences, so its frequency and start date are fixed.')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Checklist item 2')).toHaveValue('Record the month on the SEO Rankings page');
    expect(within(dialog).queryByRole('button', { name: 'Delete activity' })).not.toBeInTheDocument();
  });
});
