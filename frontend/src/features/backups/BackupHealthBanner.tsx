import { ArrowRight, TriangleAlert } from 'lucide-react';
import { Link, useLocation } from 'react-router';

import { isSuperAdmin } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';

import { useBackupStatus } from './api';
import { bannerMessage } from './backup-rules';

const BACKUPS_PATH = '/admin/backups';

/**
 * Red banner across the top of every page for Super Admins while the backup health check fails (last successful
 * backup older than 26 hours, last run failed, a copy failing, or backups not configured). Checked every five
 * minutes; nobody else sees it, and it stays out of the way when the status cannot be loaded.
 */
export function BackupHealthBanner() {
  const { user } = useAuth();
  const superAdmin = isSuperAdmin(user);
  const status = useBackupStatus({ enabled: superAdmin, idleInterval: 5 * 60_000 });
  const location = useLocation();

  if (!superAdmin || !status.data || status.data.healthy) return null;
  return (
    <div role="alert" className="flex flex-wrap items-center gap-x-3 gap-y-1 bg-status-danger px-4 py-2 text-sm text-white sm:px-6 lg:px-8">
      <TriangleAlert className="size-4 shrink-0" aria-hidden />
      <p className="min-w-0 flex-1">
        <span className="font-semibold">Backups need attention:</span> {bannerMessage(status.data.problems)}
      </p>
      {location.pathname !== BACKUPS_PATH && (
        <Link to={BACKUPS_PATH} className="inline-flex shrink-0 items-center gap-1 font-medium underline underline-offset-2 hover:no-underline focus-visible:ring-2 focus-visible:ring-white focus-visible:outline-none">
          Open Backups
          <ArrowRight className="size-3.5" aria-hidden />
        </Link>
      )}
    </div>
  );
}
