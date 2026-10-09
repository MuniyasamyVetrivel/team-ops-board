import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, superAdmin } from '@/test/fixtures';
import { arun, priya } from '@/test/marketing-fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { MarketingContext } from '../api';
import type { FrozenMonth, MonthlyReport, ReportLine } from './api';
import MarketingReportPage from './MarketingReportPage';

const originalAdapter = api.defaults.adapter;

const context: MarketingContext = {
  today: '2026-10-09',
  currentPeriod: { month: 10, year: 2026, label: 'October 2026' },
  years: [2027, 2026, 2025],
  owners: [priya, arun],
  behindThresholdPct: 60,
};

const line = (label: string, current: number | null, previous: number | null, extra: Partial<ReportLine> = {}): ReportLine => {
  const change = current === null || previous === null ? null : current - previous;
  const unit = extra.unit ?? 'COUNT';
  return {
    label,
    unit,
    better: 'HIGHER',
    current,
    previous,
    change,
    changePct: unit === 'PERCENT' || change === null || !previous ? null : Math.round((change / previous) * 10000) / 100,
    target: false,
    ...extra,
  };
};

/** Brief section 50: September → October, leads 180 → 200 (+11.1%), top 10 35 → 42 (+20%), backlinks 18 → 22 (+22%). */
const october: MonthlyReport = {
  period: { month: 10, year: 2026, label: 'October 2026' },
  comparisonPeriod: { month: 9, year: 2026, label: 'September 2026' },
  ownerId: null,
  frozen: false,
  generatedAt: '2026-10-09T05:00:00Z',
  generatedBy: null,
  groups: [
    { key: 'SEO', title: 'SEO summary', lines: [line('Top 10 keywords', 42, 35), line('Not ranked', 12, 15, { better: 'LOWER' }), line('Average position', 9.4, 11.2, { unit: 'DECIMAL', better: 'LOWER' })] },
    { key: 'LEADS', title: 'Lead performance', lines: [line('Leads generated', 200, 180), line('Email leads', 45, 50), line('Monthly lead target', 250, 220, { better: 'NEITHER', target: true })] },
    { key: 'EMAIL', title: 'Email campaign performance', lines: [line('Emails sent', 25_000, 20_000, { better: 'NEITHER' }), line('Open rate', 35.42, 31.58, { unit: 'PERCENT' })] },
    { key: 'BACKLINKS', title: 'Backlink performance', lines: [line('Submitted', 35, 30), line('Live', 22, 18)] },
    { key: 'ACTIVITIES', title: 'Recurring activity completion', lines: [line('Completed', 6, 8), line('Completion', 75, 80, { unit: 'PERCENT' })] },
  ],
  keywordMovements: {
    improved: [{ keywordId: 1, keyword: 'sap testing services', page: 'SAP Testing Services', previousPosition: 14, position: 7, change: 7 }],
    declined: [{ keywordId: 2, keyword: 'software testing company', page: 'Software Testing Services', previousPosition: 9, position: null, change: null }],
  },
  targets: [
    { type: 'Website Leads', unit: 'COUNT', targetValue: 250, actual: 200, achievementPct: 80, remaining: 50, status: 'IN_PROGRESS', previousTargetValue: 220, previousActual: 210, previousAchievementPct: 95.45 },
  ],
};

const leadsOnly: MonthlyReport = { ...october, groups: october.groups.filter((g) => g.key === 'LEADS' || g.key === 'ACTIVITIES'), keywordMovements: null, targets: null };
const september: MonthlyReport = { ...october, period: october.comparisonPeriod, comparisonPeriod: { month: 8, year: 2026, label: 'August 2026' } };
const frozenSeptember: MonthlyReport = { ...september, frozen: true, generatedAt: '2026-10-02T06:30:00Z', generatedBy: priya };
const frozen: FrozenMonth[] = [{ period: september.period, generatedAt: '2026-10-02T06:30:00Z', generatedBy: priya }];

type Handler = (config: InternalAxiosRequestConfig) => unknown;
function routes(extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /marketing/context': () => context,
    'GET /marketing/reports/frozen': () => [],
    'GET /marketing/reports/monthly': () => october,
    ...extra,
  };
}

describe('MarketingReportPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('compares the month with the month before (brief section 50)', async () => {
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'GET /marketing/reports/monthly': (config) => (requests.push(config), october) }));
    renderPage(<MarketingReportPage />, superAdmin);

    const headlines = await screen.findByRole('region', { name: 'September 2026 to October 2026' });
    expect(screen.getByText('September 2026 → October 2026')).toBeInTheDocument();
    expect(requests[0]?.params).toEqual({ month: 10, year: 2026 });
    const leads = within(headlines).getByText('Leads').parentElement!;
    expect(leads).toHaveTextContent('180');
    expect(leads).toHaveTextContent('200');
    expect(within(leads).getByText('Up 11.11%')).toHaveClass('text-status-success');
    expect(within(headlines).getByText('Top 10 keywords').parentElement).toHaveTextContent('Up 20%');
    expect(within(headlines).getByText('Backlinks live').parentElement).toHaveTextContent('Up 22.22%');
    expect(within(headlines).getByText('Email open rate').parentElement).toHaveTextContent('Up 3.84 pts');

    // A lower average position is good news; a drop in completed activities is bad news, in words too.
    const seo = screen.getByRole('table', { name: 'SEO summary' });
    expect(within(within(seo).getByText('Average position').closest('tr')!).getByText(/Down/)).toHaveClass('text-status-success');
    const activities = screen.getByRole('table', { name: 'Recurring activity completion' });
    expect(within(within(activities).getByText('Completed').closest('tr')!).getByText('Down 25%')).toHaveClass('text-status-danger');

    expect(screen.getByRole('figure', { name: 'SEO summary: September 2026 and October 2026' })).toBeInTheDocument();
    const improved = screen.getByRole('list', { name: 'Keywords that improved most' });
    expect(improved).toHaveTextContent('sap testing services');
    expect(improved).toHaveTextContent('+7 places');
    expect(screen.getByRole('list', { name: 'Keywords that declined most' })).toHaveTextContent('NR · NOT RANKED');
    const targets = screen.getByRole('table', { name: 'Target achievement' });
    expect(within(targets).getByRole('progressbar', { name: 'Website Leads achievement' })).toHaveAttribute('aria-valuenow', '80');
    expect(targets).toHaveTextContent('95.45%');
  });

  it('shows a reader only the parts the server sends them', async () => {
    mockApi(routes({ 'GET /marketing/reports/monthly': () => leadsOnly }));
    renderPage(<MarketingReportPage />, makeUser(['EMPLOYEE'], ['MARKETING_VIEW', 'LEAD_VIEW']));

    expect(await screen.findByRole('table', { name: 'Lead performance' })).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'SEO summary' })).not.toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Target achievement' })).not.toBeInTheDocument();
    expect(screen.queryByText('Keywords that improved most')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Freeze report' })).not.toBeInTheDocument();
  });

  it('freezes an ended month for someone who can see every module', async () => {
    const user = userEvent.setup();
    const posts: InternalAxiosRequestConfig[] = [];
    mockApi(
      routes({
        'GET /marketing/reports/monthly': () => september,
        'POST /marketing/reports/monthly/2026/9/freeze': (config) => (posts.push(config), frozenSeptember),
      }),
    );
    renderPage(<MarketingReportPage />, superAdmin, '/digital-marketing/reports?month=9&year=2026');

    await user.click(await screen.findByRole('button', { name: 'Freeze report' }));
    expect(posts).toHaveLength(1);
  });

  it('serves a frozen month as it stood and shows live figures on request', async () => {
    const user = userEvent.setup();
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(
      routes({
        'GET /marketing/reports/frozen': () => frozen,
        'GET /marketing/reports/monthly': (config) => (requests.push(config), (config.params as { live?: boolean }).live ? september : frozenSeptember),
      }),
    );
    renderPage(<MarketingReportPage />, superAdmin, '/digital-marketing/reports?month=9&year=2026');

    expect(await screen.findByText(/These figures stay as reported/)).toHaveTextContent('by Priya Menon');
    expect(screen.queryByRole('button', { name: 'Freeze report' })).not.toBeInTheDocument();
    await user.click(screen.getByLabelText('Show live figures'));
    expect(requests.at(-1)?.params).toEqual({ month: 9, year: 2026, live: true });
    expect(await screen.findByText('Showing live figures; later corrections are included.')).toBeInTheDocument();
    expect(within(screen.getByRole('list', { name: 'Frozen reports' })).getByRole('button', { name: 'September 2026' })).toBeInTheDocument();
  });

  it('does not offer to freeze the current month', async () => {
    mockApi(routes());
    renderPage(<MarketingReportPage />, superAdmin);

    expect(await screen.findByRole('table', { name: 'Lead performance' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Freeze report' })).not.toBeInTheDocument();
  });
});
