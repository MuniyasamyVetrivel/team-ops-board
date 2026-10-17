import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { page } from '@/test/celebration-fixtures';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';
import { contract, vendorTool } from '@/test/vendor-fixtures';
import { vendorRoutes } from '@/test/vendor-routes';

import RenewalsPage from './RenewalsPage';

const originalAdapter = api.defaults.adapter;

function renderRenewals(contracts = [
  contract({ id: 1, title: 'Confluence', daysUntilRenewal: 25, noticePeriodDays: 10, noticeBy: '2026-11-01', renewsOn: '2026-11-11' }),
  contract({ id: 2, title: 'Jira Cloud Premium', daysUntilRenewal: 3, renewsOn: '2026-10-20' }),
  contract({ id: 3, title: 'Support', daysUntilRenewal: 6, renewalDate: null, autoRenew: false, noticePeriodDays: null, noticeBy: null, renewsOn: '2026-10-23' }),
]) {
  const adapter = mockApi({
    'GET /department-tools': () => [vendorTool(false)],
    'GET /modules/vendors/contracts': () => page(contracts),
  });
  renderPage(vendorRoutes('renewals', <RenewalsPage />), superAdmin, '/tools/vendors/renewals');
  return adapter;
}

describe('RenewalsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('groups renewals by the reminder lead times, soonest first, with notice deadlines', async () => {
    const adapter = renderRenewals();

    const week = await screen.findByRole('list', { name: 'Within 7 days' });
    expect(within(week).getAllByRole('listitem').map((li) => within(li).getAllByRole('button')[0]!.textContent)).toEqual(['Jira Cloud Premium', 'Support']);
    expect(within(week).getByText('Ends in 6 days')).toBeInTheDocument();
    expect(within(week).getByText('Notice deadline passed')).toBeInTheDocument();
    const month = screen.getByRole('list', { name: 'In 8 to 30 days' });
    expect(within(month).getByText(/Give notice within 15 days/)).toBeInTheDocument();
    expect(adapter.mock.calls.at(-1)?.[0].params).toMatchObject({ renewalWithinDays: 30 });
  });

  it('looks further ahead on request', async () => {
    const adapter = renderRenewals();
    await screen.findByRole('list', { name: 'Within 7 days' });

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Renewing within' }), '90');

    await waitFor(() => expect(adapter.mock.calls.at(-1)?.[0].params).toMatchObject({ renewalWithinDays: 90 }));
  });

  it('says when nothing is due', async () => {
    renderRenewals([]);

    expect(await screen.findByText('Nothing renews or ends in the next 30 days')).toBeInTheDocument();
  });
});
