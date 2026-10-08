import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { Route, Routes } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, seoExecutive, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { MarketingContext } from '../api';
import type { KeywordHistory, KeywordItem, KeywordStanding, MonthlyReport, PageHistory, SeoPageDetail, SeoStats } from './api';
import SeoPageDetailPage from './SeoPageDetailPage';
import SeoRankingsPage from './SeoRankingsPage';

const originalAdapter = api.defaults.adapter;

const arun = { id: 12, fullName: 'Arun Kumar', email: 'arun.kumar@teamops.local', jobTitle: 'SEO Executive', status: 'ACTIVE' as const };
const sapPage = { id: 1, title: 'SAP Testing Services', url: '/services/sap-testing', status: 'ACTIVE' as const };

const context: MarketingContext = {
  today: '2026-10-08',
  currentPeriod: { month: 10, year: 2026, label: 'October 2026' },
  years: [2027, 2026, 2025],
  owners: [arun],
  behindThresholdPct: 60,
};

const seoReader = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'MARKETING_VIEW', 'SEO_VIEW']);

const standing = (overrides: Partial<KeywordStanding> = {}): KeywordStanding => ({
  recorded: true,
  position: 7,
  status: 'TOP_10',
  previousRecorded: true,
  previousPosition: 12,
  change: { value: 5, movement: 'IMPROVED' },
  ...overrides,
});

function keyword(overrides: Partial<KeywordItem> = {}): KeywordItem {
  return {
    id: 1,
    keyword: 'SAP Testing Services',
    page: sapPage,
    searchEngine: 'GOOGLE',
    location: 'India',
    device: 'DESKTOP',
    targetPosition: 5,
    searchVolume: 1900,
    keywordDifficulty: 48,
    owner: arun,
    status: 'ACTIVE',
    ranking: standing(),
    entry: { id: 31, version: 0, searchVolume: 2100, notes: null, source: 'MANUAL', updatedAt: '2026-10-05T05:00:00Z' },
    lastRankedAt: '2026-10-05T05:00:00Z',
    version: 0,
    createdAt: '2026-08-01T05:00:00Z',
    updatedAt: '2026-10-05T05:00:00Z',
    ...overrides,
  };
}

const unrecorded = (id: number, text: string, device: KeywordItem['device'] = 'DESKTOP') =>
  keyword({ id, keyword: text, device, entry: null, ranking: standing({ recorded: false, position: null, status: 'NOT_RANKED', change: null, previousPosition: id === 2 ? 28 : null, previousRecorded: id === 2 }) });

const page = <T,>(content: T[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });

const stats = (overrides: Partial<SeoStats> = {}): SeoStats => ({
  totalKeywords: 14,
  top3: 2,
  top10: 6,
  ranking: 6,
  positions11to20: 3,
  positions21to50: 2,
  positions51to100: 1,
  notRanked: 2,
  notRecorded: 1,
  improved: 8,
  declined: 3,
  unchanged: 1,
  averagePosition: 15.4,
  ...overrides,
});

const report: MonthlyReport = {
  period: { month: 10, year: 2026, label: 'October 2026' },
  stats: stats(),
  previousPeriod: { month: 9, year: 2026, label: 'September 2026' },
  previousStats: stats({ top3: 1, top10: 5, positions11to20: 3, notRanked: 4, averagePosition: 19.2 }),
};

const history: KeywordHistory = {
  keywordId: 1,
  keyword: 'SAP Testing Services',
  page: sapPage,
  searchEngine: 'GOOGLE',
  location: 'India',
  device: 'DESKTOP',
  targetPosition: 5,
  status: 'ACTIVE',
  entries: [
    { id: 31, month: 10, year: 2026, label: 'October 2026', position: 7, status: 'TOP_10', change: { value: 5, movement: 'IMPROVED' }, searchVolume: 2100, notes: null, source: 'MANUAL', recordedBy: arun, createdAt: '2026-10-05T05:00:00Z', updatedAt: '2026-10-05T05:00:00Z', version: 0, correctable: true },
    { id: 30, month: 9, year: 2026, label: 'September 2026', position: 12, status: 'RANKING', change: { value: 6, movement: 'IMPROVED' }, searchVolume: 1900, notes: 'After the content refresh', source: 'CSV', recordedBy: arun, createdAt: '2026-09-04T05:00:00Z', updatedAt: '2026-09-04T05:00:00Z', version: 1, correctable: true },
    { id: 29, month: 8, year: 2026, label: 'August 2026', position: 18, status: 'RANKING', change: { value: null, movement: 'NEW' }, searchVolume: 1900, notes: null, source: 'MANUAL', recordedBy: arun, createdAt: '2026-08-04T05:00:00Z', updatedAt: '2026-08-04T05:00:00Z', version: 0, correctable: false },
  ],
};

type ApiRoutes = Record<string, (config: InternalAxiosRequestConfig) => unknown>;

function routes(extra: ApiRoutes = {}): ApiRoutes {
  return {
    'GET /marketing/context': () => context,
    'GET /marketing/imports': () => [],
    'GET /marketing/pages/options': () => [sapPage],
    'GET /marketing/rankings': () => page([keyword(), keyword({ id: 2, keyword: 'SAP Testing Company', ranking: standing({ position: 31, status: 'RANKING', previousPosition: 28, change: { value: -3, movement: 'DECLINED' } }) })]),
    'GET /marketing/rankings/monthly': () => report,
    'GET /marketing/keywords/1/rankings': () => history,
    ...extra,
  };
}

const body = (config: InternalAxiosRequestConfig) => JSON.parse(String(config.data)) as Record<string, unknown>;

describe('SEO ranking table', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('is the default tab and shows the month with labels and movement', async () => {
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'GET /marketing/rankings': (config) => (requests.push(config), page([keyword()])) }));
    renderPage(<SeoRankingsPage />, seoReader);

    const row = (await screen.findByText('SAP Testing Services', { selector: 'p' })).closest('tr')!;
    expect(within(row).getByText(/#7/)).toHaveTextContent('#7 · TOP 10');
    expect(within(row).getByText('#12')).toBeInTheDocument();
    expect(within(row).getByText('Improved by 5 places')).toBeInTheDocument();
    // The month's recorded volume wins over the keyword's default.
    expect(within(row).getByText('2,100')).toBeInTheDocument();
    expect(requests.at(-1)?.params).toMatchObject({ month: 10, year: 2026, sort: 'best', status: ['ACTIVE', 'PAUSED'] });
    expect(screen.queryByRole('button', { name: 'Record rankings' })).not.toBeInTheDocument();
  });

  it('sends the brief section 27 filters and sorts', async () => {
    const user = userEvent.setup();
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'GET /marketing/rankings': (config) => (requests.push(config), page([keyword()])) }));
    renderPage(<SeoRankingsPage />, seoReader);

    await screen.findByText('SAP Testing Services', { selector: 'p' });
    await user.selectOptions(screen.getByLabelText('Sort rankings'), 'Biggest decline');
    await user.selectOptions(screen.getByLabelText('Position'), '11–20');
    await user.selectOptions(screen.getByLabelText('Movement'), 'Declined');
    await user.selectOptions(screen.getByLabelText('Ranking status'), 'No data this month');

    expect(requests.at(-1)?.params).toMatchObject({ sort: 'decline', minPosition: 11, maxPosition: 20, movement: 'DECLINED', standing: 'NOT_RECORDED', page: 0 });
  });
});

describe('Monthly SEO summary', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('compares every position band with the previous month', async () => {
    mockApi(routes());
    renderPage(<SeoRankingsPage />, seoReader, '/?tab=summary');

    const table = await screen.findByRole('table', { name: 'Keywords by position, October 2026 against September 2026' });
    const top10 = within(table).getByText('Top 10').closest('tr')!;
    expect(top10).toHaveTextContent('6');
    expect(within(top10).getByText('+1')).toBeInTheDocument();
    expect(within(top10).getByText('more than last month', { exact: false })).toBeInTheDocument();
    expect(within(within(table).getByText('11–20').closest('tr')!).getByText('No change')).toBeInTheDocument();
    // Fewer keywords out of the rankings is good news, shown in words as well as colour.
    const notRanked = within(table).getByText('Not ranked').closest('tr')!;
    expect(within(notRanked).getByText('−2')).toHaveClass('text-status-success');

    const cards = screen.getByRole('region', { name: 'SEO summary for October 2026' });
    expect(within(cards).getByText('1 without a ranking for October 2026')).toBeInTheDocument();
    expect(within(cards).getByText('Average position').closest('div')).toHaveTextContent('15.4');
  });
});

describe('Keyword ranking history', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('charts the months, offers corrections only where allowed, and records Not Ranked explicitly', async () => {
    const user = userEvent.setup();
    const posts: InternalAxiosRequestConfig[] = [];
    mockApi(
      routes({
        'GET /marketing/keywords/2/rankings': () => ({ ...history, keywordId: 2, keyword: 'SAP Testing Company', entries: [] }),
        'POST /marketing/keywords/2/rankings': (config) => (posts.push(config), { ...history, keywordId: 2, keyword: 'SAP Testing Company' }),
      }),
    );
    renderPage(<SeoRankingsPage />, seoExecutive);

    await user.click(await screen.findByRole('button', { name: 'Ranking history for SAP Testing Services' }));
    const sheet = await screen.findByRole('dialog');
    expect(await within(sheet).findByRole('figure', { name: 'Monthly positions for SAP Testing Services' })).toBeInTheDocument();
    expect(within(sheet).getByRole('button', { name: 'Correct October 2026' })).toBeInTheDocument();
    expect(within(sheet).getByRole('button', { name: 'Correct September 2026' })).toBeInTheDocument();
    expect(within(sheet).queryByRole('button', { name: 'Correct August 2026' })).not.toBeInTheDocument();
    expect(within(sheet).getByText('August 2026 is closed')).toBeInTheDocument();
    expect(within(sheet).getByText('CSV import')).toBeInTheDocument();
    await user.keyboard('{Escape}');

    await user.click(screen.getByRole('button', { name: 'Ranking history for SAP Testing Company' }));
    const other = await screen.findByRole('dialog');
    expect(await within(other).findByText('No rankings recorded yet')).toBeInTheDocument();
    await user.click(within(other).getByRole('button', { name: 'Record October' }));
    await user.click(within(other).getByRole('button', { name: 'Record ranking' }));
    expect(await within(other).findByText('Enter a position from 1 to 100, or tick Not ranked')).toBeInTheDocument();
    expect(posts).toHaveLength(0);

    await user.click(within(other).getByLabelText('Not ranked'));
    await user.click(within(other).getByRole('button', { name: 'Record ranking' }));
    expect(posts).toHaveLength(1);
    expect(body(posts[0]!)).toEqual({ month: 10, year: 2026, position: null, searchVolume: null, notes: null });
  });
});

describe('Monthly ranking update', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists keywords without a ranking and sends only the filled rows', async () => {
    const user = userEvent.setup();
    const posts: InternalAxiosRequestConfig[] = [];
    mockApi(
      routes({
        'GET /marketing/rankings': (config) =>
          (config.params as { standing?: string }).standing === 'NOT_RECORDED'
            ? page([unrecorded(2, 'SAP Testing Company'), unrecorded(3, 'SAP Application Testing'), unrecorded(4, 'SAP S4HANA Testing', 'MOBILE')])
            : page([keyword()]),
        'POST /marketing/rankings/monthly': (config) => (posts.push(config), { period: context.currentPeriod, recorded: 2 }),
      }),
    );
    renderPage(<SeoRankingsPage />, seoExecutive);

    await user.click(await screen.findByRole('button', { name: 'Record rankings' }));
    const sheet = await screen.findByRole('dialog', { name: 'Record rankings for October 2026' });
    const company = (await within(sheet).findByText('SAP Testing Company')).closest('tr')!;
    expect(within(company).getByText('#28')).toBeInTheDocument();

    await user.click(within(sheet).getByRole('button', { name: /^Save/ }));
    expect(await within(sheet).findByText('Enter at least one position, or tick NR for a keyword that is not ranked')).toBeInTheDocument();

    await user.type(within(sheet).getByLabelText('Position for SAP Testing Company (desktop)'), '101');
    await user.click(within(sheet).getByRole('button', { name: /^Save/ }));
    expect(await within(sheet).findByText('1–100, or tick NR')).toBeInTheDocument();

    await user.clear(within(sheet).getByLabelText('Position for SAP Testing Company (desktop)'));
    await user.type(within(sheet).getByLabelText('Position for SAP Testing Company (desktop)'), '9');
    await user.click(within(sheet).getByLabelText('SAP S4HANA Testing (mobile) is not ranked'));
    expect(within(sheet).getByText('2 of 3 filled in')).toBeInTheDocument();
    await user.click(within(sheet).getByRole('button', { name: 'Save 2 rankings' }));

    expect(posts).toHaveLength(1);
    expect(body(posts[0]!)).toEqual({
      month: 10,
      year: 2026,
      entries: [
        { keywordId: 2, position: 9, searchVolume: null, notes: null },
        { keywordId: 4, position: null, searchVolume: null, notes: null },
      ],
    });
  });
});

describe('Page ranking history chart', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('draws the best placed keywords with a named legend', async () => {
    const pageHistory: PageHistory = {
      periods: [
        { month: 8, year: 2026, label: 'August 2026' },
        { month: 9, year: 2026, label: 'September 2026' },
        { month: 10, year: 2026, label: 'October 2026' },
      ],
      series: [
        { keywordId: 2, keyword: 'SAP Testing Company', device: 'DESKTOP', points: [{ recorded: true, position: 34 }, { recorded: true, position: 28 }, { recorded: true, position: 31 }] },
        { keywordId: 1, keyword: 'SAP Testing Services', device: 'MOBILE', points: [{ recorded: true, position: 18 }, { recorded: true, position: 12 }, { recorded: true, position: 7 }] },
      ],
      averages: [26, 20, 19],
    };
    const detail: SeoPageDetail = {
      ...sapPage,
      pageType: 'SERVICE',
      primaryKeyword: null,
      department: { id: 7, name: 'Digital Marketing', code: 'DM' },
      owner: arun,
      stats: stats(),
      updatedAt: '2026-10-08T05:00:00Z',
      period: context.currentPeriod,
      previousPeriod: report.previousPeriod,
      previousStats: stats(),
      keywordCount: 2,
      version: 0,
      createdAt: '2026-08-01T05:00:00Z',
      permissions: { canEdit: false },
    };
    mockApi(routes({ 'GET /marketing/pages/1': () => detail, 'GET /marketing/pages/1/rankings': () => pageHistory, 'GET /marketing/keywords': () => page([]) }));
    renderPage(
      <Routes>
        <Route path="/digital-marketing/seo/pages/:id" element={<SeoPageDetailPage />} />
      </Routes>,
      seoReader,
      '/digital-marketing/seo/pages/1',
    );

    const chart = await screen.findByRole('figure', { name: "Ranking history of this page's keywords" });
    const legend = within(chart).getByRole('list', { name: 'Chart legend' });
    const items = within(legend).getAllByRole('listitem').map((li) => li.textContent);
    // Ordered by the latest position: #7 before #31; the mobile keyword is named as such.
    expect(items).toEqual(['SAP Testing Services (mobile)', 'SAP Testing Company', 'Average position']);
    expect(screen.getByLabelText('History range')).toHaveValue('12');
  });
});
