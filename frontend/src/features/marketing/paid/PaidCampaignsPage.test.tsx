import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { MarketingContext } from '../api';
import type { PaidCampaign, PaidCampaignDetail, PaidFigures, PaidMonth, PaidMonthTotals, PaidSummary } from './api';
import PaidCampaignsPage from './PaidCampaignsPage';

const originalAdapter = api.defaults.adapter;

const priya = { id: 5, fullName: 'Priya Menon', email: 'priya.menon@teamops.local', jobTitle: 'Digital Marketing Manager', status: 'ACTIVE' as const };
const dm = { id: 7, name: 'Digital Marketing', code: 'DM' };

const context: MarketingContext = {
  today: '2026-10-09',
  currentPeriod: { month: 10, year: 2026, label: 'October 2026' },
  years: [2027, 2026, 2025],
  owners: [priya],
  behindThresholdPct: 60,
};

const viewer = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'MARKETING_VIEW', 'CAMPAIGN_VIEW'], { id: 12, department: dm });
const editor = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'MARKETING_VIEW', 'CAMPAIGN_VIEW', 'CAMPAIGN_EDIT'], { id: 5, department: dm });

/** Brief section 76: ₹42,000 spent, 150,000 impressions, 2,800 clicks, 84 leads. */
const SAP: PaidFigures = {
  results: { spend: 42_000, impressions: 150_000, clicks: 2_800, leads: 84, conversions: 21 },
  rates: { ctr: 1.87, costPerLead: 500, conversionRate: 25, costPerClick: 15 },
};
const AUGUST: PaidFigures = {
  results: { spend: 6_000, impressions: 30_000, clicks: 450, leads: 10, conversions: 2 },
  rates: { ctr: 1.5, costPerLead: 600, conversionRate: 20, costPerClick: 13.33 },
};
const NOTHING: PaidFigures = {
  results: { spend: 0, impressions: 0, clicks: 0, leads: 0, conversions: 0 },
  rates: { ctr: null, costPerLead: null, conversionRate: null, costPerClick: null },
};

function campaign(overrides: Partial<PaidCampaign>): PaidCampaign {
  return {
    id: 1,
    name: 'SAP S/4HANA Testing Campaign',
    platform: 'LINKEDIN',
    objective: 'LEAD_GENERATION',
    startDate: '2026-08-01',
    endDate: null,
    budget: 50_000,
    currency: 'INR',
    owner: priya,
    status: 'ACTIVE',
    notes: null,
    provider: 'MANUAL',
    externalId: null,
    lifetime: SAP,
    budgetProgress: { budget: 50_000, spent: 42_000, remaining: 8_000, usedPct: 84, overBudget: false },
    month: SAP,
    version: 0,
    createdAt: '2026-08-01T05:00:00Z',
    updatedAt: '2026-10-05T05:00:00Z',
    ...overrides,
  };
}

const overspent = campaign({
  id: 2,
  name: 'QA services brand awareness',
  objective: 'BRAND_AWARENESS',
  status: 'PAUSED',
  budget: 40_000,
  budgetProgress: { budget: 40_000, spent: 43_000, remaining: -3_000, usedPct: 107.5, overBudget: true },
  month: NOTHING,
});

function month(overrides: Partial<PaidMonth> & Pick<PaidMonth, 'id' | 'period'>): PaidMonth {
  return { figures: SAP, notes: null, source: 'MANUAL', recordedBy: priya, updatedAt: '2026-10-05T05:00:00Z', version: 0, correctable: true, ...overrides };
}

const detail: PaidCampaignDetail = {
  campaign: campaign({ month: null, lifetime: { results: { spend: 48_000, impressions: 180_000, clicks: 3_250, leads: 94, conversions: 23 }, rates: { ctr: 1.81, costPerLead: 510.64, conversionRate: 24.47, costPerClick: 14.77 } }, budgetProgress: { budget: 50_000, spent: 48_000, remaining: 2_000, usedPct: 96, overBudget: false } }),
  months: [
    month({ id: 11, period: { month: 8, year: 2026, label: 'August 2026' }, figures: AUGUST, correctable: false }),
    month({ id: 12, period: { month: 10, year: 2026, label: 'October 2026' }, version: 3 }),
  ],
  permissions: { canEdit: true },
};

const october: PaidMonthTotals = { period: context.currentPeriod, campaigns: 1, figures: SAP };
const september: PaidMonthTotals = {
  period: { month: 9, year: 2026, label: 'September 2026' },
  campaigns: 2,
  figures: { results: { spend: 48_000, impressions: 200_000, clicks: 2_400, leads: 60, conversions: 18 }, rates: { ctr: 1.2, costPerLead: 800, conversionRate: 30, costPerClick: 20 } },
};
const summary: PaidSummary = {
  current: october,
  comparison: september,
  byPlatform: [{ platform: 'LINKEDIN', campaigns: 1, figures: SAP }],
  budget: { campaigns: 2, progress: { budget: 90_000, spent: 85_000, remaining: 5_000, usedPct: 94.44, overBudget: false } },
};

const page = <T,>(content: T[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });
const body = (config: InternalAxiosRequestConfig) => JSON.parse(String(config.data)) as Record<string, unknown>;

type Handler = (config: InternalAxiosRequestConfig) => unknown;
function routes(extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /marketing/context': () => context,
    'GET /marketing/imports': () => [],
    'GET /marketing/paid-campaigns/summary': () => summary,
    'GET /marketing/paid-campaigns/trend': () => ({ months: [september, october] }),
    'GET /marketing/paid-campaigns': () => page([campaign({}), overspent]),
    'GET /marketing/paid-campaigns/1': () => detail,
    ...extra,
  };
}

describe('PaidCampaignsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows budget, spent, remaining, leads and cost per lead for the month', async () => {
    mockApi(routes());
    renderPage(<PaidCampaignsPage />, viewer);

    const cards = await screen.findByRole('region', { name: 'Paid results for October 2026' });
    expect(within(cards).getByText('Budget').closest('div')).toHaveTextContent('₹90,000');
    expect(within(cards).getByText('2 campaigns running in October')).toBeInTheDocument();
    expect(within(cards).getByRole('progressbar', { name: 'Budget used' })).toHaveAttribute('aria-valuenow', '94');
    expect(within(cards).getByText('Remaining budget').closest('div')).toHaveTextContent('₹5,000');
    const leads = within(cards).getByText('Leads').closest('div')!;
    expect(leads).toHaveTextContent('84');
    expect(leads).toHaveTextContent('Up 40% vs September');
    // A lower cost per lead is good news, in words as well as colour.
    const cpl = within(cards).getByText('Cost per lead').closest('div')!;
    expect(cpl).toHaveTextContent('₹500');
    expect(within(cpl).getByText('Down 37.5% vs September')).toHaveClass('text-status-success');

    const table = screen.getByRole('table', { name: 'October 2026 against September 2026' });
    expect(within(within(table).getByText('Cost per lead').closest('tr')!).getByText('Down ₹300')).toHaveClass('text-status-success');
    expect(within(within(table).getByText('CTR').closest('tr')!).getByText('Up 0.67 pts')).toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Paid results by month' })).getAllByRole('row')).toHaveLength(3);
    expect(screen.getByRole('figure', { name: 'Spend vs leads by month' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New campaign' })).not.toBeInTheDocument();
  });

  it('lists campaigns with budget progress and flags overspending in words', async () => {
    const user = userEvent.setup();
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'GET /marketing/paid-campaigns': (config) => (requests.push(config), page([campaign({}), overspent])) }));
    renderPage(<PaidCampaignsPage />, viewer);

    const sap = (await screen.findByRole('button', { name: 'SAP S/4HANA Testing Campaign' })).closest('tr')!;
    expect(within(sap).getByRole('progressbar', { name: 'SAP S/4HANA Testing Campaign budget used' })).toHaveAttribute('aria-valuenow', '84');
    expect(within(sap).getByText('₹8,000 left')).toBeInTheDocument();
    expect(within(sap).getByText('₹500')).toBeInTheDocument();
    expect(within(sap).getByText('Active')).toBeInTheDocument();
    const paused = screen.getByText('QA services brand awareness').closest('tr')!;
    expect(within(paused).getByText('Over budget by ₹3,000')).toBeInTheDocument();
    expect(within(paused).getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100');
    expect(within(paused).getByText('Paused')).toBeInTheDocument();
    expect(requests.at(-1)?.params).toMatchObject({ month: 10, year: 2026, sort: 'start,desc' });

    await user.click(screen.getByLabelText('All months'));
    expect(requests.at(-1)?.params).not.toHaveProperty('month');
  });

  it('opens a campaign with its monthly results, read-only for viewers', async () => {
    const user = userEvent.setup();
    mockApi(routes({ 'GET /marketing/paid-campaigns/1': () => ({ ...detail, permissions: { canEdit: false }, months: detail.months.map((m) => ({ ...m, correctable: false })) }) }));
    renderPage(<PaidCampaignsPage />, viewer);

    await user.click(await screen.findByRole('button', { name: 'SAP S/4HANA Testing Campaign' }));
    const sheet = await screen.findByRole('dialog', { name: /SAP S\/4HANA Testing Campaign/ });
    const totals = within(sheet).getByRole('region', { name: 'Campaign totals' });
    expect(within(totals).getByText('Remaining').closest('div')).toHaveTextContent('₹2,000');
    expect(within(totals).getByText('Cost per lead').closest('div')).toHaveTextContent('₹510.64');
    const rows = within(within(sheet).getByRole('table', { name: 'Monthly results' })).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('October 2026');
    expect(rows[2]).toHaveTextContent('August 2026');
    expect(within(sheet).queryByRole('button', { name: 'Record a month' })).not.toBeInTheDocument();
    expect(within(sheet).queryByRole('button', { name: 'Edit campaign' })).not.toBeInTheDocument();
  });

  it('records a missing month after checking the figures', async () => {
    const user = userEvent.setup();
    const puts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'PUT /marketing/paid-campaigns/1/results/2026/9': (config) => (puts.push(config), detail) }));
    renderPage(<PaidCampaignsPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'SAP S/4HANA Testing Campaign' }));
    const sheet = await screen.findByRole('dialog', { name: /SAP S\/4HANA Testing Campaign/ });
    // August is closed (lock), October is open; September is the only month left to record.
    expect(within(sheet).getByText('August 2026 is closed')).toBeInTheDocument();
    expect(within(sheet).getByRole('button', { name: 'Correct October 2026' })).toBeInTheDocument();
    await user.click(within(sheet).getByRole('button', { name: 'Record a month' }));
    const form = within(sheet).getByRole('form', { name: 'Record a month' });
    const monthSelect = within(form).getByLabelText(/Month/);
    expect(within(monthSelect).getAllByRole('option').map((o) => o.textContent)).toEqual(['September 2026']);

    await user.type(within(form).getByLabelText(/Amount spent/), '36,500');
    await user.type(within(form).getByLabelText('Impressions'), '1000');
    await user.type(within(form).getByLabelText('Clicks'), '2000');
    await user.type(within(form).getByLabelText('Leads'), '70');
    await user.click(within(form).getByRole('button', { name: 'Record results' }));
    expect(await within(form).findByText('Cannot be more than the impressions')).toBeInTheDocument();
    expect(puts).toHaveLength(0);

    await user.clear(within(form).getByLabelText('Impressions'));
    await user.type(within(form).getByLabelText('Impressions'), '120000');
    await user.click(within(form).getByRole('button', { name: 'Record results' }));
    expect(puts).toHaveLength(1);
    expect(body(puts[0]!)).toEqual({ amountSpent: 36_500, impressions: 120_000, clicks: 2_000, leads: 70, conversions: 0, notes: null });
  });

  it('corrects an open month with its version', async () => {
    const user = userEvent.setup();
    const puts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'PUT /marketing/paid-campaigns/1/results/2026/10': (config) => (puts.push(config), detail) }));
    renderPage(<PaidCampaignsPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'SAP S/4HANA Testing Campaign' }));
    const sheet = await screen.findByRole('dialog', { name: /SAP S\/4HANA Testing Campaign/ });
    await user.click(within(sheet).getByRole('button', { name: 'Correct October 2026' }));
    const form = within(sheet).getByRole('form', { name: 'Correct October 2026' });
    expect(within(form).getByLabelText(/Amount spent/)).toHaveValue('42000');
    await user.clear(within(form).getByLabelText('Leads'));
    await user.type(within(form).getByLabelText('Leads'), '80');
    await user.click(within(form).getByRole('button', { name: 'Save correction' }));
    expect(body(puts[0]!)).toEqual({ version: 3, amountSpent: 42_000, impressions: 150_000, clicks: 2_800, leads: 80, conversions: 21, notes: null });
  });

  it('creates a campaign and checks its dates and budget', async () => {
    const user = userEvent.setup();
    const posts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'POST /marketing/paid-campaigns': (config) => (posts.push(config), detail) }));
    renderPage(<PaidCampaignsPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'New campaign' }));
    const dialog = await screen.findByRole('dialog', { name: 'New paid campaign' });
    await user.type(within(dialog).getByLabelText(/Campaign name/), 'SAP S/4HANA Testing Campaign');
    await user.type(within(dialog).getByLabelText(/End date/), '2026-09-30');
    await user.click(within(dialog).getByRole('button', { name: 'Add campaign' }));
    expect(await within(dialog).findByText('Budget is required')).toBeInTheDocument();
    expect(within(dialog).getByText('The end date cannot be before the start date')).toBeInTheDocument();
    expect(posts).toHaveLength(0);

    await user.clear(within(dialog).getByLabelText(/End date/));
    await user.type(within(dialog).getByLabelText(/End date/), '2026-10-31');
    await user.type(within(dialog).getByLabelText(/Budget/), '50,000');
    await user.click(within(dialog).getByRole('button', { name: 'Add campaign' }));
    expect(body(posts[0]!)).toEqual({
      name: 'SAP S/4HANA Testing Campaign',
      platform: 'LINKEDIN',
      objective: 'LEAD_GENERATION',
      status: 'ACTIVE',
      startDate: '2026-10-09',
      endDate: '2026-10-31',
      budget: 50_000,
      ownerId: 5,
      notes: null,
    });
    // The new campaign opens so its first month can be recorded.
    expect(await screen.findByRole('region', { name: 'Campaign totals' })).toBeInTheDocument();
  });

  it('keeps a campaign with results from going back to draft or being deleted', async () => {
    const user = userEvent.setup();
    mockApi(routes());
    renderPage(<PaidCampaignsPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'SAP S/4HANA Testing Campaign' }));
    const sheet = await screen.findByRole('dialog', { name: /SAP S\/4HANA Testing Campaign/ });
    await user.click(within(sheet).getByRole('button', { name: 'Edit campaign' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit campaign' });
    expect(within(dialog).getByRole('option', { name: 'Draft' })).toBeDisabled();
    expect(within(dialog).queryByRole('button', { name: 'Delete campaign' })).not.toBeInTheDocument();
    expect(within(dialog).getByLabelText(/Budget/)).toHaveValue('50000');
  });
});
