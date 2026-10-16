import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { api } from '@/lib/api/client';
import { allocation, consultant, engagement } from '@/test/bench-fixtures';
import { page } from '@/test/celebration-fixtures';
import { superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { SaveAllocationInput } from './api';
import { AllocationFormDialog } from './AllocationFormDialog';

const originalAdapter = api.defaults.adapter;

const over = [{ from: '2026-10-20', to: '2026-11-05', totalPercent: 130 }];

function renderForm(checkResult: typeof over) {
  const saved: SaveAllocationInput[] = [];
  const onSaved = vi.fn();
  const adapter = mockApi({
    'GET /modules/bench/consultants': () => page([consultant(), consultant({ id: 9, employmentStatus: 'LEFT', status: 'LEFT', employee: null })]),
    'GET /modules/bench/engagements': () => page([engagement(), engagement({ id: 6, name: 'Old rollout', status: 'ENDED' })]),
    'GET /modules/bench/allocations/check': () => ({ overAllocations: checkResult }),
    'POST /modules/bench/allocations': (config) => {
      saved.push(JSON.parse(config.data as string) as SaveAllocationInput);
      return { allocation: allocation({ id: 30 }), overAllocations: checkResult };
    },
  });
  renderPage(<AllocationFormDialog open onOpenChange={() => {}} consultantId={7} onSaved={onSaved} />, superAdmin);
  return { adapter, saved, onSaved };
}

async function fillIn() {
  const user = userEvent.setup();
  await screen.findByRole('option', { name: 'Acme Corp · S/4HANA regression' });
  await user.selectOptions(screen.getByLabelText(/Engagement/), '5');
  await user.type(screen.getByLabelText(/Role/), 'Automation lead');
  await user.type(screen.getByLabelText(/Start date/), '2026-10-01');
  await user.type(screen.getByLabelText(/End date/), '2026-11-05');
  await user.clear(screen.getByLabelText(/Allocation \(%\)/));
  await user.type(screen.getByLabelText(/Allocation \(%\)/), '80');
  return user;
}

describe('AllocationFormDialog', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('leaves consultants who left and ended engagements out of the pickers', async () => {
    renderForm([]);

    await screen.findByRole('option', { name: 'Acme Corp · S/4HANA regression' });
    expect(screen.queryByRole('option', { name: /Old rollout/ })).not.toBeInTheDocument();
    expect(screen.getAllByRole('option', { name: /Arjun Rao|Employee record deleted/ })).toHaveLength(1);
  });

  it('warns about going above 100% but still saves', async () => {
    const { adapter, saved, onSaved } = renderForm(over);
    const user = await fillIn();

    expect(await screen.findByText('Over 100% allocated')).toBeInTheDocument();
    expect(screen.getByText('130% in total')).toBeInTheDocument();
    const check = adapter.mock.calls.map(([config]) => config).find((c) => c.url === '/modules/bench/allocations/check');
    expect(check?.params).toMatchObject({ consultantId: 7, startDate: '2026-10-01', endDate: '2026-11-05', allocationPercent: 80 });

    await user.click(screen.getByRole('button', { name: 'Save anyway' }));

    await vi.waitFor(() => expect(onSaved).toHaveBeenCalledWith(30));
    expect(saved[0]).toMatchObject({ consultantId: 7, engagementId: 5, role: 'Automation lead', startDate: '2026-10-01', endDate: '2026-11-05', allocationPercent: 80, billable: true });
  });

  it('shows no warning while the consultant stays within 100%', async () => {
    const { adapter } = renderForm([]);
    await fillIn();

    await vi.waitFor(() => expect(adapter.mock.calls.some(([config]) => config.url === '/modules/bench/allocations/check')).toBe(true));
    expect(screen.queryByText('Over 100% allocated')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save allocation' })).toBeInTheDocument();
  });

  it('keeps the allocation inside the engagement before asking the server', async () => {
    const { saved } = renderForm([]);
    const user = await fillIn();
    await user.clear(screen.getByLabelText(/End date/));
    await user.type(screen.getByLabelText(/End date/), '2027-01-15');

    await user.click(screen.getByRole('button', { name: 'Save allocation' }));

    expect(await screen.findByText('Ends after the engagement')).toBeInTheDocument();
    expect(saved).toHaveLength(0);
  });
});
