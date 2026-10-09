import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, seoExecutive, superAdmin } from '@/test/fixtures';
import { arun, bareMarketingDashboard as bare, marketingDashboard as full, priya } from '@/test/marketing-fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { MarketingContext, ProviderInfo } from '../api';
import MarketingDashboardPage from './MarketingDashboardPage';

const originalAdapter = api.defaults.adapter;

const context: MarketingContext = {
  today: '2026-10-09',
  currentPeriod: { month: 10, year: 2026, label: 'October 2026' },
  years: [2027, 2026, 2025, 2024, 2023],
  owners: [priya, arun],
  behindThresholdPct: 60,
};

const providers: ProviderInfo[] = [
  { code: 'MANUAL', name: 'Manual entry', category: 'SEO_RANKINGS', automated: false, connected: false, description: 'Positions are recorded monthly.', planned: ['Semrush', 'Google Search Console'] },
];

type Handler = (config: InternalAxiosRequestConfig) => unknown;
function routes(extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /marketing/context': () => context,
    'GET /marketing/integrations': () => ({ providers }),
    'GET /marketing/imports': () => [],
    'GET /marketing/dashboard': () => full,
    ...extra,
  };
}

describe('MarketingDashboardPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows the KPI row for the business month with links to each page', async () => {
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'GET /marketing/dashboard': (config) => (requests.push(config), full) }));
    renderPage(<MarketingDashboardPage />, superAdmin);

    const kpis = await screen.findByRole('region', { name: 'Marketing performance for October 2026' });
    expect(requests[0]?.params).toEqual({ month: 10, year: 2026, months: 6 });
    expect(screen.getByLabelText('Month')).toHaveValue('10');

    const seo = within(kpis).getByRole('link', { name: /SEO · Top 10 keywords/ });
    expect(seo).toHaveAttribute('href', '/digital-marketing/seo');
    expect(seo).toHaveTextContent('42');
    expect(seo).toHaveTextContent('Up 20% vs September');
    expect(seo).toHaveTextContent('18 improved · 7 declined');
    const leads = within(kpis).getByRole('link', { name: /^Leads/ });
    expect(leads).toHaveTextContent('200');
    expect(within(leads).getByRole('progressbar', { name: 'Leads against target' })).toHaveAttribute('aria-valuenow', '80');
    expect(within(kpis).getByRole('link', { name: /Email · Open rate/ })).toHaveTextContent('35.42%');
    const linkedin = within(kpis).getByRole('link', { name: /LinkedIn · Leads/ });
    expect(linkedin).toHaveTextContent('84');
    expect(linkedin).toHaveTextContent('CPL ₹500');
    expect(within(kpis).getByRole('link', { name: /Backlinks · Live/ })).toHaveTextContent('15 to submit');
    expect(within(kpis).getByRole('link', { name: /Content · Blogs published/ })).toHaveTextContent('3 remaining · 45 leads');
  });

  it('shows the SEO overview, targets, lead sources, campaigns, backlinks, content and activities', async () => {
    mockApi(routes());
    renderPage(<MarketingDashboardPage />, superAdmin);

    expect(await screen.findByRole('img', { name: 'TOP 10 42, RANKING 12, NOT RANKED 12' })).toBeInTheDocument();
    const position = screen.getByText('Average position').closest('div')!;
    expect(position).toHaveTextContent('9.4');
    // A lower average position is better: the drop reads as good news, in words.
    expect(within(position).getByText(/Down 16.1% vs September/)).toHaveClass('text-status-success');

    const targets = screen.getByRole('list', { name: 'Targets' });
    expect(within(targets).getAllByRole('listitem')).toHaveLength(3);
    expect(within(targets).getByText('Behind')).toBeInTheDocument();
    expect(screen.getByText('0 achieved · 2 in progress · 1 behind')).toBeInTheDocument();

    expect(within(screen.getByRole('list', { name: 'Leads by source' })).getAllByRole('listitem')[0]).toHaveTextContent('Organic80 (70 in September)');
    expect(screen.getByText('Website Leads target').closest('div')!.parentElement).toHaveTextContent('50 remaining');

    expect(screen.getByText('Open rate').closest('div')!.parentElement).toHaveTextContent('Up 3.84 pts vs September');
    const cpl = screen.getByText('Cost per lead').closest('div')!.parentElement!;
    expect(within(cpl).getByText(/Down 37.5% vs September/)).toHaveClass('text-status-success');
    expect(screen.getByRole('progressbar', { name: 'LinkedIn budget used' })).toHaveAttribute('aria-valuenow', '84');

    expect(within(screen.getByRole('list', { name: 'Backlink funnel' })).getAllByRole('listitem').map((li) => li.textContent)).toEqual(['Target50', 'Submitted35', 'Approved28', 'Live22']);
    expect(screen.getByText(/still to submit/)).toHaveTextContent('15 still to submit · live 44% of target');
    expect(screen.getByText(/remaining · 75% of target/)).toHaveTextContent('3 remaining · 75% of target');

    const attention = screen.getByRole('list', { name: 'Needs attention' });
    expect(attention).toHaveTextContent('Monthly SEO ranking update');
    expect(attention).toHaveTextContent('Arun Kumar');
    expect(screen.getByText('75% done')).toBeInTheDocument();
  });

  it('charts one trend metric at a time with every metric in the table', async () => {
    const user = userEvent.setup();
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'GET /marketing/dashboard': (config) => (requests.push(config), full) }));
    renderPage(<MarketingDashboardPage />, superAdmin);

    expect(await screen.findByRole('figure', { name: 'Leads by month' })).toBeInTheDocument();
    const rows = within(screen.getByRole('table', { name: 'Monthly trend' })).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('October 2026200424584₹42,000229');
    await user.selectOptions(screen.getByLabelText('Trend metric'), 'backlinksLive');
    expect(screen.getByRole('figure', { name: 'Backlinks live by month' })).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Trend range'), '12');
    expect(requests.at(-1)?.params).toMatchObject({ months: 12 });
  });

  it('shows only the activities to a viewer without module permissions', async () => {
    mockApi(routes({ 'GET /marketing/dashboard': () => bare }));
    renderPage(<MarketingDashboardPage />, makeUser(['EMPLOYEE'], ['MARKETING_VIEW']));

    expect(await screen.findByText('Recurring activities')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: /Marketing performance/ })).not.toBeInTheDocument();
    expect(screen.queryByText('SEO ranking overview')).not.toBeInTheDocument();
    expect(screen.queryByText('Target achievement')).not.toBeInTheDocument();
    expect(screen.getByText('No marketing modules to chart for your access.')).toBeInTheDocument();
    expect(screen.getByText('Nothing open is due by the end of the month.')).toBeInTheDocument();
  });

  it('changes the period and owner, and resets to the current month', async () => {
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'GET /marketing/dashboard': (config) => (requests.push(config), full) }));
    renderPage(<MarketingDashboardPage />, seoExecutive);

    await userEvent.selectOptions(await screen.findByLabelText('Month'), '9');
    await userEvent.selectOptions(screen.getByLabelText('Owner'), 'Arun Kumar');
    expect(screen.getByLabelText('Month')).toHaveValue('9');
    expect(requests.at(-1)?.params).toEqual({ month: 9, year: 2026, ownerId: 12, months: 6 });

    await userEvent.click(screen.getByRole('button', { name: 'Reset to October 2026' }));
    expect(screen.getByLabelText('Owner')).toHaveValue('');
    expect(requests.at(-1)?.params).toEqual({ month: 10, year: 2026, months: 6 });
  });

  it('reads the filters from the URL', async () => {
    mockApi(routes());
    renderPage(<MarketingDashboardPage />, seoExecutive, '/digital-marketing?month=3&year=2025&owner=11');

    expect(await screen.findByLabelText('Month')).toHaveValue('3');
    expect(screen.getByLabelText('Year')).toHaveValue('2025');
    expect(screen.getByLabelText('Owner')).toHaveValue('11');
    expect(screen.getByText('March 2025')).toBeInTheDocument();
  });

  it('keeps the rating legend, data sources and the imports the viewer may run', async () => {
    mockApi(
      routes({
        'GET /marketing/imports': () => [
          { type: 'keywords', label: 'Keywords', description: 'SEO keywords per page.', maxRows: 5000, columns: [{ name: 'keyword', required: true, description: 'Phrase', example: 'sap' }] },
        ],
      }),
    );
    renderPage(<MarketingDashboardPage />, makeUser(['SUPER_ADMIN'], ['MARKETING_VIEW', 'SEO_VIEW', 'SEO_EDIT']));

    expect(await screen.findByText('Ready for: Semrush, Google Search Console')).toBeInTheDocument();
    expect(screen.getByText(/Below 60% of target/)).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: 'Import Keywords' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'Import keywords' })).toBeInTheDocument();
  });

  it('shows an error state with retry when the dashboard fails', async () => {
    mockApi(
      routes({
        'GET /marketing/dashboard': () => {
          throw new Error('boom');
        },
      }),
    );
    renderPage(<MarketingDashboardPage />, superAdmin);

    expect(await screen.findByText("Couldn't load the marketing dashboard")).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Try again|Retry/ })).toBeInTheDocument();
  });
});
