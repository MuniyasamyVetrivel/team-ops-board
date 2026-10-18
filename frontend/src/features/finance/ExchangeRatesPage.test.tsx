import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import type { SettingItem } from '@/features/admin/settings/api';
import type { CurrentUser } from '@/features/auth/types';
import { api } from '@/lib/api/client';
import { page } from '@/test/celebration-fixtures';
import { financeContext, financeTool, financeViewer, rate } from '@/test/finance-fixtures';
import { financeRoutes } from '@/test/finance-routes';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import ExchangeRatesPage from './ExchangeRatesPage';

const originalAdapter = api.defaults.adapter;

const setting: SettingItem = {
  key: 'finance.defaultCurrency',
  group: 'Finance',
  label: 'Default currency',
  description: null,
  valueType: 'CHOICE',
  value: 'INR',
  unit: '',
  min: 0,
  max: 0,
  options: ['INR', 'AUD', 'USD', 'SGD'],
  updatedBy: null,
  updatedAt: '2026-10-01T04:00:00Z',
  version: 4,
};

function renderRates(user: CurrentUser = superAdmin, canEdit = true) {
  const updates: unknown[] = [];
  mockApi({
    'GET /department-tools': () => [financeTool(canEdit)],
    'GET /modules/finance/exchange-rates': () => page([rate(), rate({ id: 2, effectiveFrom: '2026-09-01', rateToAud: 1.45, inForce: false, source: null })]),
    'GET /modules/finance/exchange-rates/current': () => [rate()],
    'GET /modules/finance/context': () => financeContext(),
    'GET /modules/finance/imports': () => [],
    'GET /admin/settings': () => [setting],
    'PUT /admin/settings/finance.defaultCurrency': (config) => {
      const body = JSON.parse(config.data as string) as { value: string; version: number };
      updates.push(body);
      return { ...setting, value: body.value, version: 5 };
    },
  });
  renderPage(financeRoutes('exchange-rates', <ExchangeRatesPage />), user, '/tools/finance/exchange-rates');
  return updates;
}

describe('ExchangeRatesPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists every rate, marks the one in force and shows today’s rates', async () => {
    renderRates();

    const today = await screen.findByRole('list', { name: 'Rates in force today' });
    expect(within(today).getByText('USD')).toBeInTheDocument();
    const rows = await screen.findAllByRole('row');
    expect(within(rows[1]!).getByText('In force')).toBeInTheDocument();
    expect(within(rows[2]!).queryByText('In force')).not.toBeInTheDocument();
    expect(within(rows[2]!).getByText('1.45')).toBeInTheDocument();
  });

  it('lets a settings manager change the default currency', async () => {
    const updates = renderRates();

    const select = await screen.findByRole('combobox', { name: 'Default currency' });
    expect(select).toHaveValue('INR');
    await userEvent.selectOptions(select, 'AUD');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(updates).toEqual([{ value: 'AUD', version: 4 }]));
  });

  it('shows the default currency read-only, and rates without editing, to view-only users', async () => {
    renderRates(financeViewer, false);

    expect(await screen.findByText(/Your Super Admin sets it in Admin settings/)).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Default currency' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New rate' })).not.toBeInTheDocument();

    await userEvent.click((await screen.findAllByRole('button', { name: /^View the USD rate/ }))[0]!);
    expect(within(await screen.findByRole('dialog')).getByLabelText(/^Currency/)).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Save changes' })).not.toBeInTheDocument();
  });

  it('refuses a rate for AUD, the base currency', async () => {
    renderRates();
    await userEvent.click(await screen.findByRole('button', { name: 'New rate' }));

    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText(/^Currency/), 'aud');
    await userEvent.type(within(dialog).getByLabelText(/^1 unit in AUD/), '1');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add rate' }));
    expect(await screen.findByText(/AUD is the base currency/)).toBeInTheDocument();
  });
});
