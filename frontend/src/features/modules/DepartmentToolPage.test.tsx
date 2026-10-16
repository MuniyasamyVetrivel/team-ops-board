import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { DepartmentTool } from './api';
import DepartmentToolPage from './DepartmentToolPage';

const originalAdapter = api.defaults.adapter;

const tools: DepartmentTool[] = [
  {
    key: 'bench',
    name: 'Bench Management',
    icon: 'Users',
    description: 'Consultants',
    canEdit: false,
  },
];

function renderAt(path: string) {
  return renderPage(
    <Routes>
      <Route path="/tools/:key" element={<DepartmentToolPage />} />
    </Routes>,
    webEmployee,
    path,
  );
}

describe('DepartmentToolPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('opens a tool the user may use', async () => {
    mockApi({ 'GET /department-tools': () => tools });
    renderAt('/tools/bench');

    expect(await screen.findByRole('heading', { name: 'Bench Management' })).toBeInTheDocument();
    expect(screen.getByText('View only')).toBeInTheDocument();
  });

  it('turns away a tool that is off or not permitted', async () => {
    mockApi({ 'GET /department-tools': () => tools });
    renderAt('/tools/vendors');

    expect(await screen.findByText("This tool isn't available to you")).toBeInTheDocument();
  });
});
