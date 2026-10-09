import { Download, FileText, LoaderCircle, Trash2, Upload } from 'lucide-react';
import { useRef } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { formatBytes } from '@/features/tasks/task-meta';
import { errorMessage } from '@/lib/api/errors';
import type { UserSummary } from '@/lib/api/types';
import { formatRelative } from '@/lib/format';
import { ACCEPTED_UPLOADS } from '@/lib/uploads';

export interface AttachmentFile {
  fileId: number;
  fileName: string;
  sizeBytes: number;
  addedBy: UserSummary | null;
  addedAt: string;
}

interface AttachmentListProps {
  /** undefined while loading. */
  files: AttachmentFile[] | undefined;
  canEdit: boolean;
  uploading: boolean;
  onUpload: (file: File) => Promise<unknown>;
  onRemove: (fileId: number) => Promise<unknown>;
  onDownload: (file: AttachmentFile) => Promise<unknown>;
}

/**
 * Files attached to a record: download for everyone, upload and remove for editors. The server checks type, size and
 * name (UploadPolicy) and serves every file as a download.
 */
export function AttachmentList({ files, canEdit, uploading, onUpload, onRemove, onDownload }: AttachmentListProps) {
  const input = useRef<HTMLInputElement>(null);

  async function run(action: Promise<unknown>, success?: string) {
    try {
      await action;
      if (success) toast.success(success);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <div className="space-y-3">
      {files === undefined ? (
        <Skeleton className="h-12" role="status" aria-label="Loading attachments" />
      ) : files.length === 0 ? (
        <p className="text-sm text-muted-foreground">No files attached.</p>
      ) : (
        <ul className="divide-y rounded-lg border" aria-label="Attachments">
          {files.map((file) => (
            <li key={file.fileId} className="flex items-center gap-3 px-3 py-2">
              <FileText className="size-4 shrink-0 text-muted-foreground" aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{file.fileName}</p>
                <p className="text-xs text-muted-foreground">
                  {formatBytes(file.sizeBytes)} · {file.addedBy?.fullName ?? 'Former user'} · {formatRelative(file.addedAt)}
                </p>
              </div>
              <Button variant="ghost" size="icon" className="size-8" aria-label={`Download ${file.fileName}`} onClick={() => void run(onDownload(file))}>
                <Download />
              </Button>
              {canEdit && (
                <Button variant="ghost" size="icon" className="size-8" aria-label={`Remove ${file.fileName}`} onClick={() => void run(onRemove(file.fileId), `${file.fileName} removed`)}>
                  <Trash2 />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {canEdit && (
        <div className="flex flex-wrap items-center gap-3">
          <input
            ref={input}
            type="file"
            className="hidden"
            data-testid="attachment-input"
            accept={ACCEPTED_UPLOADS}
            onChange={(event) => {
              const file = event.target.files?.[0];
              event.target.value = '';
              if (file) void run(onUpload(file), `${file.name} uploaded`);
            }}
          />
          <Button variant="outline" size="sm" disabled={uploading} onClick={() => input.current?.click()}>
            {uploading ? <LoaderCircle className="animate-spin" aria-hidden /> : <Upload aria-hidden />}
            Attach file
          </Button>
          <p className="text-xs text-muted-foreground">PDF, Office, images, text, CSV or ZIP. Up to 20 MB.</p>
        </div>
      )}
    </div>
  );
}
