import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { celebrationEditor, employee, page } from '@/test/celebration-fixtures';
import { webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import EmployeesPage from './EmployeesPage';

const originalAdapter = api.defaults.adapter;

const context = { today: '2026-10-12', currentPeriod: { month: 10, year: 2026, label: 'October 2026' }, years: [2026], owners: [], behindThresholdPct: 60 };

function routes(list = page([employee()])) {
  return {
    'GET /celebrations/employees': () => list,
    'GET /celebrations/employees/40': () => employee(),
    'GET /departments': () => [],
    'GET /team': () => page([]),
    'GET /marketing/context': () => context,
    'GET /marketing/imports': () => [{ type: 'employees', label: 'Employees', description: 'Employees for birthdays.', columns: [], maxRows: 5000 }],
  };
}

describe('EmployeesPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows birthdays without the year and anniversaries, with no editing for viewers', async () => {
    mockApi(routes());
    renderPage(<EmployeesPage />, webEmployee);

    const row = (await screen.findByText('Priya Menon')).closest('tr')!;
    expect(within(row).getByText(/14 October|October 14/)).toBeInTheDocument();
    expect(within(row).getByText(/5 years/)).toBeInTheDocument();
    expect(within(row).queryByText('1992')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /new employee/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /import csv/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /export csv/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Employee status' })).not.toBeInTheDocument();
  });

  it('opens an employee from the URL in a drawer', async () => {
    mockApi(routes());
    renderPage(<EmployeesPage />, webEmployee, '/celebrations/employees?employee=40');

    const drawer = await screen.findByRole('dialog');
    expect(await within(drawer).findByText('Rakesh')).toBeInTheDocument();
    expect(within(drawer).getByText('Next on 14 Oct 2026')).toBeInTheDocument();
    expect(within(drawer).queryByRole('button', { name: /edit employee/i })).not.toBeInTheDocument();
  });

  it('lets editors add, import and export, and validates the form like the server', async () => {
    const adapter = mockApi({
      ...routes(page([employee({ canEdit: true, dateOfBirth: '1992-10-14' })])),
      'POST /celebrations/employees': (config: InternalAxiosRequestConfig) => employee({ ...JSON.parse(config.data as string), id: 41, fullName: 'Rahul Verma', canEdit: true }),
      'GET /celebrations/employees/41': () => employee({ id: 41, fullName: 'Rahul Verma', canEdit: true }),
    });
    const user = userEvent.setup();
    renderPage(<EmployeesPage />, celebrationEditor);

    expect(await screen.findByRole('button', { name: /import csv/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /export csv/i })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Employee status' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /new employee/i }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Add employee' }));
    expect(await within(dialog).findByText('Employee ID is required')).toBeInTheDocument();
    expect(within(dialog).getByText('First name is required (or link an app user)')).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText(/Employee ID/), 'TRI-0107');
    await user.type(within(dialog).getByLabelText(/First name/), 'Rahul');
    await user.type(within(dialog).getByLabelText(/Last name/), 'Verma');
    await user.type(within(dialog).getByLabelText(/Date of birth/), '1996-11-22');
    await user.type(within(dialog).getByLabelText(/Designation/), 'QA Engineer');
    await user.type(within(dialog).getByLabelText(/Reporting manager/), 'Anjali Nair');
    await user.click(within(dialog).getByRole('button', { name: 'Add employee' }));

    await waitFor(() => expect(adapter.mock.calls.some(([config]) => config.method === 'post')).toBe(true));
    const post = adapter.mock.calls.find(([config]) => config.method === 'post')![0];
    expect(JSON.parse(post.data as string)).toMatchObject({ employeeCode: 'TRI-0107', firstName: 'Rahul', dateOfBirth: '1996-11-22', userId: null, status: 'ACTIVE' });
  });
});
