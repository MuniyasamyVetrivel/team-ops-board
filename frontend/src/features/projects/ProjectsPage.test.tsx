import { screen, within } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { ProjectDetail, ProjectListItem } from './api';
import ProjectDetailPage from './ProjectDetailPage';
import ProjectsPage from './ProjectsPage';

const originalAdapter = api.defaults.adapter;

const sanjay = { id: 3, fullName: 'Sanjay Varma', email: 'sanjay.varma@teamops.local', jobTitle: 'Web Lead', status: 'ACTIVE' as const };
const webDev = { id: 5, name: 'Web Development', code: 'WEBDEV' };

function item(overrides: Partial<ProjectListItem>): ProjectListItem {
  return {
    id: 1,
    code: 'PRJ-0001',
    name: 'Website Revamp',
    status: 'ACTIVE',
    department: webDev,
    owner: sanjay,
    startDate: '2026-09-01',
    endDate: '2026-12-15',
    progress: 50,
    progressOverridden: false,
    tasks: { total: 8, completed: 4, open: 4, overdue: 2 },
    milestones: { total: 3, completed: 1, overdue: 0 },
    openRisks: 1,
    updatedAt: '2026-10-08T05:00:00Z',
    ...overrides,
  };
}

function detail(overrides: Partial<ProjectDetail> = {}): ProjectDetail {
  return {
    id: 1,
    code: 'PRJ-0001',
    name: 'Website Revamp',
    description: 'New marketing site.',
    status: 'ACTIVE',
    department: webDev,
    owner: sanjay,
    startDate: '2026-09-01',
    endDate: '2026-12-15',
    progress: 50,
    progressOverride: null,
    tasks: { total: 8, completed: 4, open: 4, overdue: 2 },
    members: [sanjay],
    milestones: [
      { id: 1, name: 'Design sign-off', description: null, dueDate: '2026-10-01', status: 'IN_PROGRESS', completedAt: null, position: 1, overdue: true },
      { id: 2, name: 'Launch', description: null, dueDate: '2026-12-15', status: 'PLANNED', completedAt: null, position: 2, overdue: false },
    ],
    risks: [{ id: 1, title: 'Vendor delay', description: null, probability: 'HIGH', impact: 'HIGH', severity: 9, mitigation: 'Second vendor', owner: sanjay, status: 'OPEN' }],
    dependencies: [],
    version: 2,
    createdAt: '2026-09-01T05:00:00Z',
    updatedAt: '2026-10-08T05:00:00Z',
    permissions: { canEdit: false },
    ...overrides,
  };
}

const page = (content: ProjectListItem[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });

describe('ProjectsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows progress, milestones and warning signals with labels', async () => {
    mockApi({ 'GET /projects': () => page([item({}), item({ id: 2, code: 'PRJ-0002', name: 'ISO readiness', progress: null, tasks: { total: 0, completed: 0, open: 0, overdue: 0 }, openRisks: 0 })]), 'GET /departments': () => [] });

    renderPage(<ProjectsPage />, superAdmin);

    const revamp = (await screen.findByText('Website Revamp')).closest('tr')!;
    expect(within(revamp).getByRole('progressbar', { name: 'Website Revamp progress' })).toHaveAttribute('aria-valuenow', '50');
    expect(within(revamp).getByText('1/3 milestones')).toBeInTheDocument();
    expect(within(revamp).getByText('2 overdue')).toBeInTheDocument();
    expect(within(revamp).getByText('1 open risk')).toBeInTheDocument();
    const iso = screen.getByText('ISO readiness').closest('tr')!;
    expect(within(iso).getByText('—')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New project' })).toBeInTheDocument();
  });

  it('hides project creation without PROJECT_EDIT', async () => {
    mockApi({ 'GET /projects': () => page([item({})]), 'GET /departments': () => [] });

    renderPage(<ProjectsPage />, webEmployee);

    await screen.findByText('Website Revamp');
    expect(screen.queryByRole('button', { name: 'New project' })).not.toBeInTheDocument();
  });
});

describe('ProjectDetailPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  function render(project: ProjectDetail) {
    mockApi({ 'GET /projects/1': () => project, 'GET /tasks': () => page([]), 'GET /team': () => page([]), 'GET /projects/options': () => [] });
    renderPage(
      <Routes>
        <Route path="/projects/:id" element={<ProjectDetailPage />} />
      </Routes>,
      superAdmin,
      '/projects/1',
    );
  }

  it('flags overdue milestones and shows risk severity in words', async () => {
    render(detail());

    expect(await screen.findByRole('heading', { name: 'Website Revamp' })).toBeInTheDocument();
    const signOff = screen.getByText('Design sign-off').closest('li')!;
    expect(within(signOff).getByText('Overdue')).toBeInTheDocument();
    expect(within(screen.getByText('Launch').closest('li')!).queryByText('Overdue')).not.toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Project progress' })).toHaveAttribute('aria-valuenow', '50');
    expect(screen.getByText('Progress (from tasks)')).toBeInTheDocument();
  });

  it('offers editing only when the server allows it', async () => {
    render(detail());
    await screen.findByRole('heading', { name: 'Website Revamp' });
    expect(screen.queryByRole('button', { name: 'Edit project' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add milestone' })).not.toBeInTheDocument();
  });

  it('shows the editing controls to editors', async () => {
    render(detail({ permissions: { canEdit: true }, progressOverride: 80, progress: 80 }));
    expect(await screen.findByRole('button', { name: 'Edit project' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add milestone' })).toBeInTheDocument();
    expect(screen.getByText('Progress (set manually)')).toBeInTheDocument();
  });
});
