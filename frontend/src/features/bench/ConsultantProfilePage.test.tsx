import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { allocation, benchTool, consultant, dashboard } from '@/test/bench-fixtures';
import { benchRoutes } from '@/test/bench-routes';
import { page } from '@/test/celebration-fixtures';
import { superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import ConsultantProfilePage from './ConsultantProfilePage';

const originalAdapter = api.defaults.adapter;

function renderProfile(user = superAdmin, canEdit = true, overrides: Parameters<typeof consultant>[0] = {}) {
  mockApi({
    'GET /department-tools': () => [benchTool(canEdit)],
    'GET /modules/bench/consultants/7': () => consultant(overrides),
    'GET /modules/bench/allocations': () => page([allocation(), allocation({ id: 13, billable: false, status: 'ENDED', startDate: '2026-06-01', endDate: '2026-08-31', daysUntilEnd: null, role: 'Shadow' })]),
    'GET /modules/bench/dashboard': () => dashboard(),
  });
  renderPage(benchRoutes('consultants/:id', <ConsultantProfilePage />), user, '/tools/bench/consultants/7');
}

describe('ConsultantProfilePage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows the Employee record, skills and certifications', async () => {
    renderProfile();

    expect(await screen.findByRole('heading', { name: 'Arjun Rao' })).toBeInTheDocument();
    expect(screen.getByText('TRI-0101 · Senior Test Engineer · Delivery')).toBeInTheDocument();
    expect(screen.getByText('Partially allocated')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'SAP S/4HANA' })).toHaveAttribute('href', '/tools/bench/on-bench?skill=SAP%20S%2F4HANA');
    expect(screen.getByText('Tosca Automation Specialist')).toBeInTheDocument();
    expect(screen.getByText('Valid')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Edit consultant' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'History' })).toBeInTheDocument();
  });

  it('lays allocations out on a timeline, billable and non-billable told apart in words', async () => {
    renderProfile();
    await userEvent.click(await screen.findByRole('tab', { name: 'Allocations' }));

    const timeline = await screen.findByRole('list', { name: 'Allocation timeline' });
    expect(timeline).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Test automation lead, 50%, billable/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Shadow, 50%, non-billable.*ended/ })).toBeInTheDocument();
  });

  it('hides edit controls and History from view-only users without HISTORY_VIEW', async () => {
    renderProfile(webEmployee, false);

    await screen.findByRole('heading', { name: 'Arjun Rao' });
    expect(screen.queryByRole('button', { name: 'Edit consultant' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Allocate' })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'History' })).not.toBeInTheDocument();
  });

  it('explains a consultant whose Employee record is in the recycle bin', async () => {
    renderProfile(superAdmin, true, { employee: null });

    expect(await screen.findByRole('heading', { name: 'Employee record deleted' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('in the recycle bin');
  });
});
