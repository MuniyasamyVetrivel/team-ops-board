import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, superAdmin } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { ApprovalDetail, ApprovalListItem, ApprovalType } from './api';
import ApprovalsPage from './ApprovalsPage';

const originalAdapter = api.defaults.adapter;

const karthik = { id: 4, fullName: 'Karthik Raj', email: 'karthik.raj@teamops.local', jobTitle: null, status: 'ACTIVE' as const };
const sanjay = { id: 3, fullName: 'Sanjay Varma', email: 'sanjay.varma@teamops.local', jobTitle: null, status: 'ACTIVE' as const };
const department = { id: 5, name: 'Web Development', code: 'WEBDEV' };

const types: ApprovalType[] = [
  { id: 3, code: 'PURCHASE', name: 'Purchase', description: 'Equipment and other purchases', requiresAmount: true, active: true, steps: [
    { stepOrder: 1, approverKind: 'DEPARTMENT_MANAGER', role: null, user: null },
    { stepOrder: 2, approverKind: 'ROLE', role: { code: 'SUPER_ADMIN', name: 'Super Admin' }, user: null },
  ], version: 0 },
  { id: 7, code: 'OTHER', name: 'Other', description: null, requiresAmount: false, active: true, steps: [{ stepOrder: 1, approverKind: 'DEPARTMENT_MANAGER', role: null, user: null }], version: 0 },
];

const item: ApprovalListItem = {
  id: 9,
  code: 'APR-000009',
  title: 'External monitor',
  type: { id: 3, code: 'PURCHASE', name: 'Purchase', requiresAmount: true },
  status: 'PENDING',
  requester: karthik,
  department,
  amount: 14500,
  currency: 'INR',
  dueDate: '2026-10-13',
  currentStep: 1,
  stepCount: 2,
  waitingOn: 'Sanjay Varma',
  awaitingMe: true,
  createdAt: '2026-10-08T05:00:00Z',
  decidedAt: null,
};

const detail: ApprovalDetail = {
  id: 9,
  code: 'APR-000009',
  title: 'External monitor',
  description: 'For design reviews.',
  type: item.type,
  status: 'PENDING',
  requester: karthik,
  department,
  amount: 14500,
  currency: 'INR',
  dueDate: '2026-10-13',
  currentStep: 1,
  steps: [
    { stepOrder: 1, approverKind: 'DEPARTMENT_MANAGER', approver: sanjay, role: null, status: 'PENDING', decidedBy: null, comment: null, decidedAt: null },
    { stepOrder: 2, approverKind: 'ROLE', approver: null, role: { code: 'SUPER_ADMIN', name: 'Super Admin' }, status: 'WAITING', decidedBy: null, comment: null, decidedAt: null },
  ],
  createdAt: '2026-10-08T05:00:00Z',
  decidedAt: null,
  version: 0,
  permissions: { canDecide: true, canCancel: false },
};

const page = (content: ApprovalListItem[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });

const manager = makeUser(['DEPARTMENT_MANAGER'], ['APPROVAL_VIEW', 'APPROVAL_DECIDE'], { id: 3, fullName: 'Sanjay Varma' });

describe('ApprovalsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('opens on requests waiting for the approver, with a count and who it waits on', async () => {
    mockApi({ 'GET /approvals': () => page([item]), 'GET /approvals/types': () => types });

    renderPage(<ApprovalsPage />, manager);

    const tab = await screen.findByRole('tab', { name: /To decide/ });
    expect(tab).toHaveAttribute('aria-selected', 'true');
    await waitFor(() => expect(tab).toHaveTextContent('1'));
    const row = (await screen.findByText('External monitor')).closest('tr')!;
    expect(within(row).getByText('Waiting for you')).toBeInTheDocument();
    expect(within(row).getByText(/step 1 of 2/)).toBeInTheDocument();
    expect(within(row).getByText('₹14,500.00')).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Workflows' })).not.toBeInTheDocument();
  });

  it('requires a reason before rejecting, then sends the decision', async () => {
    const adapter = mockApi({
      'GET /approvals': () => page([item]),
      'GET /approvals/types': () => types,
      'GET /approvals/9': () => detail,
      'POST /approvals/9/decision': () => ({ ...detail, status: 'REJECTED', permissions: { canDecide: false, canCancel: false } }),
    });

    renderPage(<ApprovalsPage />, manager, '/approvals?approval=9');

    const drawer = await screen.findByRole('dialog');
    expect(await within(drawer).findByText('Any Super Admin')).toBeInTheDocument();
    await userEvent.click(within(drawer).getByRole('button', { name: 'Reject' }));
    expect(within(drawer).getByText('Give a reason when rejecting a request')).toBeInTheDocument();
    expect(adapter.mock.calls.some(([c]) => c.method === 'post')).toBe(false);

    await userEvent.type(within(drawer).getByLabelText('Comment'), 'Not this quarter');
    await userEvent.click(within(drawer).getByRole('button', { name: 'Reject' }));
    await waitFor(() => {
      const post = adapter.mock.calls.find(([c]) => c.method === 'post');
      expect(JSON.parse(post![0].data as string)).toEqual({ decision: 'REJECT', comment: 'Not this quarter' });
    });
  });

  it('asks for an amount on purchase requests and shows the approval path', async () => {
    const adapter = mockApi({ 'GET /approvals': () => page([]), 'GET /approvals/types': () => types, 'POST /approvals': () => ({ ...detail, id: 10 }), 'GET /approvals/10': () => detail });

    renderPage(<ApprovalsPage />, makeUser(['EMPLOYEE'], ['APPROVAL_VIEW']));
    await userEvent.click(await screen.findByRole('button', { name: 'New request' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.selectOptions(within(dialog).getByLabelText(/Request type/), '3');
    expect(within(dialog).getByLabelText('Approval steps')).toHaveTextContent('Department manager');
    expect(within(dialog).getByLabelText('Approval steps')).toHaveTextContent('Any Super Admin');
    await userEvent.type(within(dialog).getByLabelText(/Title/), 'Standing desk');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Submit request' }));
    expect(await within(dialog).findByText('Purchase requests need an amount')).toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText(/Amount/), '8200');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Submit request' }));
    await waitFor(() => {
      const post = adapter.mock.calls.find(([c]) => c.method === 'post');
      expect(JSON.parse(post![0].data as string)).toMatchObject({ typeId: 3, title: 'Standing desk', amount: 8200, currency: 'INR' });
    });
  });

  it('lets configurers see the workflows', async () => {
    mockApi({ 'GET /approvals': () => page([]), 'GET /approvals/types': () => types });

    renderPage(<ApprovalsPage />, superAdmin);
    await userEvent.click(await screen.findByRole('tab', { name: 'Workflows' }));

    expect(await screen.findByRole('button', { name: 'Edit Purchase workflow' })).toBeInTheDocument();
  });
});
