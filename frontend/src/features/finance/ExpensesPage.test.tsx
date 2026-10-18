import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import type { CurrentUser } from '@/features/auth/types';
import { api } from '@/lib/api/client';
import { page } from '@/test/celebration-fixtures';
import { expense, financeContext, financeTool, financeViewer } from '@/test/finance-fixtures';
import { financeRoutes } from '@/test/finance-routes';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { SaveExpenseInput } from './api';
import ExpensesPage from './ExpensesPage';

const originalAdapter = api.defaults.adapter;

function noRate(config: InternalAxiosRequestConfig) {
  const response = { data: { status: 400, code: 'NO_EXCHANGE_RATE', message: 'No SGD rate', error: 'Bad Request', timestamp: '', path: '' }, status: 400, statusText: 'Bad Request', headers: {}, config };
  throw new AxiosError('Bad Request', 'ERR_BAD_REQUEST', config, null, { ...response, headers: new AxiosHeaders() });
}

function renderExpenses({ user = superAdmin, canEdit = true, path = '/tools/finance/expenses', convert }: { user?: CurrentUser; canEdit?: boolean; path?: string; convert?: (config: InternalAxiosRequestConfig) => unknown } = {}) {
  const saved: SaveExpenseInput[] = [];
  // The receipt as the server holds it: a change is read back by the refetch that follows it.
  let receipt = expense().receipt;
  const adapter = mockApi({
    'GET /department-tools': () => [financeTool(canEdit)],
    'GET /modules/finance/expenses': () => page([expense(), expense({ id: 41, description: 'Coffee beans', currency: 'AUD', audRate: 1, audRateDate: null, amountAud: 50, amount: 50, gst: null, receipt: null })]),
    'GET /modules/finance/expenses/40': () => expense({ receipt }),
    'POST /modules/finance/expenses/40/receipt': () => {
      receipt = { fileId: 78, fileName: 'new receipt.pdf', contentType: 'application/pdf', sizeBytes: 1000, addedAt: '2026-10-19T04:00:00Z' };
      return expense({ receipt });
    },
    'DELETE /modules/finance/expenses/40/receipt': () => {
      receipt = null;
      return expense({ receipt });
    },
    'GET /modules/finance/context': () => financeContext({ defaultCurrency: 'SGD' }),
    'GET /modules/finance/exchange-rates/convert': convert ?? (() => ({ amount: 200, currency: 'SGD', date: '2026-10-19', rate: 1.15, rateFrom: '2026-09-30', aud: 230 })),
    'GET /departments': () => [],
    'GET /approvals': () => page([]),
    'POST /modules/finance/expenses': (config) => {
      const input = JSON.parse(config.data as string) as SaveExpenseInput;
      saved.push(input);
      return expense({ id: 42, description: input.description });
    },
  });
  renderPage(financeRoutes('expenses', <ExpensesPage />), user, path);
  return { adapter, saved };
}

describe('ExpensesPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists expenses with their AUD amounts and receipts', async () => {
    renderExpenses();

    const row = (await screen.findByRole('button', { name: 'Flights to Sydney' })).closest('tr')!;
    expect(within(row).getByText(/1,150\.00/)).toBeInTheDocument();
    expect(within(row).getByText('Yes')).toBeInTheDocument();
    const coffee = screen.getByRole('button', { name: 'Coffee beans' }).closest('tr')!;
    expect(within(coffee).getByText('No')).toBeInTheDocument();
  });

  it('opens an expense from the URL with the rate it used, its approval and receipt, and replaces or removes the receipt', async () => {
    const { adapter } = renderExpenses({ path: '/tools/finance/expenses?expense=40' });

    const receipt = await screen.findByRole('region', { name: 'Receipt' });
    expect(within(receipt).getByText('flight receipt.pdf')).toBeInTheDocument();
    expect(within(receipt).getByRole('button', { name: 'Download flight receipt.pdf' })).toBeInTheDocument();
    expect(screen.getByText('1 SGD = 1.15 AUD')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /APR-000012/ })).toHaveAttribute('href', '/approvals?approval=12');

    await userEvent.upload(within(receipt).getByLabelText('Replace the receipt'), new File(['%PDF'], 'new receipt.pdf', { type: 'application/pdf' }));
    expect(await within(receipt).findByText('new receipt.pdf')).toBeInTheDocument();
    expect(adapter.mock.calls.some(([config]) => config.method === 'post' && config.url === '/modules/finance/expenses/40/receipt' && config.data instanceof FormData)).toBe(true);

    await userEvent.click(within(receipt).getByRole('button', { name: 'Remove new receipt.pdf' }));
    await userEvent.click(within(receipt).getByRole('button', { name: 'Confirm' }));
    expect(await within(receipt).findByText(/No receipt yet/)).toBeInTheDocument();
  });

  it('starts a new expense today in the default currency and shows its AUD amount', async () => {
    const { saved } = renderExpenses();
    await userEvent.click(await screen.findByRole('button', { name: 'New expense' }));

    expect(await screen.findByLabelText(/^Date/)).toHaveValue('2026-10-19');
    expect(screen.getByLabelText(/^Currency/)).toHaveValue('SGD');
    await userEvent.type(screen.getByLabelText(/^Description/), 'Taxi');
    await userEvent.type(screen.getByLabelText(/^Amount/), '200');
    expect(await screen.findByText(/230\.00 at 1\.15/)).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/^GST included/), '250');
    await userEvent.click(screen.getByRole('button', { name: 'Add expense' }));
    expect(await screen.findByText(/cannot be more than the amount/)).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText(/^GST included/));
    await userEvent.click(screen.getByRole('button', { name: 'Add expense' }));
    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0]).toMatchObject({ description: 'Taxi', amount: 200, gst: null, currency: 'SGD', expenseDate: '2026-10-19', category: 'SOFTWARE' });
  });

  it('flags a currency with no exchange rate on the date', async () => {
    renderExpenses({ convert: noRate });
    await userEvent.click(await screen.findByRole('button', { name: 'New expense' }));
    await userEvent.type(await screen.findByLabelText(/^Amount/), '200');

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent("There is no SGD exchange rate on or before");
    expect(within(alert).getByRole('link', { name: 'Add a rate' })).toHaveAttribute('href', '/tools/finance/exchange-rates');
  });

  it('lets view-only users download the receipt but not change it', async () => {
    renderExpenses({ user: financeViewer, canEdit: false, path: '/tools/finance/expenses?expense=40' });

    const receipt = await screen.findByRole('region', { name: 'Receipt' });
    expect(within(receipt).getByRole('button', { name: 'Download flight receipt.pdf' })).toBeInTheDocument();
    expect(within(receipt).queryByRole('button', { name: /Remove/ })).not.toBeInTheDocument();
    expect(within(receipt).queryByRole('button', { name: /Replace/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit expense' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New expense' })).not.toBeInTheDocument();
  });
});
