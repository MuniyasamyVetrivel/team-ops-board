import { CircleCheck, CircleX, Download, LoaderCircle, X } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { formatBytes } from '@/lib/format';

import type { ExportState } from './use-data-export';

export function ExportButton({ running, onStart }: { running: boolean; onStart: () => void }) {
  return (
    <Button variant="outline" onClick={onStart} disabled={running}>
      {running ? <LoaderCircle className="animate-spin" aria-hidden /> : <Download aria-hidden />}
      {running ? 'Exporting…' : 'Export all data'}
    </Button>
  );
}

/**
 * The export's progress: the server builds the ZIP while it streams it, so the total is unknown and the bar is
 * indeterminate; the bytes received so far are shown instead.
 */
export function ExportProgress({ state, onCancel, onDismiss }: { state: ExportState; onCancel: () => void; onDismiss: () => void }) {
  if (state.phase === 'idle') return null;
  return (
    <Card className="flex flex-wrap items-center gap-4 px-5 py-4" aria-live="polite">
      {state.phase === 'running' && (
        <>
          <LoaderCircle className="size-5 shrink-0 animate-spin text-primary" aria-hidden />
          <div className="min-w-48 flex-1 space-y-2">
            <p className="text-sm font-medium">
              Exporting all data… <span className="font-normal text-muted-foreground tabular-nums">{formatBytes(state.loaded)} downloaded</span>
            </p>
            <div className="h-1.5 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label="Export progress" aria-valuetext={`${formatBytes(state.loaded)} downloaded`}>
              <div className="h-full w-1/3 animate-indeterminate rounded-full bg-primary motion-reduce:w-full motion-reduce:animate-none motion-reduce:opacity-60" />
            </div>
            <p className="text-xs text-muted-foreground">Every table as CSV plus all uploaded files, in one ZIP. Large exports take a few minutes; keep this page open.</p>
          </div>
          <Button variant="ghost" size="sm" onClick={onCancel}>
            <X aria-hidden />
            Cancel
          </Button>
        </>
      )}
      {state.phase === 'done' && (
        <>
          <CircleCheck className="size-5 shrink-0 text-status-success" aria-hidden />
          <p className="min-w-48 flex-1 text-sm">
            <span className="font-medium">Export downloaded:</span> {state.fileName} ({formatBytes(state.sizeBytes)}). It contains personal data: store it safely.
          </p>
          <Button variant="ghost" size="sm" onClick={onDismiss}>
            Dismiss
          </Button>
        </>
      )}
      {state.phase === 'failed' && (
        <>
          <CircleX className="size-5 shrink-0 text-status-danger" aria-hidden />
          <p className="min-w-48 flex-1 text-sm" role="alert">
            <span className="font-medium">Export failed:</span> {state.message}
          </p>
          <Button variant="ghost" size="sm" onClick={onDismiss}>
            Dismiss
          </Button>
        </>
      )}
    </Card>
  );
}
