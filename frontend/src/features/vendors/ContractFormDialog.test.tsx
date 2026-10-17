import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { api } from '@/lib/api/client';
import { page } from '@/test/celebration-fixtures';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';
import { contract, vendor, vendorEditor } from '@/test/vendor-fixtures';

import type { SaveContractInput } from './api';
import { ContractFormDialog } from './ContractFormDialog';

const originalAdapter = api.defaults.adapter;

function renderForm(props: Partial<Parameters<typeof ContractFormDialog>[0]> = {}, user = superAdmin) {
  const saved: SaveContractInput[] = [];
  const onSaved = vi.fn();
  mockApi({
    'GET /modules/vendors/vendors': () => page([vendor(), vendor({ id: 6, name: 'Shady Supplies', status: 'BLACKLISTED' })]),
    'POST /modules/vendors/contracts': (config) => {
      saved.push(JSON.parse(config.data as string) as SaveContractInput);
      return contract({ id: 40 });
    },
    'PUT /modules/vendors/contracts/21': (config) => {
      saved.push(JSON.parse(config.data as string) as SaveContractInput);
      return contract({ value: null, valueHidden: true });
    },
  });
  renderPage(<ContractFormDialog open onOpenChange={() => {}} onSaved={onSaved} {...props} />, user);
  return { saved, onSaved };
}

describe('ContractFormDialog', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('records renewal terms and shows when notice is due', async () => {
    const user = userEvent.setup();
    const { saved, onSaved } = renderForm({ vendorId: 5 });
    await screen.findByRole('option', { name: 'Atlassian' });
    expect(screen.queryByRole('option', { name: 'Shady Supplies' })).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/Title/), 'Confluence');
    await user.type(screen.getByLabelText(/Start date/), '2026-03-01');
    await user.type(screen.getByLabelText(/End date/), '2027-02-28');
    await user.type(screen.getByLabelText(/^Value/), '12,000.50');
    await user.clear(screen.getByLabelText(/Currency/));
    await user.type(screen.getByLabelText(/Currency/), 'aud');
    await user.type(screen.getByLabelText(/Renewal date/), '2027-03-01');
    await user.type(screen.getByLabelText(/Notice period/), '30');
    expect(screen.getByText(/Give notice by 30 Jan 2027/)).toBeInTheDocument();
    await user.click(screen.getByLabelText(/Renews automatically/));

    await user.click(screen.getByRole('button', { name: 'Add contract' }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(40));
    expect(saved[0]).toEqual({
      version: undefined,
      vendorId: 5,
      title: 'Confluence',
      startDate: '2026-03-01',
      endDate: '2027-02-28',
      value: 12000.5,
      currency: 'AUD',
      renewalDate: '2027-03-01',
      autoRenew: true,
      noticePeriodDays: 30,
      notes: null,
    });
  });

  it('checks the dates before saving', async () => {
    const user = userEvent.setup();
    const { saved } = renderForm({ vendorId: 5 });
    await screen.findByRole('option', { name: 'Atlassian' });

    await user.type(screen.getByLabelText(/Title/), 'Confluence');
    await user.type(screen.getByLabelText(/Start date/), '2026-03-01');
    await user.type(screen.getByLabelText(/Renewal date/), '2026-02-01');
    await user.click(screen.getByRole('button', { name: 'Add contract' }));

    expect(await screen.findByText('The renewal date must be on or after the start date')).toBeInTheDocument();
    expect(saved).toHaveLength(0);
  });

  it('never shows or clears a value hidden from the editor', async () => {
    const user = userEvent.setup();
    const { saved } = renderForm({ contract: contract({ value: null, valueHidden: true }) }, vendorEditor);
    await screen.findByRole('option', { name: 'Atlassian' });

    expect(screen.getByText(/Hidden\. Only people who can see finance can see or change it/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Value/)).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0]).toMatchObject({ value: null, version: 1, title: 'Jira Cloud Premium' });
  });
});
