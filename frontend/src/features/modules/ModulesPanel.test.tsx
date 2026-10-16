import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import SettingsPage from '@/features/admin/settings/SettingsPage';
import type { DepartmentListItem } from '@/features/departments/api';
import { api } from '@/lib/api/client';
import { makeUser, superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { ModuleSettings } from './api';

const originalAdapter = api.defaults.adapter;

function department(id: number, name: string, code: string, status: DepartmentListItem['status'] = 'ACTIVE'): DepartmentListItem {
  return { id, name, code, description: null, status, manager: null, memberCount: 3, secondaryMemberCount: 0 };
}

const departments = [
  department(1, 'Human Resources', 'HR'),
  department(2, 'Talent Acquisition', 'TALENT_ACQUISITION'),
  department(3, 'Web Development', 'WEB'),
  department(4, 'Old Team', 'OLD', 'INACTIVE'),
];

const bench: ModuleSettings = {
  key: 'bench',
  name: 'Bench Management',
  icon: 'Users',
  description: 'Consultants, allocations and the bench',
  viewPermission: 'BENCH_VIEW',
  editPermission: 'BENCH_EDIT',
  departments: [{ id: 1, name: 'Human Resources', code: 'HR' }],
};

type Handler = (config: InternalAxiosRequestConfig) => unknown;
function routes(modules: ModuleSettings[], extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /admin/settings': () => [],
    'GET /roles': () => [],
    'GET /permissions': () => [],
    'GET /approvals/types': () => [],
    'GET /team': () => ({
      content: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    }),
    'GET /admin/modules': () => modules,
    'GET /departments': () => departments,
    ...extra,
  };
}

describe('Settings → Modules', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows an empty state until department tools exist', async () => {
    mockApi(routes([]));
    renderPage(<SettingsPage />, superAdmin, '/admin/settings?tab=modules');

    expect(await screen.findByText('No department tools yet')).toBeInTheDocument();
  });

  it('switches a module on for a department and reports it in words', async () => {
    const user = userEvent.setup();
    const adapter = mockApi(
      routes([bench], {
        'PUT /admin/modules/bench/departments/2': (config) => {
          expect(JSON.parse(config.data as string)).toEqual({ enabled: true });
          return {
            ...bench,
            departments: [...bench.departments, { id: 2, name: 'Talent Acquisition', code: 'TALENT_ACQUISITION' }],
          };
        },
      }),
    );
    renderPage(<SettingsPage />, superAdmin, '/admin/settings?tab=modules');

    const list = await screen.findByRole('list', {
      name: 'Bench Management by department',
    });
    expect(screen.getByText('On for 1 of 3')).toBeInTheDocument();
    expect(
      within(list).getByRole('checkbox', {
        name: 'Bench Management for Human Resources',
      }),
    ).toBeChecked();
    // Inactive departments that are off are listed nowhere; the switch only offers active ones.
    expect(within(list).queryByText('Old Team')).not.toBeInTheDocument();
    expect(screen.getByText('BENCH_VIEW')).toBeInTheDocument();

    const talent = within(list).getByRole('checkbox', {
      name: 'Bench Management for Talent Acquisition',
    });
    expect(talent).not.toBeChecked();
    await user.click(talent);

    expect(await screen.findByText('On for 2 of 3')).toBeInTheDocument();
    expect(talent).toBeChecked();
    expect(adapter).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'put',
        url: '/admin/modules/bench/departments/2',
      }),
    );
  });

  it('keeps an inactive department that is still on, so it can be switched off', async () => {
    mockApi(routes([{ ...bench, departments: [{ id: 4, name: 'Old Team', code: 'OLD' }] }]));
    renderPage(<SettingsPage />, superAdmin, '/admin/settings?tab=modules');

    const list = await screen.findByRole('list', {
      name: 'Bench Management by department',
    });
    expect(
      within(list).getByRole('checkbox', {
        name: 'Bench Management for Old Team',
      }),
    ).toBeEnabled();
    expect(within(list).getByText('Inactive')).toBeInTheDocument();
  });

  it('is hidden from people without MODULE_MANAGE', async () => {
    mockApi(routes([bench]));
    renderPage(<SettingsPage />, makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'SETTINGS_MANAGE']), '/admin/settings?tab=modules');

    expect(await screen.findByRole('tab', { name: 'General' })).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Modules' })).not.toBeInTheDocument();
    expect(screen.queryByText('Bench Management')).not.toBeInTheDocument();
  });
});
