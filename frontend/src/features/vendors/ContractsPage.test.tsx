import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { page } from '@/test/celebration-fixtures';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';
import { contract, vendor, vendorEditor, vendorTool } from '@/test/vendor-fixtures';
import { vendorRoutes } from '@/test/vendor-routes';

import ContractsPage from './ContractsPage';

const originalAdapter = api.defaults.adapter;

function renderContracts(user = superAdmin, canEdit = true, path = '/tools/vendors/contracts') {
  mockApi({
    'GET /department-tools': () => [vendorTool(canEdit)],
    'GET /modules/vendors/vendors': () => page([vendor()]),
    'GET /modules/vendors/contracts': () => page([contract()]),
    'GET /modules/vendors/contracts/21': () => contract(),
    'GET /modules/vendors/imports': () => [],
  });
  renderPage(vendorRoutes('contracts', <ContractsPage />), user, path);
}

describe('ContractsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('opens a contract from the URL with its documents', async () => {
    renderContracts(superAdmin, true, '/tools/vendors/contracts?contract=21');

    const documents = await screen.findByRole('region', { name: 'Documents' });
    expect(within(documents).getByText('signed contract.pdf')).toBeInTheDocument();
    expect(within(documents).getByRole('button', { name: 'Download signed contract.pdf' })).toBeInTheDocument();
    expect(within(documents).getByRole('button', { name: 'Remove signed contract.pdf' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Edit contract' })).toBeInTheDocument();
  });

  it('offers sorting by value only to people who can see finance', async () => {
    renderContracts();
    await screen.findByRole('button', { name: 'Jira Cloud Premium' });
    expect(within(screen.getByRole('combobox', { name: 'Sort contracts' })).getByRole('option', { name: 'Highest value' })).toBeInTheDocument();
  });

  it('hides value sorting and editing from view-only people without finance', async () => {
    renderContracts(vendorEditor, false);
    await screen.findByRole('button', { name: 'Jira Cloud Premium' });

    expect(within(screen.getByRole('combobox', { name: 'Sort contracts' })).queryByRole('option', { name: 'Highest value' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New contract' })).not.toBeInTheDocument();
  });
});
