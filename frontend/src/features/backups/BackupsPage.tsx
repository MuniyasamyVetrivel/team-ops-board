import { Archive, CalendarClock, CircleCheck, CircleX, CopyCheck, DatabaseBackup, HardDrive, LoaderCircle, Minus, Play, TriangleAlert } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { KpiGrid, KpiTile } from '@/components/common/KpiCard';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { Panel } from '@/components/common/Panel';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { errorMessage } from '@/lib/api/errors';
import { formatBytes, formatRelative, formatTimestamp } from '@/lib/format';

import { canExportAllData, canRunBackup } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';

import { useBackupHistory, useBackupStatus, useRunBackup, type BackupCopy, type BackupCopyStatus, type BackupRun, type BackupStatus } from './api';
import { copyBadge, formatDuration, primaryCopy, TRIGGER_LABELS } from './backup-rules';
import { ExportButton, ExportProgress } from './DataExport';
import { useDataExport } from './use-data-export';

const PAGE_SIZE = 20;

const COPY_ICONS = { running: LoaderCircle, stored: CircleCheck, failed: CircleX, removed: Archive, none: Minus } as const;

/** One copy's state in the history, with its error (or the retention date) underneath. */
function CopyCell({ copy }: { copy: BackupCopy | null }) {
  const badge = copyBadge(copy?.status, copy?.removedAt);
  const Icon = COPY_ICONS[badge.kind];
  return (
    <div className="space-y-1">
      <Badge tone={badge.tone} title={copy?.target}>
        <Icon className={badge.kind === 'running' ? 'animate-spin' : undefined} aria-hidden />
        {badge.label}
      </Badge>
      {copy?.status === 'FAILED' && copy.errorMessage && (
        <p className="line-clamp-2 max-w-72 text-xs text-status-danger" title={copy.errorMessage}>
          {copy.errorMessage}
        </p>
      )}
      {copy?.removedAt && <p className="text-xs text-muted-foreground">on {formatTimestamp(copy.removedAt)}</p>}
    </div>
  );
}

function CopyHealthRow({ label, copy }: { label: string; copy: BackupCopyStatus | undefined }) {
  return (
    <li className="flex flex-wrap items-start gap-x-6 gap-y-2 px-6 py-4">
      <div className="min-w-48 flex-1 space-y-1">
        <p className="text-sm font-medium">{label}</p>
        <p className="font-mono text-xs break-all text-muted-foreground">{copy ? copy.target : 'Not configured. Set BACKUP_SECONDARY_TARGET to keep a second copy in another place.'}</p>
      </div>
      {copy && (
        <div className="space-y-1 text-right">
          {copy.healthy ? (
            <Badge tone="success">
              <CircleCheck aria-hidden />
              Healthy
            </Badge>
          ) : (
            <Badge tone="danger">
              <TriangleAlert aria-hidden />
              Needs attention
            </Badge>
          )}
          <p className="text-xs text-muted-foreground">Last stored {copy.lastSuccessAt ? formatRelative(copy.lastSuccessAt) : 'never'}</p>
        </div>
      )}
      {copy && copy.problems.length > 0 && (
        <ul className="w-full list-disc space-y-0.5 pl-5 text-sm text-status-danger">
          {copy.problems.map((p) => (
            <li key={p.code}>{p.message}</li>
          ))}
        </ul>
      )}
    </li>
  );
}

function StatusTiles({ status }: { status: BackupStatus }) {
  const last = status.lastSuccess;
  const stale = status.problems.some((p) => p.code === 'STALE' || p.code === 'NO_BACKUP');
  const healthyCopies = status.copies.filter((c) => c.healthy).length;
  return (
    <KpiGrid aria-label="Backup summary">
      <KpiTile
        label="Last successful backup"
        icon={DatabaseBackup}
        value={last ? formatRelative(last.finishedAt ?? last.startedAt) : 'Never'}
        muted={!last}
        critical={stale}
        hint={last ? formatTimestamp(last.finishedAt ?? last.startedAt) : 'No backup has completed yet'}
      />
      <KpiTile
        label="Backup size"
        icon={HardDrive}
        value={formatBytes(last?.sizeBytes)}
        muted={!last}
        hint={last?.uploadFiles != null ? `Database and ${last.uploadFiles.toLocaleString()} uploaded ${last.uploadFiles === 1 ? 'file' : 'files'}, encrypted` : 'Database and uploaded files, encrypted'}
      />
      <KpiTile
        label="Next scheduled run"
        icon={CalendarClock}
        value={status.nextRunAt ? formatRelative(status.nextRunAt) : 'Off'}
        muted={!status.nextRunAt}
        hint={status.nextRunAt ? `${formatTimestamp(status.nextRunAt)} (nightly)` : 'The nightly schedule is switched off'}
      />
      <KpiTile
        label="Copies healthy"
        icon={CopyCheck}
        value={`${healthyCopies} of ${status.copies.length}`}
        critical={healthyCopies < status.copies.length}
        hint={`Kept ${status.keepDaily} daily and ${status.keepMonthly} monthly`}
      />
    </KpiGrid>
  );
}

function HistoryTable({ runs }: { runs: BackupRun[] }) {
  return (
    <Table aria-label="Backup history">
      <TableHeader>
        <TableRow>
          <TableHead>Started</TableHead>
          <TableHead>Trigger</TableHead>
          <TableHead>Primary copy</TableHead>
          <TableHead>Second copy</TableHead>
          <TableHead className="text-right">Size</TableHead>
          <TableHead className="text-right">Duration</TableHead>
          <TableHead>File</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {runs.map((run) => (
          <TableRow key={run.id}>
            <TableCell className="whitespace-nowrap">{formatTimestamp(run.startedAt)}</TableCell>
            <TableCell className="text-muted-foreground">{TRIGGER_LABELS[run.trigger]}</TableCell>
            <TableCell>
              <CopyCell copy={primaryCopy(run)} />
            </TableCell>
            <TableCell>
              <CopyCell copy={run.secondary} />
            </TableCell>
            <TableCell className="text-right whitespace-nowrap tabular-nums">{formatBytes(run.sizeBytes)}</TableCell>
            <TableCell className="text-right whitespace-nowrap tabular-nums">{formatDuration(run.startedAt, run.finishedAt)}</TableCell>
            <TableCell>
              {run.fileName ? (
                <div className="max-w-64 space-y-0.5">
                  <p className="truncate font-mono text-xs" title={run.fileName}>
                    {run.fileName}
                  </p>
                  {run.checksum && (
                    <p className="truncate font-mono text-[11px] text-muted-foreground" title={`SHA-256 ${run.checksum}`}>
                      SHA-256 {run.checksum.slice(0, 12)}…
                    </p>
                  )}
                </div>
              ) : (
                <span className="text-muted-foreground">—</span>
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/**
 * Administration → Backups (BACKUP_VIEW, Super Admin only by default): health of each copy, the last successful
 * backup, its size, the next nightly run and the history with the primary and second copy of every run. "Run backup
 * now" is the Super Admin's; "Export all data" needs DATA_EXPORT.
 */
export default function BackupsPage() {
  const { user } = useAuth();
  const status = useBackupStatus();
  const running = status.data?.running ?? false;
  const [page, setPage] = useState(0);
  const history = useBackupHistory(page, PAGE_SIZE, running);
  const runBackup = useRunBackup();
  const dataExport = useDataExport();
  const exporting = dataExport.state.phase === 'running';

  const startBackup = () =>
    runBackup.mutate(undefined, {
      onSuccess: () => toast.success('Backup started. This page updates when it finishes.'),
      onError: (error) => toast.error(errorMessage(error, 'The backup could not be started.')),
    });

  return (
    <div className="space-y-6">
      <PageHeader
        title="Backups"
        description="Nightly encrypted backups of the database and uploaded files, each copy checked after it is written."
        actions={
          <>
            {canExportAllData(user) && <ExportButton running={exporting} onStart={() => void dataExport.start()} />}
            {canRunBackup(user) && (
              <Button onClick={startBackup} disabled={running || runBackup.isPending}>
                {running || runBackup.isPending ? <LoaderCircle className="animate-spin" aria-hidden /> : <Play aria-hidden />}
                {running ? 'Backup running…' : 'Run backup now'}
              </Button>
            )}
          </>
        }
      />

      <ExportProgress state={dataExport.state} onCancel={dataExport.cancel} onDismiss={dataExport.dismiss} />

      {status.isPending ? (
        <KpiGrid aria-label="Loading backup status">
          {Array.from({ length: 4 }, (_, i) => (
            <KpiTile key={i} label=" " value="" loading />
          ))}
        </KpiGrid>
      ) : status.isError ? (
        <Card className="p-6">
          <ErrorState error={status.error} title="Couldn't load the backup status" onRetry={() => void status.refetch()} />
        </Card>
      ) : (
        <>
          {!status.data.healthy && (
            <Card className="flex gap-3 border-status-danger/50 bg-status-danger/5 px-5 py-4" role="alert">
              <TriangleAlert className="mt-0.5 size-5 shrink-0 text-status-danger" aria-hidden />
              <div className="space-y-1">
                <p className="font-semibold text-status-danger">Backups need attention</p>
                <ul className="list-disc space-y-0.5 pl-5 text-sm">
                  {status.data.problems.map((p) => (
                    <li key={p.code}>{p.message}</li>
                  ))}
                </ul>
              </div>
            </Card>
          )}
          <StatusTiles status={status.data} />
          <Panel title="Copies" icon={CopyCheck} description={`Each backup is written to every target and verified there. Times are in ${status.data.timeZone}.`}>
            <ul className="divide-y divide-border/70">
              <CopyHealthRow label="Primary copy" copy={status.data.copies.find((c) => c.copy === 'PRIMARY')} />
              <CopyHealthRow label="Second copy" copy={status.data.copies.find((c) => c.copy === 'SECONDARY')} />
            </ul>
          </Panel>
        </>
      )}

      <Panel title="History" icon={DatabaseBackup} description="Every backup run, newest first. Old files are removed by the retention policy; their rows stay.">
        {history.isPending ? (
          <div className="space-y-2 p-4" role="status" aria-label="Loading the backup history">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-11 rounded-md" />
            ))}
          </div>
        ) : history.isError ? (
          <div className="p-6">
            <ErrorState error={history.error} title="Couldn't load the backup history" onRetry={() => void history.refetch()} />
          </div>
        ) : history.data.content.length === 0 ? (
          <EmptyState icon={DatabaseBackup} title="No backups yet" description="The first backup runs tonight, or start one now." className="py-14" />
        ) : (
          <>
            <HistoryTable runs={history.data.content} />
            <div className="border-t border-border/70 px-4 py-3">
              <Pagination page={history.data.page} size={history.data.size} totalElements={history.data.totalElements} totalPages={history.data.totalPages} onPageChange={setPage} />
            </div>
          </>
        )}
      </Panel>
    </div>
  );
}
