import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { MarketingContext } from '../api';
import type { EmailCampaign, EmailCounts, EmailRates, MonthTotals, MonthlySummary } from './api';
import EmailCampaignsPage from './EmailCampaignsPage';

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

const SAMPLE: EmailCounts = { emailsSent: 25_000, delivered: 24_000, bounced: 1_000, opened: 9_400, uniqueOpens: 8_500, clicked: 1_400, uniqueClicks: 1_250, unsubscribed: 60, leads: 185 };
const SAMPLE_RATES: EmailRates = { deliveryRate: 96, openRate: 35.42, clickRate: 5.21, clickToOpenRate: 14.71, leadConversionRate: 0.77, bounceRate: 4, unsubscribeRate: 0.25 };
const ZERO: EmailCounts = { emailsSent: 0, delivered: 0, bounced: 0, opened: 0, uniqueOpens: 0, clicked: 0, uniqueClicks: 0, unsubscribed: 0, leads: 0 };
const NO_RATES: EmailRates = { deliveryRate: null, openRate: null, clickRate: null, clickToOpenRate: null, leadConversionRate: null, bounceRate: null, unsubscribeRate: null };

function campaign(overrides: Partial<EmailCampaign>): EmailCampaign {
  return {
    id: 1,
    name: 'SAP Testing Services Outreach',
    campaignType: 'LEAD_GENERATION',
    campaignDate: '2026-10-02',
    owner: priya,
    audience: 'SAP decision makers',
    status: 'SENT',
    counts: SAMPLE,
    rates: SAMPLE_RATES,
    notes: null,
    provider: 'CSV',
    externalId: 'ZC-1001',
    version: 0,
    createdAt: '2026-10-02T05:00:00Z',
    updatedAt: '2026-10-02T05:00:00Z',
    ...overrides,
  };
}

const october: MonthTotals = { period: context.currentPeriod, campaigns: 1, counts: SAMPLE, rates: SAMPLE_RATES };
const september: MonthTotals = {
  period: { month: 9, year: 2026, label: 'September 2026' },
  campaigns: 3,
  counts: { ...SAMPLE, emailsSent: 34_500, leads: 140 },
  rates: { ...SAMPLE_RATES, openRate: 32.82, bounceRate: 3.5 },
};
const summary: MonthlySummary = { current: october, comparison: september, byType: [{ campaignType: 'LEAD_GENERATION', campaigns: 1, counts: SAMPLE, rates: SAMPLE_RATES }] };

const page = <T,>(content: T[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });
const body = (config: InternalAxiosRequestConfig) => JSON.parse(String(config.data)) as Record<string, unknown>;

type Handler = (config: InternalAxiosRequestConfig) => unknown;
function routes(extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /marketing/context': () => context,
    'GET /marketing/imports': () => [],
    'GET /marketing/email-campaigns/summary': () => summary,
    'GET /marketing/email-campaigns/trend': () => ({ months: [september, october] }),
    'GET /marketing/email-campaigns': () => page([campaign({}), campaign({ id: 2, name: 'Year-end customer update', status: 'DRAFT', campaignDate: '2026-11-08', counts: ZERO, rates: NO_RATES })]),
    ...extra,
  };
}

describe('EmailCampaignsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows the month as KPI cards and a full comparison with the previous month', async () => {
    mockApi(routes());
    renderPage(<EmailCampaignsPage />, viewer);

    const cards = await screen.findByRole('region', { name: 'Email results for October 2026' });
    const open = within(cards).getByText('Open rate').closest('div')!;
    expect(open).toHaveTextContent('35.42%');
    expect(open).toHaveTextContent(/Up .* vs September/);
    expect(within(cards).getByText('Unique opens ÷ delivered')).toBeInTheDocument();

    const table = screen.getByRole('table', { name: 'October 2026 against September 2026' });
    const openRow = within(table).getByText('Open rate').closest('tr')!;
    expect(within(openRow).getByText('Up 2.60 pts')).toHaveClass('text-status-success');
    // A higher bounce rate is bad news, shown in words too.
    expect(within(within(table).getByText('Bounce rate').closest('tr')!).getByText('Up 0.50 pts')).toHaveClass('text-status-danger');
    expect(within(screen.getByRole('list', { name: 'Campaign types' })).getByText('Lead generation')).toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Email results by month' })).getAllByRole('row')).toHaveLength(3);

    expect(screen.queryByRole('button', { name: 'New campaign' })).not.toBeInTheDocument();
  });

  it('compares with the same month last year on request', async () => {
    const user = userEvent.setup();
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'GET /marketing/email-campaigns/summary': (config) => (requests.push(config), summary) }));
    renderPage(<EmailCampaignsPage />, viewer);

    await user.selectOptions(await screen.findByLabelText('Compare with'), 'Compare with the same month last year');
    expect(requests.at(-1)?.params).toMatchObject({ month: 10, year: 2026, compareMonth: 10, compareYear: 2025 });
  });

  it('lists the month’s campaigns with rates only for sent ones', async () => {
    const user = userEvent.setup();
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'GET /marketing/email-campaigns': (config) => (requests.push(config), page([campaign({}), campaign({ id: 2, name: 'Year-end customer update', status: 'DRAFT', counts: ZERO, rates: NO_RATES })])) }));
    renderPage(<EmailCampaignsPage />, viewer);

    const sent = (await screen.findByText('SAP Testing Services Outreach')).closest('tr')!;
    expect(within(sent).getByText('35.42%')).toBeInTheDocument();
    expect(within(sent).getByText('Sent')).toBeInTheDocument();
    const draft = screen.getByText('Year-end customer update').closest('tr')!;
    expect(within(draft).getByText('Draft')).toBeInTheDocument();
    expect(within(draft).getAllByText('—')).toHaveLength(4);
    expect(requests.at(-1)?.params).toMatchObject({ month: 10, year: 2026, sort: 'date,desc' });

    await user.click(screen.getByLabelText('All months'));
    expect(requests.at(-1)?.params).not.toHaveProperty('month');
  });

  it('checks the counts before saving a sent campaign', async () => {
    // Many fields to type: no pause between keystrokes.
    const user = userEvent.setup({ delay: null });
    const posts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'POST /marketing/email-campaigns': (config) => (posts.push(config), campaign({ id: 9 })) }));
    renderPage(<EmailCampaignsPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'New campaign' }));
    const dialog = await screen.findByRole('dialog', { name: 'New email campaign' });
    await user.type(within(dialog).getByLabelText(/Campaign name/), 'SAP Testing Services Outreach');
    await user.selectOptions(within(dialog).getByLabelText(/Type/), 'Lead generation');
    await user.type(within(dialog).getByLabelText('Emails sent'), '25,000');
    await user.type(within(dialog).getByLabelText('Delivered'), '26000');
    await user.clear(within(dialog).getByLabelText(/Sent on/));
    await user.type(within(dialog).getByLabelText(/Sent on/), '2026-10-12');
    await user.click(within(dialog).getByRole('button', { name: 'Add campaign' }));
    expect(await within(dialog).findByText('Cannot be more than the emails sent')).toBeInTheDocument();
    expect(within(dialog).getByText('A sent campaign cannot be dated in the future')).toBeInTheDocument();
    expect(posts).toHaveLength(0);

    await user.clear(within(dialog).getByLabelText('Delivered'));
    await user.type(within(dialog).getByLabelText('Delivered'), '24000');
    await user.clear(within(dialog).getByLabelText(/Sent on/));
    await user.type(within(dialog).getByLabelText(/Sent on/), '2026-10-02');
    // One paste per field keeps this long form quick to fill.
    for (const [label, value] of [['Bounced', '1000'], ['Opened (total)', '9400'], ['Unique opens', '8500'], ['Clicked (total)', '1400'], ['Unique clicks', '1250'], ['Unsubscribed', '60'], ['Leads generated', '185']] as const) {
      await user.click(within(dialog).getByLabelText(label));
      await user.paste(value);
    }
    await user.click(within(dialog).getByRole('button', { name: 'Add campaign' }));

    expect(posts).toHaveLength(1);
    expect(body(posts[0]!)).toEqual({
      name: 'SAP Testing Services Outreach',
      campaignType: 'LEAD_GENERATION',
      campaignDate: '2026-10-02',
      status: 'SENT',
      ownerId: 5,
      audience: null,
      notes: null,
      emailsSent: 25_000,
      delivered: 24_000,
      bounced: 1_000,
      opened: 9_400,
      uniqueOpens: 8_500,
      clicked: 1_400,
      uniqueClicks: 1_250,
      unsubscribed: 60,
      leadsGenerated: 185,
    });
    // A long form: typing every field can exceed the default 5s when the whole suite runs in parallel.
  }, 15_000);

  it('saves drafts without counts and only deletes campaigns that were not sent', async () => {
    const user = userEvent.setup();
    const puts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'PUT /marketing/email-campaigns/1': (config) => (puts.push(config), campaign({ status: 'DRAFT', counts: ZERO, rates: NO_RATES })) }));
    renderPage(<EmailCampaignsPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'Edit Year-end customer update' }));
    let dialog = await screen.findByRole('dialog', { name: 'Edit campaign' });
    expect(within(dialog).getByRole('button', { name: 'Delete campaign' })).toBeInTheDocument();
    expect(within(dialog).queryByLabelText('Emails sent')).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    await user.click(screen.getByRole('button', { name: 'Edit SAP Testing Services Outreach' }));
    dialog = await screen.findByRole('dialog', { name: 'Edit campaign' });
    expect(within(dialog).queryByRole('button', { name: 'Delete campaign' })).not.toBeInTheDocument();
    expect(within(dialog).getByLabelText('Unique opens')).toHaveValue('8500');
    await user.selectOptions(within(dialog).getByLabelText(/Status/), 'Draft');
    expect(within(dialog).getByText('Results are only kept for sent campaigns: saving with this status clears them.')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(body(puts[0]!)).toMatchObject({ version: 0, status: 'DRAFT', emailsSent: 0, uniqueOpens: 0, leadsGenerated: 0 });
  });
});
