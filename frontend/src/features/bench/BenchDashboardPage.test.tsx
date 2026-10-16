import { screen, within } from '@testing-library/react';
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { benchTool, dashboard } from '@/test/bench-fixtures';
import { benchRoutes } from '@/test/bench-routes';
import { superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import BenchDashboardPage from './BenchDashboardPage';

const originalAdapter = api.defaults.adapter;

function renderDashboard(user = superAdmin) {
  return renderPage(benchRoutes('', <BenchDashboardPage />), user, '/tools/bench');
}

function forbidden(code: string) {
  return (config: InternalAxiosRequestConfig) => {
    const response = { data: { status: 403, code, message: 'Bench Management is switched off', error: 'Forbidden', timestamp: '', path: '' }, status: 403, statusText: 'Forbidden', headers: {}, config };
    throw new AxiosError('Forbidden', 'ERR_BAD_REQUEST', config, null, { ...response, headers: new AxiosHeaders() });
  };
}

describe('BenchDashboardPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows utilisation, the bench, roll-offs and bench skills as the server computed them', async () => {
    mockApi({ 'GET /department-tools': () => [benchTool(true)], 'GET /modules/bench/dashboard': () => dashboard() });
    renderDashboard();

    const figures = await screen.findByRole('region', { name: 'Bench figures' });
    expect(within(figures).getByText('70%')).toBeInTheDocument();
    expect(within(figures).getByText('3.50 of 5.00 FTE allocated')).toBeInTheDocument();
    expect(within(figures).getByText('60%')).toBeInTheDocument();
    expect(within(figures).getByText('2')).toBeInTheDocument();
    expect(within(figures).getByText('Longest: 59 days')).toBeInTheDocument();

    expect(screen.getByText('Ends in 19 days')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Meera Iyer/ })).toHaveAttribute('href', '/tools/bench/consultants/8');
    expect(within(screen.getByRole('list', { name: 'Skills on the bench' })).getByRole('link', { name: /JMeter/ })).toHaveAttribute('href', '/tools/bench/on-bench?skill=JMeter');
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page');
  });

  it('shows a dash for percentages when nobody is available yet', async () => {
    mockApi({
      'GET /department-tools': () => [benchTool(false)],
      'GET /modules/bench/dashboard': () =>
        dashboard({ capacityConsultants: 0, capacityFte: 0, allocatedFte: 0, billableFte: 0, nonBillableFte: 0, benchFte: 0, utilisationPercent: null, billablePercent: null, nonBillablePercent: null }),
    });
    renderDashboard();

    const figures = await screen.findByRole('region', { name: 'Bench figures' });
    expect(within(figures).getAllByText('—').length).toBeGreaterThanOrEqual(2);
    expect(within(figures).getByText('Nobody is available yet')).toBeInTheDocument();
    expect(screen.getByText('View only')).toBeInTheDocument();
  });

  it('invites adding consultants when there are none', async () => {
    mockApi({ 'GET /department-tools': () => [benchTool(true)], 'GET /modules/bench/dashboard': () => dashboard({ byStatus: [] }) });
    renderDashboard();

    expect(await screen.findByText('No consultants yet')).toBeInTheDocument();
  });

  it('says so when the module was switched off for the department', async () => {
    mockApi({ 'GET /department-tools': () => [benchTool(true)], 'GET /modules/bench/dashboard': forbidden('MODULE_DISABLED') });
    renderDashboard();

    expect(await screen.findByText('This tool is not switched on for your department')).toBeInTheDocument();
  });

  it('turns away users the tool is not offered to', async () => {
    mockApi({ 'GET /department-tools': () => [] });
    renderDashboard(webEmployee);

    expect(await screen.findByText("This tool isn't available to you")).toBeInTheDocument();
  });
});
