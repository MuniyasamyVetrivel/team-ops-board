import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { page } from '@/test/celebration-fixtures';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';
import { contract, vendor, vendorEditor, vendorTool } from '@/test/vendor-fixtures';
import { vendorRoutes } from '@/test/vendor-routes';

import type { VendorLinkGroup } from './api';
import VendorPage from './VendorPage';

const originalAdapter = api.defaults.adapter;

function renderVendor(user = superAdmin, links: VendorLinkGroup[] = [], hidden = false) {
  mockApi({
    'GET /department-tools': () => [vendorTool(true)],
    'GET /modules/vendors/vendors/5': () => vendor(),
    'GET /modules/vendors/vendors/5/links': () => links,
    'GET /modules/vendors/contracts': () =>
      page([contract(hidden ? { value: null, valueHidden: true } : {}), contract({ id: 22, title: 'Old support', status: 'EXPIRED', daysUntilRenewal: null, documents: [] })]),
  });
  renderPage(vendorRoutes(':id', <VendorPage />), user, '/tools/vendors/5');
}

describe('VendorPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows the vendor with its main contact first', async () => {
    renderVendor();

    expect(await screen.findByRole('heading', { name: 'Atlassian' })).toBeInTheDocument();
    const contacts = screen.getByRole('list', { name: 'Contacts' });
    expect(within(contacts).getAllByRole('listitem')[0]).toHaveTextContent('Priya Shah');
    expect(within(contacts).getAllByRole('listitem')[0]).toHaveTextContent('Main contact');
    expect(within(contacts).getByRole('link', { name: 'priya@atlassian.example' })).toHaveAttribute('href', 'mailto:priya@atlassian.example');
    expect(screen.getByRole('link', { name: /atlassian\.com/ })).toHaveAttribute('rel', 'noopener noreferrer');
    expect(screen.getByRole('button', { name: 'New contract' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'History' })).toBeInTheDocument();
  });

  it('lists its contracts with renewal, notice and documents', async () => {
    renderVendor();
    await userEvent.click(await screen.findByRole('tab', { name: /Contracts/ }));

    const row = (await screen.findByRole('button', { name: 'Jira Cloud Premium' })).closest('tr')!;
    expect(within(row).getByText('Renews in 20 days')).toBeInTheDocument();
    expect(within(row).getByText(/AUD\s?18,500/)).toBeInTheDocument();
    expect(within(row).getByText('Notice deadline passed')).toBeInTheDocument();
    expect(within(row).getByLabelText('1 documents')).toBeInTheDocument();
    expect(within(screen.getByRole('button', { name: 'Old support' }).closest('tr')!).getByText('Expired')).toBeInTheDocument();
  });

  it('shows "Hidden" instead of the value to people who cannot see finance', async () => {
    renderVendor(vendorEditor, [], true);
    await userEvent.click(await screen.findByRole('tab', { name: /Contracts/ }));

    const row = (await screen.findByRole('button', { name: 'Jira Cloud Premium' })).closest('tr')!;
    expect(within(row).getByText('Hidden')).toBeInTheDocument();
    expect(within(row).queryByText(/18,500/)).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'History' })).not.toBeInTheDocument();
  });

  it('keeps a place for licences and expenses until the Finance Tracker links them', async () => {
    renderVendor();
    await userEvent.click(await screen.findByRole('tab', { name: 'Licences and expenses' }));

    expect(await screen.findByText('No licences or expenses linked yet')).toBeInTheDocument();
  });

  it('lists linked records the server lets the viewer see', async () => {
    renderVendor(superAdmin, [{ kind: 'LICENCES', label: 'Tools and licences', total: 3, items: [{ id: 1, title: 'Jira Cloud', detail: '18 of 20 seats', status: 'ACTIVE' }] }]);
    await userEvent.click(await screen.findByRole('tab', { name: 'Licences and expenses' }));

    const group = await screen.findByRole('region', { name: 'Tools and licences' });
    expect(within(group).getByText('Jira Cloud')).toBeInTheDocument();
    expect(within(group).getByText('Showing the newest 1 of 3.')).toBeInTheDocument();
  });
});
