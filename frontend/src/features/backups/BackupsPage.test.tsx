import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { AxiosProgressEvent, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, superAdmin, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { BackupRun, BackupStatus } from './api';
import BackupsPage from './BackupsPage';

const originalAdapter = api.defaults.adapter;

const ok: BackupRun = {
  id: 7,
  trigger: 'SCHEDULED',
  status: 'SUCCESS',
  startedAt: '2026-10-15T20:30:00Z',
  finishedAt: '2026-10-15T20:33:20Z',
  fileName: 'team-ops-backup-20261015T203000Z.tobk',
  sizeBytes: 48 * 1024 * 1024,
  checksum: 'ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12',
  target: 'LOCAL D:\\team-ops-backups',
  uploadFiles: 312,
  errorMessage: null,
  requestedById: null,
  removedAt: null,
  secondary: { target: 'S3 team-ops-dr/backups/', status: 'SUCCESS', errorMessage: null, removedAt: null },
};

const copyFailed: BackupRun = {
  ...ok,
  id: 8,
  trigger: 'MANUAL',
  startedAt: '2026-10-16T05:00:00Z',
  finishedAt: '2026-10-16T05:01:00Z',
  fileName: 'team-ops-backup-20261016T050000Z.tobk',
  secondary: { target: 'S3 team-ops-dr/backups/', status: 'FAILED', errorMessage: 'Upload to S3 failed: Access Denied', removedAt: null },
};

const healthy: BackupStatus = {
  healthy: true,
  problems: [],
  copies: [
    { copy: 'PRIMARY', target: 'LOCAL D:\\team-ops-backups', healthy: true, problems: [], lastSuccessAt: ok.finishedAt, lastStatus: 'SUCCESS', lastError: null },
    { copy: 'SECONDARY', target: 'S3 team-ops-dr/backups/', healthy: true, problems: [], lastSuccessAt: ok.finishedAt, lastStatus: 'SUCCESS', lastError: null },
  ],
  lastSuccess: ok,
  lastRun: ok,
  running: false,
  scheduleEnabled: true,
  schedule: '0 0 2 * * *',
  timeZone: 'Asia/Kolkata',
  nextRunAt: '2026-10-16T20:30:00Z',
  target: 'LOCAL D:\\team-ops-backups',
  secondaryTarget: 'S3 team-ops-dr/backups/',
  keepDaily: 30,
  keepMonthly: 12,
  maxAgeHours: 26,
};

const secondFailing: BackupStatus = {
  ...healthy,
  healthy: false,
  problems: [{ code: 'SECONDARY_LAST_FAILED', message: 'The second copy of the last backup failed: Upload to S3 failed: Access Denied' }],
  copies: [
    healthy.copies[0],
    { ...healthy.copies[1], healthy: false, problems: [{ code: 'SECONDARY_LAST_FAILED', message: 'The second copy of the last backup failed: Upload to S3 failed: Access Denied' }], lastStatus: 'FAILED', lastError: 'Upload to S3 failed: Access Denied' },
  ],
  lastRun: copyFailed,
};

const page = (content: BackupRun[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 });

type Handler = (config: InternalAxiosRequestConfig) => unknown;
function routes(status: BackupStatus = healthy, extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /admin/backups/status': () => status,
    'GET /admin/backups': () => page([copyFailed, ok]),
    ...extra,
  };
}

describe('BackupsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
    vi.restoreAllMocks();
  });

  it('shows the last successful backup, its size, the next run and both copies', async () => {
    mockApi(routes());
    renderPage(<BackupsPage />, superAdmin);

    const summary = await screen.findByRole('region', { name: 'Backup summary' });
    expect(within(summary).getByText('Last successful backup')).toBeInTheDocument();
    expect(within(summary).getByText('48.0 MB')).toBeInTheDocument();
    expect(within(summary).getByText('Database and 312 uploaded files, encrypted')).toBeInTheDocument();
    expect(within(summary).getByText('Next scheduled run')).toBeInTheDocument();
    expect(within(summary).getByText('2 of 2')).toBeInTheDocument();
    expect(screen.getByText('LOCAL D:\\team-ops-backups')).toBeInTheDocument();
    expect(screen.getByText('S3 team-ops-dr/backups/')).toBeInTheDocument();
    expect(screen.getAllByText('Healthy')).toHaveLength(2);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('lists the history with the primary and second copy of every run', async () => {
    mockApi(routes(secondFailing));
    renderPage(<BackupsPage />, superAdmin);

    const table = await screen.findByRole('table', { name: 'Backup history' });
    const [, latest, older] = within(table).getAllByRole('row');
    expect(latest).toHaveTextContent('Run now');
    const cells = within(latest).getAllByRole('cell');
    expect(cells[2]).toHaveTextContent('Stored');
    expect(cells[3]).toHaveTextContent('Failed');
    expect(cells[3]).toHaveTextContent('Upload to S3 failed: Access Denied');
    expect(within(older).getAllByText('Stored')).toHaveLength(2);
    expect(within(older).getByText('3 min 20 s')).toBeInTheDocument();
    expect(within(older).getByText('team-ops-backup-20261015T203000Z.tobk')).toBeInTheDocument();

    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Backups need attention');
    expect(alert).toHaveTextContent('The second copy of the last backup failed');
    expect(screen.getByText('Needs attention')).toBeInTheDocument();
  });

  it('starts a backup now and shows it running', async () => {
    const user = userEvent.setup();
    let status = healthy;
    const runs: unknown[] = [];
    mockApi(
      routes(healthy, {
        'GET /admin/backups/status': () => status,
        'POST /admin/backups/run': () => {
          runs.push(1);
          status = { ...healthy, running: true };
          return { ...ok, id: 9, status: 'RUNNING', finishedAt: null };
        },
      }),
    );
    renderPage(<BackupsPage />, superAdmin);

    await user.click(await screen.findByRole('button', { name: 'Run backup now' }));
    expect(runs).toHaveLength(1);
    expect(await screen.findByRole('button', { name: 'Backup running…' })).toBeDisabled();
  });

  it('exports all data with progress, then saves the ZIP', async () => {
    const user = userEvent.setup();
    const base = mockApi(routes());
    let finish: (() => void) | undefined;
    api.defaults.adapter = (config: InternalAxiosRequestConfig) => {
      if (config.url !== '/admin/data-export') return base(config);
      config.onDownloadProgress?.({ loaded: 3.5 * 1024 * 1024 } as AxiosProgressEvent);
      return new Promise<AxiosResponse>((resolve) => {
        finish = () =>
          resolve({
            data: new Blob(['PK zip']),
            status: 200,
            statusText: 'OK',
            headers: { 'content-disposition': 'attachment; filename="team-ops-export-20261016-101500.zip"' },
            config,
          });
      });
    };
    URL.createObjectURL = vi.fn(() => 'blob:export');
    URL.revokeObjectURL = vi.fn();
    renderPage(<BackupsPage />, superAdmin);

    await user.click(await screen.findByRole('button', { name: 'Export all data' }));
    expect(await screen.findByText('3.5 MB downloaded')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Export progress' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Exporting…' })).toBeDisabled();

    finish?.();
    expect(await screen.findByText(/team-ops-export-20261016-101500\.zip/)).toBeInTheDocument();
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Export all data' })).toBeEnabled();
  });

  it('shows a viewer granted BACKUP_VIEW the status but neither button', async () => {
    mockApi(routes());
    renderPage(<BackupsPage />, makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'BACKUP_VIEW']));

    await screen.findByRole('table', { name: 'Backup history' });
    expect(screen.queryByRole('button', { name: 'Run backup now' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Export all data' })).not.toBeInTheDocument();
  });

  it('offers Export all data to a DATA_EXPORT holder but Run backup now only to the Super Admin', async () => {
    mockApi(routes());
    renderPage(<BackupsPage />, makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'BACKUP_VIEW', 'DATA_EXPORT']));

    expect(await screen.findByRole('button', { name: 'Export all data' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Run backup now' })).not.toBeInTheDocument();
  });

  it('says when there are no backups yet and when the status cannot be loaded', async () => {
    mockApi(
      routes(healthy, {
        'GET /admin/backups/status': () => {
          throw new Error('boom');
        },
        'GET /admin/backups': () => page([]),
      }),
    );
    renderPage(<BackupsPage />, superAdmin);

    expect(await screen.findByText('No backups yet')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText("Couldn't load the backup status")).toBeInTheDocument());
  });
});
