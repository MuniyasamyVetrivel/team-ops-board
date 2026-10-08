import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { DocumentDetail, DocumentItem, DocumentVersion } from './api';
import DocumentsPage from './DocumentsPage';

const originalAdapter = api.defaults.adapter;

const lakshmi = { id: 6, fullName: 'Lakshmi Priya', email: 'lakshmi.priya@teamops.local', jobTitle: null, status: 'ACTIVE' as const };

function version(no: number, note: string | null): DocumentVersion {
  return { versionNo: no, fileName: `handbook-v${no}.pdf`, contentType: 'application/pdf', sizeBytes: 2048 * no, changeNote: note, uploadedBy: lakshmi, uploadedAt: '2026-10-05T05:00:00Z' };
}

const item: DocumentItem = {
  id: 4,
  name: 'Employee handbook',
  description: 'Company-wide policies.',
  department: null,
  project: null,
  uploadedBy: lakshmi,
  current: version(2, 'Remote-work section'),
  versionCount: 2,
  createdAt: '2026-09-01T05:00:00Z',
  updatedAt: '2026-10-05T05:00:00Z',
  canEdit: false,
};

const detail: DocumentDetail = {
  id: 4,
  name: 'Employee handbook',
  description: 'Company-wide policies.',
  department: null,
  project: null,
  uploadedBy: lakshmi,
  versions: [version(2, 'Remote-work section'), version(1, null)],
  createdAt: '2026-09-01T05:00:00Z',
  updatedAt: '2026-10-05T05:00:00Z',
  version: 1,
  canEdit: false,
};

const page = { content: [item], page: 0, size: 25, totalElements: 1, totalPages: 1 };

describe('DocumentsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('lists documents with their current version and opens the version history', async () => {
    mockApi({ 'GET /documents': () => page, 'GET /departments': () => [], 'GET /documents/4': () => detail });

    renderPage(<DocumentsPage />, webEmployee);

    const row = (await screen.findByText('Employee handbook')).closest('tr')!;
    expect(within(row).getByText('Company-wide')).toBeInTheDocument();
    expect(within(row).getByText('handbook-v2.pdf')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Upload' })).not.toBeInTheDocument();

    await userEvent.click(row);
    const drawer = await screen.findByRole('dialog');
    expect(await within(drawer).findByText('Version 2')).toBeInTheDocument();
    expect(within(drawer).getByText('Current')).toBeInTheDocument();
    expect(within(drawer).getByRole('button', { name: 'Download version 1' })).toBeInTheDocument();
    expect(within(drawer).queryByText('Upload a new version')).not.toBeInTheDocument();
  });

  it('requires a file before uploading', async () => {
    mockApi({ 'GET /documents': () => page, 'GET /departments': () => [], 'GET /projects/options': () => [] });

    renderPage(<DocumentsPage />, makeUser(['DEPARTMENT_MANAGER'], ['DOCUMENT_VIEW', 'DOCUMENT_EDIT']));
    await userEvent.click(await screen.findByRole('button', { name: 'Upload' }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Upload' }));

    expect(await within(dialog).findByText('Choose a file')).toBeInTheDocument();
  });
});
