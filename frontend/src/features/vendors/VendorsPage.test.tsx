import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import type { ImportDefinition } from '@/features/marketing/api';
import { api } from '@/lib/api/client';
import { page } from '@/test/celebration-fixtures';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';
import { vendor, vendorTool } from '@/test/vendor-fixtures';
import { vendorRoutes } from '@/test/vendor-routes';

import VendorsPage from './VendorsPage';

const originalAdapter = api.defaults.adapter;

const definitions: ImportDefinition[] = [
  { type: 'vendors', label: 'Vendors', description: '', columns: [{ name: 'name', required: true, description: '', example: 'Atlassian' }], maxRows: 1000 },
  { type: 'vendor-contracts', label: 'Vendor contracts', description: '', columns: [], maxRows: 1000 },
];

function renderVendors(canEdit: boolean, routes: Record<string, Parameters<typeof mockApi>[0][string]> = {}) {
  const adapter = mockApi({
    'GET /department-tools': () => [vendorTool(canEdit)],
    'GET /modules/vendors/vendors': () => page([vendor(), vendor({ id: 6, name: 'Shady Supplies', status: 'BLACKLISTED', rating: null, contacts: [], contractCount: 0, nextRenewal: null })]),
    ...routes,
  });
  renderPage(vendorRoutes('', <VendorsPage />), superAdmin, '/tools/vendors');
  return adapter;
}

describe('VendorsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists vendors with status, rating, contracts, next renewal and the main contact', async () => {
    renderVendors(false);

    const row = (await screen.findByRole('link', { name: 'Atlassian' })).closest('tr')!;
    expect(screen.getByRole('link', { name: 'Atlassian' })).toHaveAttribute('href', '/tools/vendors/5');
    expect(within(row).getByText('Software')).toBeInTheDocument();
    expect(within(row).getByText('Active')).toBeInTheDocument();
    expect(within(row).getByLabelText('Rated 4 out of 5')).toBeInTheDocument();
    expect(within(row).getByText('Priya Shah')).toBeInTheDocument();
    const blacklisted = screen.getByRole('link', { name: 'Shady Supplies' }).closest('tr')!;
    expect(within(blacklisted).getByText('Blacklisted')).toBeInTheDocument();
    expect(within(blacklisted).getByText('Not rated')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Vendors' })).toHaveAttribute('aria-current', 'page');
  });

  it('filters by category, status and country on the server', async () => {
    const adapter = renderVendors(false);
    await screen.findByRole('link', { name: 'Atlassian' });

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Category' }), 'SOFTWARE');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Status' }), 'ACTIVE');
    await userEvent.type(screen.getByRole('textbox', { name: 'Country' }), 'Australia');

    // The country box is debounced: wait for the request that carries every filter.
    await waitFor(() => {
      const params = adapter.mock.calls.map(([config]) => config).filter((c) => c.url === '/modules/vendors/vendors').at(-1)?.params as Record<string, unknown>;
      expect(params).toMatchObject({ category: 'SOFTWARE', status: 'ACTIVE', country: 'Australia', page: 0 });
    });
  });

  it('lets view-only users export but not add or import', async () => {
    renderVendors(false);

    await screen.findByRole('link', { name: 'Atlassian' });
    expect(screen.getByRole('button', { name: 'Export CSV' })).toBeEnabled();
    expect(screen.queryByRole('button', { name: 'New vendor' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Import CSV' })).not.toBeInTheDocument();
  });

  it('offers new vendors and the shared CSV import to editors', async () => {
    renderVendors(true, { 'GET /modules/vendors/imports': () => definitions });

    expect(await screen.findByRole('button', { name: 'Import CSV' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New vendor' })).toBeInTheDocument();
  });
});

