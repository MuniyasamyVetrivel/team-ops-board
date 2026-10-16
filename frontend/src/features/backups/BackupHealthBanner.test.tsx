import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { BackupStatus } from './api';
import { BackupHealthBanner } from './BackupHealthBanner';

const originalAdapter = api.defaults.adapter;

const status = (healthy: boolean, problems: BackupStatus['problems'] = []): Partial<BackupStatus> => ({ healthy, problems, copies: [], running: false });

describe('BackupHealthBanner', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('warns the Super Admin across the app while the backup health check fails', async () => {
    mockApi({
      'GET /admin/backups/status': () =>
        status(false, [
          { code: 'STALE', message: 'The last successful backup is older than 26 hours' },
          { code: 'SECONDARY_LAST_FAILED', message: 'The second copy of the last backup failed' },
        ]),
    });
    renderPage(<BackupHealthBanner />, superAdmin, '/tasks');

    const banner = await screen.findByRole('alert');
    expect(banner).toHaveTextContent('Backups need attention: The last successful backup is older than 26 hours (+1 more)');
    expect(screen.getByRole('link', { name: 'Open Backups' })).toHaveAttribute('href', '/admin/backups');
  });

  it('stays hidden while backups are healthy', async () => {
    const adapter = mockApi({ 'GET /admin/backups/status': () => status(true) });
    renderPage(<BackupHealthBanner />, superAdmin);

    await vi.waitFor(() => expect(adapter).toHaveBeenCalled());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('never shows, nor asks, for anyone but the Super Admin', () => {
    const adapter = mockApi({ 'GET /admin/backups/status': () => status(false, [{ code: 'LAST_FAILED', message: 'failed' }]) });
    renderPage(<BackupHealthBanner />, makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'BACKUP_VIEW']));

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(adapter).not.toHaveBeenCalled();
  });
});
