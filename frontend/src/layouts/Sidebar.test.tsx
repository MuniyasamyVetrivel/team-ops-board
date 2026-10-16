import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { DepartmentTool } from '@/features/modules/api';
import { api } from '@/lib/api/client';
import { makeUser, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import { Sidebar } from './Sidebar';

const originalAdapter = api.defaults.adapter;

const recruiter = makeUser(['EMPLOYEE'], [...webEmployee.permissions]);

describe('Sidebar department tools', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists the tools switched on and permitted, linking to each', async () => {
    const tools: DepartmentTool[] = [
      {
        key: 'bench',
        name: 'Bench Management',
        icon: 'Users',
        description: 'Consultants',
        canEdit: false,
      },
    ];
    mockApi({ 'GET /department-tools': () => tools });
    renderPage(<Sidebar mobileOpen={false} onNavigate={vi.fn()} />, recruiter);

    const link = await screen.findByRole('link', { name: 'Bench Management' });
    expect(link).toHaveAttribute('href', '/tools/bench');
    expect(screen.getByText('Department tools')).toBeInTheDocument();
  });

  it('hides the section when no tool is available', async () => {
    const adapter = mockApi({ 'GET /department-tools': () => [] });
    renderPage(<Sidebar mobileOpen={false} onNavigate={vi.fn()} />, recruiter);

    expect(await screen.findByRole('link', { name: 'My Tasks' })).toBeInTheDocument();
    await vi.waitFor(() => expect(adapter).toHaveBeenCalled());
    expect(screen.queryByText('Department tools')).not.toBeInTheDocument();
  });
});
