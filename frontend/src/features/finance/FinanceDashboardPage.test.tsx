import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { financeDashboard, financeTool } from '@/test/finance-fixtures';
import { financeRoutes } from '@/test/finance-routes';
import { superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import FinanceDashboardPage from './FinanceDashboardPage';

const originalAdapter = api.defaults.adapter;

function forbidden(code: string) {
  return (config: InternalAxiosRequestConfig) => {
    const response = { data: { status: 403, code, message: 'Finance Tracker is switched off', error: 'Forbidden', timestamp: '', path: '' }, status: 403, statusText: 'Forbidden', headers: {}, config };
    throw new AxiosError('Forbidden', 'ERR_BAD_REQUEST', config, null, { ...response, headers: new AxiosHeaders() });
  };
}

function renderDashboard(path = '/tools/finance', user = superAdmin) {
  renderPage(financeRoutes('', <FinanceDashboardPage />), user, path);
}

describe('FinanceDashboardPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it("shows the month's spend, budgets, annualised cost and renewals as the server computed them", async () => {
    mockApi({ 'GET /department-tools': () => [financeTool(true)], 'GET /modules/finance/dashboard': () => financeDashboard() });
    renderDashboard();

    const figures = await screen.findByRole('region', { name: 'Finance figures' });
    expect(within(figures).getByText(/2,125\.00/)).toBeInTheDocument();
    expect(within(figures).getByText('Up')).toBeInTheDocument();
    expect(within(figures).getByText('70.8%')).toBeInTheDocument();
    expect(within(figures).getByText('Critical')).toBeInTheDocument();
    expect(within(figures).getByText(/48,000\.00/)).toBeInTheDocument();
    expect(within(figures).getByRole('link', { name: /Renewals in 30 days/ })).toHaveAttribute('href', '/tools/finance/licences?renewalWithinDays=30&sort=renewal,asc');
    expect(within(figures).getByRole('link', { name: /Spend in/ })).toHaveAttribute('href', '/tools/finance/expenses?from=2026-10-01&to=2026-10-31');

    expect(within(screen.getByRole('list', { name: 'Upcoming renewals' })).getByRole('link', { name: 'Jira Software' })).toHaveAttribute('href', '/tools/finance/licences?licence=7');
    expect(within(screen.getByRole('list', { name: 'Under-used licences' })).getByText('2 of 5 seats')).toBeInTheDocument();
    expect(within(screen.getByRole('list', { name: 'Spend by department' })).getByRole('link', { name: 'Engineering' })).toHaveAttribute(
      'href',
      '/tools/finance/expenses?from=2026-10-01&to=2026-10-31&departmentId=2',
    );
    expect(within(screen.getByRole('list', { name: 'Spend by department' })).getByText('No department')).toBeInTheDocument();
    expect(screen.queryByText('Missing exchange rates.')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page');
  });

  it('flags licences left out of the annualised cost for want of an exchange rate', async () => {
    mockApi({ 'GET /department-tools': () => [financeTool(false)], 'GET /modules/finance/dashboard': () => financeDashboard({ licencesWithoutRate: 2 }) });
    renderDashboard();

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Missing exchange rates. 2 licences are in a currency without a rate');
    expect(within(alert).getByRole('link', { name: 'Add exchange rates' })).toHaveAttribute('href', '/tools/finance/exchange-rates');
    expect(screen.getByText('2 without an exchange rate left out')).toBeInTheDocument();
    expect(screen.getByText('View only')).toBeInTheDocument();
  });

  it('asks for the month in the URL and moves between months', async () => {
    const adapter = mockApi({
      'GET /department-tools': () => [financeTool(true)],
      'GET /modules/finance/dashboard': (config) => {
        const { year, month } = config.params as { year?: number; month?: number };
        return financeDashboard(year ? { year, month: month ?? 1, monthSpendAud: 0, changePercent: null, byDepartment: [], byCategory: [] } : {});
      },
    });
    renderDashboard('/tools/finance?month=2026-09');

    expect(await screen.findAllByText('No spend in September 2026')).toHaveLength(2);
    expect(adapter.mock.calls.some(([config]) => config.url === '/modules/finance/dashboard' && (config.params as { month?: number }).month === 9)).toBe(true);

    await userEvent.click(screen.getByRole('button', { name: 'Previous month' }));
    expect(await screen.findAllByText('No spend in August 2026')).toHaveLength(2);
  });

  it('says so when the module was switched off for the department', async () => {
    mockApi({ 'GET /department-tools': () => [financeTool(true)], 'GET /modules/finance/dashboard': forbidden('MODULE_DISABLED') });
    renderDashboard();

    expect(await screen.findByText('This tool is not switched on for your department')).toBeInTheDocument();
  });

  it('turns away users the tool is not offered to', async () => {
    mockApi({ 'GET /department-tools': () => [] });
    renderDashboard('/tools/finance', webEmployee);

    expect(await screen.findByText("This tool isn't available to you")).toBeInTheDocument();
  });
});
