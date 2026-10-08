import { Download, FileText, LoaderCircle, Trash2, Upload } from 'lucide-react';
import { useRef, useState } from 'react';
import { toast } from 'sonner';

import { ErrorState } from '@/components/common/ErrorState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { formatBytes } from '@/features/tasks/task-meta';
import { errorMessage } from '@/lib/api/errors';
import { formatDateTime } from '@/lib/format';
import { ACCEPTED_UPLOADS } from '@/lib/uploads';

import { downloadDocumentVersion, useAddDocumentVersion, useDeleteDocument, useDocument, type DocumentDetail } from './api';

export function DocumentDrawer({ documentId, onClose }: { documentId: number | null; onClose: () => void }) {
  const query = useDocument(documentId);
  return (
    <Dialog open={documentId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent aria-describedby={undefined} className="sm:max-w-xl">
        {query.isPending ? (
          <div className="space-y-4 p-6" role="status" aria-label="Loading document">
            <DialogTitle className="sr-only">Loading document</DialogTitle>
            <Skeleton className="h-8 w-2/3" />
            <Skeleton className="h-48" />
          </div>
        ) : query.isError ? (
          <div className="p-6">
            <DialogTitle className="sr-only">Document unavailable</DialogTitle>
            <ErrorState error={query.error} title="Couldn't open this document" onRetry={() => void query.refetch()} />
          </div>
        ) : (
          <Body key={query.data.id} document={query.data} onDeleted={onClose} />
        )}
      </SheetContent>
    </Dialog>
  );
}

function Body({ document, onDeleted }: { document: DocumentDetail; onDeleted: () => void }) {
  const addVersion = useAddDocumentVersion(document.id);
  const remove = useDeleteDocument();
  const input = useRef<HTMLInputElement>(null);
  const [changeNote, setChangeNote] = useState('');

  async function run(action: Promise<unknown>, success?: string): Promise<boolean> {
    try {
      await action;
      if (success) toast.success(success);
      return true;
    } catch (error) {
      toast.error(errorMessage(error));
      return false;
    }
  }

  return (
    <>
      <div className="space-y-2 border-b px-6 py-5 pr-12">
        <p className="text-sm text-muted-foreground">
          {document.department?.name ?? 'Company-wide'}
          {document.project && ` · ${document.project.code} ${document.project.name}`}
        </p>
        <DialogTitle className="text-xl">{document.name}</DialogTitle>
        <DialogDescription className={document.description ? 'text-sm' : 'sr-only'}>{document.description ?? 'Document versions'}</DialogDescription>
      </div>
      <div className="flex-1 space-y-6 overflow-y-auto px-6 py-5">
        <section>
          <h3 className="mb-3 text-sm font-semibold">Versions</h3>
          <ol className="divide-y rounded-lg border">
            {document.versions.map((v, i) => (
              <li key={v.versionNo} className="flex items-center gap-3 px-3 py-2.5">
                <FileText className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                <div className="min-w-0 flex-1">
                  <p className="flex items-center gap-2 text-sm font-medium">
                    Version {v.versionNo}
                    {i === 0 && <Badge tone="primary">Current</Badge>}
                  </p>
                  <p className="truncate text-xs text-muted-foreground">
                    {v.fileName} · {formatBytes(v.sizeBytes)} · {v.uploadedBy?.fullName ?? 'Former user'} · {formatDateTime(v.uploadedAt)}
                  </p>
                  {v.changeNote && <p className="mt-0.5 text-sm">{v.changeNote}</p>}
                </div>
                <Button variant="ghost" size="icon" className="size-8" aria-label={`Download version ${v.versionNo}`} onClick={() => void run(downloadDocumentVersion(document.id, v))}>
                  <Download />
                </Button>
              </li>
            ))}
          </ol>
        </section>
        {document.canEdit && (
          <section className="space-y-3 rounded-lg border p-4">
            <h3 className="text-sm font-semibold">Upload a new version</h3>
            <div className="space-y-1.5">
              <Label htmlFor="change-note">What changed</Label>
              <Input id="change-note" maxLength={500} value={changeNote} onChange={(e) => setChangeNote(e.target.value)} placeholder="Optional" />
            </div>
            <input
              ref={input}
              type="file"
              className="hidden"
              accept={ACCEPTED_UPLOADS}
              aria-label="New version file"
              onChange={(event) => {
                const file = event.target.files?.[0];
                event.target.value = '';
                if (file) void run(addVersion.mutateAsync({ file, changeNote }), `Version ${document.versions.length + 1} uploaded`).then((ok) => ok && setChangeNote(''));
              }}
            />
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" disabled={addVersion.isPending} onClick={() => input.current?.click()}>
                {addVersion.isPending ? <LoaderCircle className="animate-spin" aria-hidden /> : <Upload aria-hidden />}
                Choose file
              </Button>
              <Button
                variant="ghost"
                className="ml-auto text-destructive"
                disabled={remove.isPending}
                onClick={() => void run(remove.mutateAsync(document.id), 'Document deleted').then((ok) => ok && onDeleted())}
              >
                <Trash2 aria-hidden />
                Delete document
              </Button>
            </div>
          </section>
        )}
      </div>
    </>
  );
}
