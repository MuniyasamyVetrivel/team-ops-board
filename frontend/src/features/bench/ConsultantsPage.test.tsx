import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import type { ImportDefinition } from '@/features/marketing/api';
import { api } from '@/lib/api/client';
import { benchTool, consultant } from '@/test/bench-fixtures';
import { benchRoutes } from '@/test/bench-routes';
import { page } from '@/test/celebration-fixtures';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import ConsultantsPage from './ConsultantsPage';

const originalAdapter = api.defaults.adapter;

const importDefinition: ImportDefinition = {
  type: 'consultants',
  label: 'Consultants',
  description: 'One row per consultant',
  columns: [{ name: 'employee_code', required: true, description: 'Employee ID', example: 'TRI-0101' }],
  maxRows: 1000,
};

function renderConsultants(canEdit: boolean, routes: Record<string, Parameters<typeof mockApi>[0][string]> = {}) {
  const adapter = mockApi({
    'GET /department-tools': () => [benchTool(canEdit)],
    'GET /modules/bench/consultants': () => page([consultant()]),
    ...routes,
  });
  renderPage(benchRoutes('consultants', <ConsultantsPage />), superAdmin, '/tools/bench/consultants');
  return adapter;
}

describe('ConsultantsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists consultants with their computed status, allocation and skills', async () => {
    renderConsultants(false);

    const row = (await screen.findByRole('link', { name: /Arjun Rao/ })).closest('tr')!;
    expect(within(row).getByText('Partially allocated')).toBeInTheDocument();
    expect(within(row).getByText('50%')).toBeInTheDocument();
    expect(within(row).getByText('Tosca')).toBeInTheDocument();
    expect(within(row).getByRole('link', { name: /Arjun Rao/ })).toHaveAttribute('href', '/tools/bench/consultants/7');
  });

  it('lets view-only users export but not add or import', async () => {
    renderConsultants(false);

    await screen.findByRole('link', { name: /Arjun Rao/ });
    expect(screen.getByRole('button', { name: 'Export CSV' })).toBeEnabled();
    expect(screen.queryByRole('button', { name: 'Add consultant' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Import CSV' })).not.toBeInTheDocument();
  });

  it('offers add and the shared CSV import to editors', async () => {
    renderConsultants(true, { 'GET /modules/bench/imports/consultants': () => importDefinition });

    expect(await screen.findByRole('button', { name: 'Import CSV' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add consultant' })).toBeInTheDocument();
  });

  it('filters by computed status on the server', async () => {
    const adapter = renderConsultants(false);
    await screen.findByRole('link', { name: /Arjun Rao/ });

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Status' }), 'ON_BENCH');

    await screen.findByRole('link', { name: /Arjun Rao/ });
    const params = adapter.mock.calls.map(([config]) => config).filter((c) => c.url === '/modules/bench/consultants').at(-1)?.params as Record<string, unknown>;
    expect(params).toMatchObject({ status: 'ON_BENCH', page: 0 });
  });
});
