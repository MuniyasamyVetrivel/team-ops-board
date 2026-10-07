import { Download, FileText, LoaderCircle, Lock, Trash2, Upload } from 'lucide-react';
import { useRef, useState } from 'react';
import { toast } from 'sonner';

import { UserAvatar } from '@/components/common/UserAvatar';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { useAuth } from '@/features/auth/use-auth';
import { formatBytes } from '@/features/tasks/task-meta';
import { errorMessage } from '@/lib/api/errors';
import { formatRelative } from '@/lib/format';
import { cn } from '@/lib/utils';

import { downloadTicketAttachment, useTicketAttachments, useTicketComments } from './api';
import { ticketHistoryLabel, ticketHistoryValue } from './ticket-meta';
import type { TicketDetail } from './types';

async function attempt(action: Promise<unknown>, success?: string): Promise<boolean> {
  try {
    await action;
    if (success) toast.success(success);
    return true;
  } catch (error) {
    toast.error(errorMessage(error));
    return false;
  }
}

function Empty({ children }: { children: string }) {
  return <p className="rounded-lg border border-dashed px-3 py-4 text-center text-sm text-muted-foreground">{children}</p>;
}

/**
 * Replies between the requester and the team. Internal notes are only returned by the server to people who can
 * work on the ticket, and are marked with a lock and a label.
 */
export function ConversationSection({ ticket }: { ticket: TicketDetail }) {
  const { user } = useAuth();
  const comments = useTicketComments(ticket.id);
  const [body, setBody] = useState('');
  const [internal, setInternal] = useState(false);
  const closed = ticket.status === 'CLOSED';
  const canReply = !closed || ticket.permissions.canInternalNote;
  const willBeInternal = internal || closed;

  return (
    <div className="space-y-4">
      {ticket.comments.length === 0 ? (
        <Empty>No replies yet.</Empty>
      ) : (
        <ul className="space-y-3">
          {ticket.comments.map((comment) => (
            <li
              key={comment.id}
              className={cn('flex gap-3 rounded-lg p-3', comment.internal ? 'border border-status-warning/30 bg-status-warning/5' : 'border bg-card')}
            >
              <UserAvatar name={comment.author?.fullName ?? 'Former user'} size="sm" />
              <div className="min-w-0 flex-1">
                <p className="flex flex-wrap items-center gap-2 text-sm">
                  <span className="font-medium">{comment.author?.fullName ?? 'Former user'}</span>
                  {comment.internal && (
                    <Badge tone="warning">
                      <Lock aria-hidden />
                      Internal note
                    </Badge>
                  )}
                  {comment.fromRequester && <Badge tone="neutral">Requester</Badge>}
                  <span className="text-xs text-muted-foreground">
                    {formatRelative(comment.createdAt)}
                    {comment.edited && ' · edited'}
                  </span>
                </p>
                <p className="mt-1 text-sm whitespace-pre-wrap">{comment.body}</p>
              </div>
              {(comment.author?.id === user?.id || ticket.permissions.canWork) && (
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-7"
                  aria-label="Delete reply"
                  disabled={comments.remove.isPending}
                  onClick={() => void attempt(comments.remove.mutateAsync(comment.id))}
                >
                  <Trash2 className="size-3.5" />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {canReply ? (
        <form
          className="space-y-2"
          onSubmit={(event) => {
            event.preventDefault();
            if (!body.trim()) return;
            void attempt(comments.add.mutateAsync({ body: body.trim(), internal: willBeInternal }), willBeInternal ? 'Note added' : 'Reply sent').then(
              (ok) => ok && setBody(''),
            );
          }}
        >
          <Textarea
            value={body}
            onChange={(event) => setBody(event.target.value)}
            rows={3}
            maxLength={10000}
            placeholder={willBeInternal ? 'Add an internal note for the team…' : 'Write a reply…'}
            aria-label={willBeInternal ? 'New internal note' : 'New reply'}
            className={cn(willBeInternal && 'border-status-warning/50')}
          />
          <div className="flex items-center justify-between gap-3">
            {ticket.permissions.canInternalNote && !closed ? (
              <div className="flex items-center gap-2">
                <Checkbox id="internal-note" checked={internal} onChange={(event) => setInternal(event.target.checked)} />
                <Label htmlFor="internal-note" className="text-sm font-normal">
                  Internal note (hidden from the requester)
                </Label>
              </div>
            ) : (
              <span className="text-xs text-muted-foreground">{closed ? 'Closed: notes are visible to the team only.' : ''}</span>
            )}
            <Button type="submit" size="sm" disabled={!body.trim() || comments.add.isPending}>
              {comments.add.isPending && <LoaderCircle className="animate-spin" aria-hidden />}
              {willBeInternal ? 'Add note' : 'Send reply'}
            </Button>
          </div>
        </form>
      ) : (
        <p className="text-sm text-muted-foreground">This ticket is closed. Raise a new ticket if you need more help.</p>
      )}
    </div>
  );
}

export function TicketFilesSection({ ticket }: { ticket: TicketDetail }) {
  const { user } = useAuth();
  const attachments = useTicketAttachments(ticket.id);
  const input = useRef<HTMLInputElement>(null);

  return (
    <div className="space-y-3">
      {ticket.attachments.length === 0 ? (
        <Empty>No files attached.</Empty>
      ) : (
        <ul className="divide-y rounded-lg border">
          {ticket.attachments.map((file) => (
            <li key={file.fileId} className="flex items-center gap-3 px-3 py-2">
              <FileText className="size-4 shrink-0 text-muted-foreground" aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{file.fileName}</p>
                <p className="text-xs text-muted-foreground">
                  {formatBytes(file.sizeBytes)} · {file.addedBy?.fullName ?? 'Former user'} · {formatRelative(file.addedAt)}
                </p>
              </div>
              <Button
                variant="ghost"
                size="icon"
                className="size-8"
                aria-label={`Download ${file.fileName}`}
                onClick={() => void downloadTicketAttachment(ticket.id, file.fileId, file.fileName).catch((error: unknown) => toast.error(errorMessage(error)))}
              >
                <Download />
              </Button>
              {(file.addedBy?.id === user?.id || ticket.permissions.canWork) && (
                <Button variant="ghost" size="icon" className="size-8" aria-label={`Remove ${file.fileName}`} onClick={() => void attempt(attachments.remove.mutateAsync(file.fileId))}>
                  <Trash2 />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      <input
        ref={input}
        type="file"
        className="hidden"
        accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.csv,.txt,.md,.png,.jpg,.jpeg,.gif,.webp,.zip"
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = '';
          if (file) void attempt(attachments.upload.mutateAsync(file), `${file.name} uploaded`);
        }}
      />
      <Button variant="outline" disabled={attachments.upload.isPending} onClick={() => input.current?.click()}>
        {attachments.upload.isPending ? <LoaderCircle className="animate-spin" aria-hidden /> : <Upload aria-hidden />}
        Upload file
      </Button>
      <p className="text-xs text-muted-foreground">Screenshots, logs or documents. PDF, Office, images, text, CSV or ZIP, up to 20 MB.</p>
    </div>
  );
}

export function TicketHistorySection({ ticket }: { ticket: TicketDetail }) {
  if (ticket.history.length === 0) return <Empty>No changes yet.</Empty>;
  return (
    <ol className="relative space-y-4 border-l pl-5">
      {ticket.history.map((entry) => (
        <li key={entry.id} className="text-sm">
          <span className="absolute -left-1 mt-1.5 size-2 rounded-full bg-border" aria-hidden />
          <p>
            <span className="font-medium">{entry.changedBy?.fullName ?? 'System'}</span> {ticketHistoryLabel(entry.field)}
            {(entry.oldValue !== null || entry.newValue !== null) && entry.field !== 'created' && (
              <span className="text-muted-foreground">
                {' '}
                {ticketHistoryValue(entry.oldValue)} → {ticketHistoryValue(entry.newValue)}
              </span>
            )}
          </p>
          <p className="text-xs text-muted-foreground">{formatRelative(entry.changedAt)}</p>
        </li>
      ))}
    </ol>
  );
}
