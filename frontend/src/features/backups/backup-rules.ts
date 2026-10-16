import type { BadgeProps } from '@/components/ui/badge';

import type { BackupCopy, BackupRun, BackupStatusCode, BackupTrigger } from './api';

type Tone = NonNullable<BadgeProps['tone']>;

export interface CopyBadge {
  label: string;
  tone: Tone;
  kind: 'running' | 'stored' | 'failed' | 'removed' | 'none';
}

/**
 * How one copy of a backup reads in the history: running, stored, failed, removed by the retention policy, or not
 * configured. Always a word next to the colour.
 */
export function copyBadge(status: BackupStatusCode | null | undefined, removedAt: string | null | undefined): CopyBadge {
  if (!status) return { label: 'Not configured', tone: 'neutral', kind: 'none' };
  if (status === 'RUNNING') return { label: 'Running', tone: 'primary', kind: 'running' };
  if (status === 'FAILED') return { label: 'Failed', tone: 'danger', kind: 'failed' };
  if (removedAt) return { label: 'Removed (retention)', tone: 'neutral', kind: 'removed' };
  return { label: 'Stored', tone: 'success', kind: 'stored' };
}

export function primaryCopy(run: BackupRun): BackupCopy {
  return { target: run.target, status: run.status, errorMessage: run.errorMessage, removedAt: run.removedAt };
}

export const TRIGGER_LABELS: Record<BackupTrigger, string> = { SCHEDULED: 'Nightly', MANUAL: 'Run now' };

/** "45 s", "3 min 20 s", "1 h 05 min"; "—" while running or unknown. */
export function formatDuration(startedAt: string, finishedAt: string | null): string {
  if (!finishedAt) return '—';
  const seconds = Math.max(0, Math.round((new Date(finishedAt).getTime() - new Date(startedAt).getTime()) / 1000));
  if (seconds < 60) return `${seconds} s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return seconds % 60 === 0 ? `${minutes} min` : `${minutes} min ${seconds % 60} s`;
  return `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')} min`;
}

/** The health banner's sentence: the first problem, and how many more there are. */
export function bannerMessage(problems: { message: string }[]): string {
  if (problems.length === 0) return '';
  const more = problems.length - 1;
  return more > 0 ? `${problems[0].message} (+${more} more)` : problems[0].message;
}
