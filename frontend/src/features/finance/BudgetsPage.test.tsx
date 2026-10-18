import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import type { CurrentUser } from '@/features/auth/types';
import { api } from '@/lib/api/client';
import { page } from '@/test/celebration-fixtures';
import { budgetSummary, financeContext, financeTool, financeViewer } from '@/test/finance-fixtures';
import { financeRoutes } from '@/test/finance-routes';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { Budget, SaveBudgetInput } from './api';
import BudgetsPage from './BudgetsPage';

const originalAdapter = api.defaults.adapter;

const engineering: Budget = {
  id: 31,
  department: { id: 2, name: 'Engineering', code: 'ENG' },
  year: 2026,
  month: 10,
  amount: 1000,
  actual: 1250,
  remaining: 0,
  usedPercent: 125,
  overBudget: true,
  overBy: 250,
  notes: null,
  version: 3,
  createdAt: '2026-10-01T04:00:00Z',
  updatedAt: '2026-10-01T04:00:00Z',
};

function renderBudgets(user: CurrentUser = superAdmin, canEdit = true, path = '/tools/finance/budgets') {
  const saved: { method: string; url: string; body: SaveBudgetInput }[] = [];
  const adapter = mockApi({
    'GET /department-tools': () => [financeTool(canEdit)],
    'GET /modules/finance/context': () => financeContext(),
    'GET /modules/finance/budgets/summary': (config) => {
      const { year, month } = config.params as { year: number; month: number };
      return year === 2026 && month === 10 ? budgetSummary() : budgetSummary({ year, month, lines: [], totalBudget: 0, totalActual: 0, totalActualBudgeted: 0, overBudgetDepartments: 0 });
    },
    'GET /modules/finance/budgets': () => page([engineering]),
    'GET /departments': () => [
      { id: 2, name: 'Engineering', code: 'ENG', description: null, status: 'ACTIVE', manager: null },
      { id: 8, name: 'Sales', code: 'SALES', description: null, status: 'ACTIVE', manager: null },
    ],
    'PUT /modules/finance/budgets/31': (config) => {
      saved.push({ method: 'PUT', url: config.url!, body: JSON.parse(config.data as string) as SaveBudgetInput });
      return engineering;
    },
    'POST /modules/finance/budgets': (config) => {
      saved.push({ method: 'POST', url: config.url!, body: JSON.parse(config.data as string) as SaveBudgetInput });
      return { ...engineering, id: 33 };
    },
  });
  renderPage(financeRoutes('budgets', <BudgetsPage />), user, path);
  return { adapter, saved };
}

describe('BudgetsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it("compares this month's budgets with actual spend, with a worded status", async () => {
    renderBudgets();

    const engineeringRow = (await screen.findByText('Engineering')).closest('tr')!;
    expect(within(engineeringRow).getByText('Over budget')).toBeInTheDocument();
    expect(within(engineeringRow).getByText(/−.*250\.00/)).toBeInTheDocument();
    expect(within(engineeringRow).getByText('125%')).toBeInTheDocument();
    expect(within(screen.getByText('HR').closest('tr')!).getByText('Within budget')).toBeInTheDocument();
    expect(within(screen.getByText('Sales').closest('tr')!).getByText('No budget')).toBeInTheDocument();
    expect(screen.getByText('No department')).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Month' })).getByText('October 2026')).toBeInTheDocument();
    expect(within(screen.getByRole('region', { name: 'Budget figures' })).getByText('Critical')).toBeInTheDocument();
  });

  it('edits a budget with its version and sets one for a department without', async () => {
    const { saved } = renderBudgets();

    const edit = await screen.findByRole('button', { name: 'Edit for Engineering' });
    await userEvent.click(edit);
    const amount = await screen.findByLabelText(/^Amount/);
    expect(amount).toHaveValue('1000.00');
    await userEvent.clear(amount);
    await userEvent.type(amount, '1500');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0]).toEqual({ method: 'PUT', url: '/modules/finance/budgets/31', body: { version: 3, departmentId: 2, year: 2026, month: 10, amount: 1500, notes: null } });

    await userEvent.click(screen.getByRole('button', { name: 'Set budget for Sales' }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByLabelText(/^Department/)).toHaveValue('8');
    expect(within(dialog).getByLabelText(/^Month/)).toHaveValue('2026-10');
  });

  it('opens the month in the URL and moves between months', async () => {
    renderBudgets(superAdmin, true, '/tools/finance/budgets?month=2026-09');

    expect(await screen.findByText('No budgets or spend in September 2026')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next month' }));
    expect(await screen.findByText('Engineering')).toBeInTheDocument();
  });

  it('shows budgets without editing to view-only users', async () => {
    renderBudgets(financeViewer, false);

    await screen.findByText('Engineering');
    expect(screen.queryByRole('button', { name: 'New budget' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Edit for/ })).not.toBeInTheDocument();
  });
});
