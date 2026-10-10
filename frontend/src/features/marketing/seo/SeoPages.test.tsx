import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { Route, Routes } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, seoExecutive, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { MarketingContext } from '../api';
import type { KeywordItem, KeywordStanding, SeoPageDetail, SeoPageListItem, SeoStats } from './api';
import SeoPageDetailPage from './SeoPageDetailPage';
import SeoRankingsPage from './SeoRankingsPage';

const originalAdapter = api.defaults.adapter;

const arun = { id: 12, fullName: 'Arun Kumar', email: 'arun.kumar@teamops.local', jobTitle: 'SEO Executive', status: 'ACTIVE' as const };
const dm = { id: 7, name: 'Digital Marketing', code: 'DM' };

const context: MarketingContext = {
  today: '2026-10-08',
  currentPeriod: { month: 10, year: 2026, label: 'October 2026' },
  years: [2027, 2026, 2025],
  owners: [arun],
  behindThresholdPct: 60,
};

/** Reads SEO but cannot change it. */
const seoReader = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'MARKETING_VIEW', 'SEO_VIEW']);

const stats = (overrides: Partial<SeoStats> = {}): SeoStats => ({
  totalKeywords: 4,
  top3: 0,
  top10: 2,
  ranking: 1,
  positions11to20: 1,
  positions21to50: 0,
  positions51to100: 0,
  notRanked: 1,
  notRecorded: 0,
  improved: 3,
  declined: 1,
  unchanged: 0,
  averagePosition: 12.5,
  ...overrides,
});

const pageItem: SeoPageListItem = {
  id: 1,
  url: '/services/sap-testing',
  title: 'SAP Testing Services',
  pageType: 'SERVICE',
  primaryKeyword: 'SAP Testing Services',
  department: dm,
  owner: arun,
  status: 'ACTIVE',
  stats: stats(),
  updatedAt: '2026-10-08T05:00:00Z',
};

const standing = (overrides: Partial<KeywordStanding>): KeywordStanding => ({
  recorded: true,
  position: 7,
  status: 'TOP_10',
  previousRecorded: true,
  previousPosition: 12,
  change: { value: 5, movement: 'IMPROVED' },
  ...overrides,
});

function keyword(overrides: Partial<KeywordItem>): KeywordItem {
  return {
    id: 1,
    keyword: 'SAP Testing Services',
    page: { id: 1, title: 'SAP Testing Services', url: '/services/sap-testing', status: 'ACTIVE' },
    searchEngine: 'GOOGLE',
    location: 'India',
    device: 'DESKTOP',
    targetPosition: 5,
    searchVolume: 1900,
    keywordDifficulty: 48,
    owner: arun,
    status: 'ACTIVE',
    ranking: standing({}),
    entry: null,
    lastRankedAt: '2026-10-05T05:00:00Z',
    version: 0,
    createdAt: '2026-08-01T05:00:00Z',
    updatedAt: '2026-10-05T05:00:00Z',
    ...overrides,
  };
}

const keywords = [
  keyword({}),
  keyword({ id: 2, keyword: 'SAP Testing Company', ranking: standing({ position: 31, status: 'RANKING', previousPosition: 28, change: { value: -3, movement: 'DECLINED' } }) }),
  keyword({ id: 3, keyword: 'test automation roi', ranking: standing({ position: null, status: 'NOT_RANKED', previousPosition: 19, change: { value: null, movement: 'DECLINED' } }) }),
  keyword({ id: 4, keyword: 'test automation calculator', lastRankedAt: null, ranking: standing({ recorded: false, position: null, status: 'NOT_RANKED', previousRecorded: false, previousPosition: null, change: null }) }),
];

const detail = (overrides: Partial<SeoPageDetail> = {}): SeoPageDetail => ({
  ...pageItem,
  period: { month: 10, year: 2026, label: 'October 2026' },
  previousPeriod: { month: 9, year: 2026, label: 'September 2026' },
  previousStats: stats({ top10: 1, averagePosition: 18, notRanked: 2 }),
  keywordCount: 4,
  version: 3,
  createdAt: '2026-08-01T05:00:00Z',
  permissions: { canEdit: true },
  ...overrides,
});

const page = <T,>(content: T[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });

describe('SeoRankingsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  function routes(requests: InternalAxiosRequestConfig[] = []) {
    return {
      'GET /marketing/context': () => context,
      'GET /marketing/pages': (config: InternalAxiosRequestConfig) => {
        requests.push(config);
        return page([pageItem]);
      },
      'GET /marketing/pages/options': () => [keywords[0]!.page],
      'GET /marketing/keywords': () => page(keywords),
      'GET /marketing/imports': () => [],
      'GET /departments': () => [],
    };
  }

  it('lists pages with their figures for the business month', async () => {
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes(requests));
    renderPage(<SeoRankingsPage />, seoExecutive, '/?tab=pages');

    const row = (await screen.findByText('/services/sap-testing')).closest('tr')!;
    expect(within(row).getByText('Service')).toBeInTheDocument();
    expect(within(row).getByText('12.5')).toBeInTheDocument();
    expect(within(row).getByText('improved', { exact: false })).toBeInTheDocument();
    expect(within(row).getByText('Active')).toBeInTheDocument();
    expect(requests.at(-1)?.params).toMatchObject({ month: 10, year: 2026, status: ['ACTIVE', 'INACTIVE'] });
    expect(screen.getByText(/October 2026/, { selector: 'p' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add page' })).toBeInTheDocument();
  });

  it('shows each keyword position with a label, never colour alone', async () => {
    mockApi(routes());
    renderPage(<SeoRankingsPage />, seoReader, '/?tab=keywords');

    const top = (await screen.findByText('SAP Testing Services', { selector: 'p' })).closest('tr')!;
    expect(within(top).getByText(/#7/)).toHaveTextContent('#7 · TOP 10');
    expect(within(top).getByText('Improved by 5 places')).toBeInTheDocument();
    expect(within(top).getByText('#12')).toBeInTheDocument();

    const ranking = screen.getByText('SAP Testing Company').closest('tr')!;
    expect(within(ranking).getByText(/#31/)).toHaveTextContent('#31 · RANKING');
    expect(within(ranking).getByText('Declined by 3 places')).toBeInTheDocument();

    const dropped = screen.getByText('test automation roi').closest('tr')!;
    expect(within(dropped).getByText(/^NR/)).toHaveTextContent('NR · NOT RANKED');
    expect(within(dropped).getByText('Declined: no longer ranked')).toBeInTheDocument();

    const missing = screen.getByText('test automation calculator').closest('tr')!;
    expect(within(missing).getByText('No data')).toBeInTheDocument();

    // Readers get no editing controls.
    expect(screen.queryByRole('button', { name: 'Add page' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Edit / })).not.toBeInTheDocument();
  });

  it('validates the page URL before calling the server', async () => {
    const user = userEvent.setup();
    mockApi(routes());
    renderPage(<SeoRankingsPage />, seoExecutive, '/?tab=pages');

    await user.click(await screen.findByRole('button', { name: 'Add page' }));
    await user.type(screen.getByLabelText(/Page URL/), 'services page');
    await user.type(screen.getByLabelText(/Page title/), 'Services');
    await user.click(screen.getByRole('button', { name: 'Add page' }));

    expect(await screen.findByText('Enter a path starting with / or a full http(s) URL, without spaces')).toBeInTheDocument();
  });
});

describe('SeoPageDetailPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  function render(pageDetail: SeoPageDetail, user = seoExecutive) {
    const keywordRequests: InternalAxiosRequestConfig[] = [];
    mockApi({
      'GET /marketing/context': () => context,
      'GET /marketing/pages/1': () => pageDetail,
      'GET /marketing/pages/1/rankings': () => ({ periods: [], series: [], averages: [] }),
      'GET /marketing/keywords': (config) => {
        keywordRequests.push(config);
        return page(keywords.slice(0, 2));
      },
    });
    renderPage(
      <Routes>
        <Route path="/digital-marketing/seo/pages/:id" element={<SeoPageDetailPage />} />
      </Routes>,
      user,
      '/digital-marketing/seo/pages/1',
    );
    return keywordRequests;
  }

  it('shows the page figures with the comparison to last month', async () => {
    const requests = render(detail({ stats: stats({ notRecorded: 1 }) }));

    expect(await screen.findByRole('heading', { name: 'SAP Testing Services' })).toBeInTheDocument();
    const figures = screen.getByRole('region', { name: 'SEO figures for October 2026' });
    expect(within(figures).getByText('Average position').closest('div')).toHaveTextContent('12.5');
    // A lower average position is better: 18 → 12.5 is shown as an improvement in words.
    expect(within(figures).getByText('30.6% vs September')).toBeInTheDocument();
    expect(within(figures).getByText('1 without a ranking for October 2026')).toBeInTheDocument();
    expect(within(figures).getByText('0 in the top 3')).toBeInTheDocument();

    expect(await screen.findByText('SAP Testing Company')).toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: 'Page' })).not.toBeInTheDocument();
    expect(requests.at(-1)?.params).toMatchObject({ pageId: 1, month: 10, year: 2026 });
    expect(screen.getByRole('button', { name: 'Edit page' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add keyword' })).toBeInTheDocument();
  });

  it('hides editing when the server does not allow it', async () => {
    render(detail({ permissions: { canEdit: false } }), seoReader);

    await screen.findByRole('heading', { name: 'SAP Testing Services' });
    expect(screen.queryByRole('button', { name: 'Edit page' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add keyword' })).not.toBeInTheDocument();
  });

  it('takes no new keywords on an archived page', async () => {
    render(detail({ status: 'ARCHIVED' }));

    await screen.findByRole('heading', { name: 'SAP Testing Services' });
    expect(screen.getByText('Archived')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add keyword' })).not.toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Edit SAP Testing Company' })).toBeInTheDocument();
  });
});
