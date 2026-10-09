import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { ImportDefinition, MarketingContext } from '../api';
import type { TargetItem, TypeRef } from '../targets/api';
import type { Backlink, BacklinkSummary, BacklinkTrendMonth, MonthActivity } from './api';
import BacklinksPage from './BacklinksPage';

const originalAdapter = api.defaults.adapter;

const arun = { id: 5, fullName: 'Arun Kumar', email: 'arun.kumar@teamops.local', jobTitle: 'SEO Executive', status: 'ACTIVE' as const };
const dm = { id: 7, name: 'Digital Marketing', code: 'DM' };

const context: MarketingContext = {
  today: '2026-10-09',
  currentPeriod: { month: 10, year: 2026, label: 'October 2026' },
  years: [2027, 2026, 2025],
  owners: [arun],
  behindThresholdPct: 60,
};

const viewer = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'MARKETING_VIEW', 'BACKLINK_VIEW'], { id: 12, department: dm });
const editor = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'MARKETING_VIEW', 'BACKLINK_VIEW', 'BACKLINK_EDIT', 'TARGET_VIEW', 'SEO_VIEW'], { id: 5, department: dm });

const backlinksType: TypeRef = { id: 7, code: 'BACKLINKS', name: 'Backlinks', unit: 'COUNT', actualSource: 'BACKLINKS_LIVE', automatic: true };

/** Brief section 46 / 74: October target 50, submitted 35, approved 28, live 22, remaining 15. */
const goal: TargetItem = {
  id: 1,
  type: backlinksType,
  month: 10,
  year: 2026,
  label: 'October 2026',
  targetValue: 50,
  actual: 22,
  actualOrigin: 'AUTOMATIC',
  achievementPct: 44,
  remaining: 28,
  status: 'BEHIND',
  thresholdPct: 60,
  owner: arun,
  department: dm,
  notes: null,
  editable: false,
  actualEditable: false,
  version: 0,
  createdAt: '2026-10-01T05:00:00Z',
  updatedAt: '2026-10-05T05:00:00Z',
};

const activity = (month: number, label: string, f: Partial<MonthActivity>): MonthActivity => ({ period: { month, year: 2026, label }, submitted: 0, approved: 0, live: 0, rejected: 0, lost: 0, ...f });
const october = activity(10, 'October 2026', { submitted: 35, approved: 28, live: 22, rejected: 2, lost: 1 });
const september = activity(9, 'September 2026', { submitted: 46, approved: 43, live: 41, rejected: 3 });

const summary: BacklinkSummary = {
  current: october,
  comparison: september,
  targetsVisible: true,
  target: { targetValue: 50, remaining: 15, target: goal },
  liveByType: [
    { linkType: 'GUEST_POST', live: 12 },
    { linkType: 'DIRECTORY', live: 10 },
  ],
  byOwner: [{ owner: arun, submitted: 18, approved: 14, live: 11 }],
  pipeline: [
    { status: 'PROSPECTED', backlinks: 6 },
    { status: 'SUBMITTED', backlinks: 3 },
    { status: 'APPROVED', backlinks: 10 },
    { status: 'LIVE', backlinks: 100 },
    { status: 'REJECTED', backlinks: 9 },
    { status: 'LOST', backlinks: 1 },
  ],
};
const viewerSummary: BacklinkSummary = { ...summary, targetsVisible: false, target: null };

const trend: BacklinkTrendMonth[] = [
  { activity: september, targetValue: 45, remaining: 0, liveAchievementPct: 91.11 },
  { activity: october, targetValue: 50, remaining: 15, liveAchievementPct: 44 },
];

function backlink(overrides: Partial<Backlink>): Backlink {
  return {
    id: 1,
    code: 'BLK-000101',
    targetPage: { id: 3, title: 'SAP Testing Services', url: '/services/sap-testing' },
    targetUrl: '/services/sap-testing',
    referringDomain: 'dzone.com',
    linkUrl: 'https://dzone.com/articles/sap-test-automation',
    anchorText: 'SAP testing services',
    linkType: 'GUEST_POST',
    status: 'SUBMITTED',
    submittedDate: '2026-10-02',
    approvedDate: null,
    liveDate: null,
    rejectedDate: null,
    lostDate: null,
    owner: arun,
    domainAuthority: 72,
    notes: null,
    provider: 'MANUAL',
    createdBy: arun,
    lockedDates: [],
    version: 2,
    createdAt: '2026-10-02T05:00:00Z',
    updatedAt: '2026-10-02T05:00:00Z',
    ...overrides,
  };
}

/** Submitted and live in June: those dates are counted in a closed month. */
const juneLink = backlink({ id: 2, code: 'BLK-000042', referringDomain: 'clutch.co', status: 'LIVE', submittedDate: '2026-06-05', liveDate: '2026-06-12', lockedDates: ['submittedDate', 'liveDate'], version: 0 });

const importDefinition: ImportDefinition = { type: 'backlinks', label: 'Backlinks', description: 'Backlinks from an outreach tracker.', columns: [], maxRows: 2000 };

const page = <T,>(content: T[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });
const body = (config: InternalAxiosRequestConfig) => JSON.parse(String(config.data)) as Record<string, unknown>;

type Handler = (config: InternalAxiosRequestConfig) => unknown;
function routes(extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /marketing/context': () => context,
    'GET /marketing/imports': () => [],
    'GET /marketing/pages/options': () => [{ id: 3, title: 'SAP Testing Services', url: '/services/sap-testing', status: 'ACTIVE' }],
    'GET /marketing/backlinks/summary': () => summary,
    'GET /marketing/backlinks/trend': () => ({ targetsVisible: true, months: trend }),
    'GET /marketing/backlinks': () => page([backlink({}), juneLink]),
    'GET /marketing/backlinks/1': () => backlink({}),
    'GET /marketing/backlinks/2': () => juneLink,
    ...extra,
  };
}

describe('BacklinksPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows the month against the target: submitted, approved, live and remaining', async () => {
    mockApi(routes());
    renderPage(<BacklinksPage />, editor);

    const cards = await screen.findByRole('region', { name: 'Backlink progress for October 2026' });
    expect(within(cards).getByText('Target').closest('div')).toHaveTextContent('50');
    expect(within(cards).getByText('Behind')).toBeInTheDocument();
    expect(within(cards).getByText('Submitted').closest('div')).toHaveTextContent('35');
    expect(within(cards).getByText('Approved').closest('div')).toHaveTextContent('28');
    const live = within(cards).getByText('Live').closest('div')!;
    expect(live).toHaveTextContent('22');
    expect(within(live).getByRole('progressbar', { name: 'Live against target' })).toHaveAttribute('aria-valuenow', '44');
    expect(within(cards).getByText('Remaining').closest('div')).toHaveTextContent('15');
    expect(screen.getByText(/2 rejected · 1 lost/)).toBeInTheDocument();

    expect(within(screen.getByRole('table', { name: 'Backlinks by owner' })).getByText('Arun Kumar').closest('tr')).toHaveTextContent('181411');
    expect(within(screen.getByRole('list', { name: 'Live by type' })).getByText('Guest post')).toBeInTheDocument();
    const rows = within(screen.getByRole('table', { name: 'Backlink history by month' })).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('October 2026');
    expect(rows[1]).toHaveTextContent('44%');
    expect(screen.getByRole('figure', { name: 'Backlinks by month' })).toBeInTheDocument();
  });

  it('leaves the target out for viewers without TARGET_VIEW and offers no editing', async () => {
    mockApi(routes({ 'GET /marketing/backlinks/summary': () => viewerSummary, 'GET /marketing/backlinks/trend': () => ({ targetsVisible: false, months: trend }) }));
    renderPage(<BacklinksPage />, viewer);

    const cards = await screen.findByRole('region', { name: 'Backlink progress for October 2026' });
    expect(within(cards).queryByText('Target')).not.toBeInTheDocument();
    expect(within(cards).queryByText('Remaining')).not.toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Backlink history by month' })).queryByText('Remaining')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New backlink' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Import backlinks' })).not.toBeInTheDocument();
  });

  it('lists backlinks and filters them by pipeline status and stage of the month', async () => {
    const user = userEvent.setup();
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'GET /marketing/backlinks': (config) => (requests.push(config), page([backlink({}), juneLink])) }));
    renderPage(<BacklinksPage />, viewer);

    const row = (await screen.findByRole('button', { name: 'dzone.com' })).closest('tr')!;
    expect(row).toHaveTextContent('BLK-000101');
    expect(row).toHaveTextContent('SAP Testing Services');
    expect(row).toHaveTextContent('Submitted 2 Oct');
    expect(within(screen.getByRole('button', { name: 'clutch.co' }).closest('tr')!).getByText('(month closed)')).toBeInTheDocument();
    expect(requests.at(-1)?.params).toMatchObject({ sort: 'updated,desc' });

    const live = within(screen.getByRole('group', { name: 'Pipeline' })).getByRole('button', { name: /Live/ });
    expect(live).toHaveTextContent('100');
    await user.click(live);
    expect(live).toHaveAttribute('aria-pressed', 'true');
    expect(requests.at(-1)?.params).toMatchObject({ status: ['LIVE'] });

    await user.selectOptions(screen.getByLabelText('List month'), 'LIVE');
    expect(requests.at(-1)?.params).toMatchObject({ month: 10, year: 2026, stage: 'LIVE' });
  });

  it('moves a backlink to the next stage in one click', async () => {
    const user = userEvent.setup();
    const puts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'PUT /marketing/backlinks/1/status': (config) => (puts.push(config), backlink({ status: 'LIVE', liveDate: '2026-10-09', version: 3 })) }));
    renderPage(<BacklinksPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'dzone.com' }));
    const sheet = await screen.findByRole('dialog', { name: /dzone\.com/ });
    const stages = within(sheet).getByRole('list', { name: 'Stage dates' });
    expect(within(stages).getAllByRole('listitem')[0]).toHaveTextContent('Submitted');
    await user.click(within(within(sheet).getByRole('group', { name: 'Change status' })).getByRole('button', { name: 'Live' }));
    expect(body(puts[0]!)).toEqual({ version: 2, status: 'LIVE' });
    expect(await within(sheet).findByText('9 Oct 2026')).toBeInTheDocument();
  });

  it('keeps dates of closed months: a June link can be lost today, not taken back', async () => {
    const user = userEvent.setup();
    mockApi(routes());
    renderPage(<BacklinksPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'clutch.co' }));
    const sheet = await screen.findByRole('dialog', { name: /clutch\.co/ });
    const group = within(sheet).getByRole('group', { name: 'Change status' });
    expect(within(group).getByRole('button', { name: 'Lost' })).toBeEnabled();
    expect(within(group).getByRole('button', { name: 'Submitted' })).toBeDisabled();
    expect(within(group).getByRole('button', { name: 'Approved' })).toBeDisabled();
    expect(within(within(sheet).getByRole('list', { name: 'Stage dates' })).getAllByText('(month closed)')).toHaveLength(2);

    await user.click(within(sheet).getByRole('button', { name: 'Edit backlink' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit BLK-000042' });
    expect(within(dialog).getByLabelText(/^Submitted/)).toBeDisabled();
    expect(within(dialog).getByLabelText(/^Live/)).toBeDisabled();
    expect(within(dialog).queryByRole('button', { name: 'Delete backlink' })).not.toBeInTheDocument();
  });

  it('creates a backlink after checking the stage rules', async () => {
    const user = userEvent.setup();
    const posts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'POST /marketing/backlinks': (config) => (posts.push(config), backlink({})) }));
    renderPage(<BacklinksPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'New backlink' }));
    const dialog = await screen.findByRole('dialog', { name: 'New backlink' });
    await user.click(within(dialog).getByRole('button', { name: 'Add backlink' }));
    expect(await within(dialog).findByText('Choose our page, or enter the URL the link points to')).toBeInTheDocument();
    expect(within(dialog).getByText('Enter the referring domain or the link URL')).toBeInTheDocument();

    await user.selectOptions(within(dialog).getByLabelText('Our page'), '3');
    await user.type(within(dialog).getByLabelText('Referring domain'), 'dzone.com');
    // Going live dates the submission and the live stage today, and needs the link URL.
    await user.selectOptions(within(dialog).getByLabelText(/Status/), 'LIVE');
    expect(within(dialog).getByLabelText(/^Live/)).toHaveValue('2026-10-09');
    await user.click(within(dialog).getByRole('button', { name: 'Add backlink' }));
    expect(await within(dialog).findByText('A live backlink needs the URL of the page that links to us')).toBeInTheDocument();
    expect(posts).toHaveLength(0);

    await user.type(within(dialog).getByLabelText(/Link URL/), 'https://dzone.com/articles/sap-test-automation');
    await user.type(within(dialog).getByLabelText(/Domain authority/), '72');
    await user.click(within(dialog).getByRole('button', { name: 'Add backlink' }));
    expect(body(posts[0]!)).toEqual({
      targetPageId: 3,
      targetUrl: null,
      referringDomain: 'dzone.com',
      linkUrl: 'https://dzone.com/articles/sap-test-automation',
      anchorText: null,
      linkType: 'GUEST_POST',
      status: 'LIVE',
      submittedDate: '2026-10-09',
      approvedDate: null,
      liveDate: '2026-10-09',
      rejectedDate: null,
      lostDate: null,
      ownerId: 5,
      domainAuthority: 72,
      notes: null,
    });
  }, 15_000);

  it('offers the CSV import to editors', async () => {
    const user = userEvent.setup();
    mockApi(routes({ 'GET /marketing/imports': () => [importDefinition] }));
    renderPage(<BacklinksPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'Import backlinks' }));
    expect(await screen.findByRole('dialog', { name: 'Import backlinks' })).toBeInTheDocument();
  });
});
