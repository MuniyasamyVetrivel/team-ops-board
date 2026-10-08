import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, seoExecutive } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { MarketingContext, ProviderInfo } from './api';
import MarketingHomePage from './MarketingHomePage';

const originalAdapter = api.defaults.adapter;

const context: MarketingContext = {
  today: '2026-10-08',
  currentPeriod: { month: 10, year: 2026, label: 'October 2026' },
  years: [2027, 2026, 2025, 2024, 2023],
  owners: [
    { id: 11, fullName: 'Priya Menon', email: 'priya.menon@teamops.local', jobTitle: 'Digital Marketing Manager', status: 'ACTIVE' },
    { id: 12, fullName: 'Arun Kumar', email: 'arun.kumar@teamops.local', jobTitle: 'SEO Executive', status: 'ACTIVE' },
  ],
  behindThresholdPct: 60,
};

const providers: ProviderInfo[] = [
  { code: 'MANUAL', name: 'Manual entry', category: 'SEO_RANKINGS', automated: false, connected: false, description: 'Positions are recorded monthly.', planned: ['Semrush', 'Google Search Console'] },
];

function routes(imports: unknown[] = []) {
  return {
    'GET /marketing/context': () => context,
    'GET /marketing/integrations': () => ({ providers }),
    'GET /marketing/imports': () => imports,
  };
}

describe('MarketingHomePage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('defaults to the business month from the server and lists only permitted pages', async () => {
    mockApi(routes());
    renderPage(<MarketingHomePage />, seoExecutive);

    expect(await screen.findByLabelText('Month')).toHaveValue('10');
    expect(screen.getByLabelText('Year')).toHaveValue('2026');
    expect(screen.getByLabelText('Owner')).toHaveValue('');
    expect(screen.getByText('October 2026')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Reset to/ })).not.toBeInTheDocument();

    expect(screen.getByRole('link', { name: /SEO Rankings/ })).toHaveAttribute('href', '/digital-marketing/seo');
    expect(screen.getByRole('link', { name: /Backlinks/ })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Leads/ })).not.toBeInTheDocument();

    expect(await screen.findByText('Ready for: Semrush, Google Search Console')).toBeInTheDocument();
    expect(screen.getByText('No imports available to you yet')).toBeInTheDocument();
    expect(screen.getByText(/Below 60% of target/)).toBeInTheDocument();
  });

  it('changes the period and owner, and resets to the current month', async () => {
    mockApi(routes());
    renderPage(<MarketingHomePage />, seoExecutive);

    await userEvent.selectOptions(await screen.findByLabelText('Month'), '9');
    await userEvent.selectOptions(screen.getByLabelText('Owner'), 'Arun Kumar');
    expect(screen.getByText('September 2026')).toBeInTheDocument();
    expect(screen.getByLabelText('Owner')).toHaveValue('12');

    await userEvent.click(screen.getByRole('button', { name: 'Reset to October 2026' }));
    expect(screen.getByText('October 2026')).toBeInTheDocument();
    expect(screen.getByLabelText('Owner')).toHaveValue('');
  });

  it('reads the filters from the URL', async () => {
    mockApi(routes());
    renderPage(<MarketingHomePage />, seoExecutive, '/digital-marketing?month=3&year=2025&owner=11');

    expect(await screen.findByLabelText('Month')).toHaveValue('3');
    expect(screen.getByLabelText('Year')).toHaveValue('2025');
    expect(screen.getByLabelText('Owner')).toHaveValue('11');
    expect(screen.getByText('March 2025')).toBeInTheDocument();
  });

  it('offers the imports the viewer may run', async () => {
    mockApi(
      routes([
        { type: 'keywords', label: 'Keywords', description: 'SEO keywords per page.', maxRows: 5000, columns: [{ name: 'keyword', required: true, description: 'Phrase', example: 'sap' }] },
      ]),
    );
    renderPage(<MarketingHomePage />, makeUser(['SUPER_ADMIN'], ['MARKETING_VIEW', 'SEO_VIEW', 'SEO_EDIT']));

    await userEvent.click(await screen.findByRole('button', { name: 'Import Keywords' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'Import keywords' })).toBeInTheDocument();
  });
});
