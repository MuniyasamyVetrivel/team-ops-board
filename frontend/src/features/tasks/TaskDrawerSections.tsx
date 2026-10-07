import { Download, Eye, EyeOff, FileText, Link2, LoaderCircle, Plus, Trash2, Upload } from 'lucide-react';
import { useRef, useState } from 'react';
import { toast } from 'sonner';

import { UserAvatar } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useAuth } from '@/features/auth/use-auth';
import { useTeamDirectory } from '@/features/team/api';
import { errorMessage } from '@/lib/api/errors';
import { formatRelative } from '@/lib/format';
import { useDebouncedValue } from '@/lib/use-debounced-value';

import { downloadAttachment, useTaskAttachments, useTaskChecklist, useTaskComments, useTaskDependencies, useTaskWatchers, useTasks } from './api';
import { TaskStatusBadge } from './TaskBadges';
import { formatBytes, historyLabel, historyValue } from './task-meta';
import type { TaskDetail } from './types';

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

export function CommentsSection({ task }: { task: TaskDetail }) {
  const { user } = useAuth();
  const comments = useTaskComments(task.id);
  const [body, setBody] = useState('');

  return (
    <div className="space-y-4">
      {task.comments.length === 0 ? (
        <Empty>No comments yet. Start the conversation.</Empty>
      ) : (
        <ul className="space-y-4">
          {task.comments.map((comment) => (
            <li key={comment.id} className="flex gap-3">
              <UserAvatar name={comment.author?.fullName ?? 'Former user'} size="sm" />
              <div className="min-w-0 flex-1">
                <p className="text-sm">
                  <span className="font-medium">{comment.author?.fullName ?? 'Former user'}</span>{' '}
                  <span className="text-xs text-muted-foreground">
                    {formatRelative(comment.createdAt)}
                    {comment.edited && ' · edited'}
                  </span>
                </p>
                <p className="mt-0.5 text-sm whitespace-pre-wrap">{comment.body}</p>
              </div>
              {(comment.author?.id === user?.id || task.permissions.canEdit) && (
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-7"
                  aria-label="Delete comment"
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
      {task.permissions.canComment && (
        <form
          className="space-y-2"
          onSubmit={(event) => {
            event.preventDefault();
            if (!body.trim()) return;
            void attempt(comments.add.mutateAsync(body.trim())).then((ok) => ok && setBody(''));
          }}
        >
          <Textarea value={body} onChange={(event) => setBody(event.target.value)} rows={3} maxLength={5000} placeholder="Write a comment…" aria-label="New comment" />
          <div className="flex justify-end">
            <Button type="submit" size="sm" disabled={!body.trim() || comments.add.isPending}>
              {comments.add.isPending && <LoaderCircle className="animate-spin" aria-hidden />}
              Comment
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}

export function ChecklistSection({ task }: { task: TaskDetail }) {
  const checklist = useTaskChecklist(task.id);
  const [content, setContent] = useState('');
  const canEdit = task.permissions.canEdit;
  const done = task.checklist.filter((i) => i.done).length;

  return (
    <div className="space-y-3">
      {task.checklist.length > 0 && (
        <div className="h-1.5 overflow-hidden rounded-full bg-muted" role="progressbar" aria-valuenow={done} aria-valuemax={task.checklist.length} aria-label="Checklist progress">
          <div className="h-full bg-status-success transition-all" style={{ width: `${(done / task.checklist.length) * 100}%` }} />
        </div>
      )}
      {task.checklist.length === 0 ? (
        <Empty>No checklist items.</Empty>
      ) : (
        <ul className="divide-y rounded-lg border">
          {task.checklist.map((item) => (
            <li key={item.id} className="flex items-center gap-3 px-3 py-2">
              <Checkbox
                checked={item.done}
                disabled={!canEdit || checklist.toggle.isPending}
                onChange={(event) => void attempt(checklist.toggle.mutateAsync({ itemId: item.id, done: event.target.checked }))}
                aria-label={item.content}
              />
              <span className={item.done ? 'flex-1 text-sm text-muted-foreground line-through' : 'flex-1 text-sm'}>{item.content}</span>
              {canEdit && (
                <Button variant="ghost" size="icon" className="size-7" aria-label={`Remove ${item.content}`} onClick={() => void attempt(checklist.remove.mutateAsync(item.id))}>
                  <Trash2 className="size-3.5" />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {canEdit && (
        <form
          className="flex gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            if (!content.trim()) return;
            void attempt(checklist.add.mutateAsync(content.trim())).then((ok) => ok && setContent(''));
          }}
        >
          <Input value={content} onChange={(event) => setContent(event.target.value)} maxLength={500} placeholder="Add an item" aria-label="New checklist item" />
          <Button type="submit" variant="outline" disabled={!content.trim() || checklist.add.isPending}>
            <Plus aria-hidden />
            Add
          </Button>
        </form>
      )}
    </div>
  );
}

export function AttachmentsSection({ task }: { task: TaskDetail }) {
  const { user } = useAuth();
  const attachments = useTaskAttachments(task.id);
  const input = useRef<HTMLInputElement>(null);

  return (
    <div className="space-y-3">
      {task.attachments.length === 0 ? (
        <Empty>No files attached.</Empty>
      ) : (
        <ul className="divide-y rounded-lg border">
          {task.attachments.map((file) => (
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
                onClick={() => void downloadAttachment(task.id, file.fileId, file.fileName).catch((error: unknown) => toast.error(errorMessage(error)))}
              >
                <Download />
              </Button>
              {(file.addedBy?.id === user?.id || task.permissions.canEdit) && (
                <Button variant="ghost" size="icon" className="size-8" aria-label={`Remove ${file.fileName}`} onClick={() => void attempt(attachments.remove.mutateAsync(file.fileId))}>
                  <Trash2 />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {task.permissions.canEdit && (
        <>
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
          <p className="text-xs text-muted-foreground">PDF, Office, images, text, CSV or ZIP. Up to 20 MB.</p>
        </>
      )}
    </div>
  );
}

export function DependenciesSection({ task }: { task: TaskDetail }) {
  const dependencies = useTaskDependencies(task.id);
  const [search, setSearch] = useState('');
  const debounced = useDebouncedValue(search.trim());
  const results = useTasks({ search: debounced, size: 6, sort: 'updated,desc' }, task.permissions.canEdit && debounced.length >= 2);
  const existing = new Set([task.id, ...task.dependencies.map((d) => d.id)]);
  const candidates = (results.data?.content ?? []).filter((t) => !existing.has(t.id));

  return (
    <div className="space-y-3">
      <p className="text-xs text-muted-foreground">Tasks that need to be finished before this one.</p>
      {task.dependencies.length === 0 ? (
        <Empty>No dependencies.</Empty>
      ) : (
        <ul className="divide-y rounded-lg border">
          {task.dependencies.map((dep) => (
            <li key={dep.id} className="flex items-center gap-3 px-3 py-2">
              <Link2 className="size-4 shrink-0 text-muted-foreground" aria-hidden />
              <span className="font-mono text-xs text-muted-foreground">{dep.code}</span>
              <span className="min-w-0 flex-1 truncate text-sm">{dep.title}</span>
              <TaskStatusBadge status={dep.status} />
              {task.permissions.canEdit && (
                <Button variant="ghost" size="icon" className="size-7" aria-label={`Remove dependency ${dep.code}`} onClick={() => void attempt(dependencies.remove.mutateAsync(dep.id))}>
                  <Trash2 className="size-3.5" />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {task.permissions.canEdit && (
        <div className="space-y-2">
          <Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Find a task by code or title" aria-label="Find a task to depend on" />
          {candidates.length > 0 && (
            <ul className="divide-y rounded-lg border">
              {candidates.map((candidate) => (
                <li key={candidate.id}>
                  <button
                    type="button"
                    className="flex w-full items-center gap-3 px-3 py-2 text-left text-sm hover:bg-muted"
                    onClick={() => void attempt(dependencies.add.mutateAsync(candidate.id)).then((ok) => ok && setSearch(''))}
                  >
                    <span className="font-mono text-xs text-muted-foreground">{candidate.code}</span>
                    <span className="min-w-0 flex-1 truncate">{candidate.title}</span>
                    <Plus className="size-4 text-muted-foreground" aria-hidden />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

export function WatchersSection({ task, onLostAccess }: { task: TaskDetail; onLostAccess: () => void }) {
  const { user } = useAuth();
  const watchers = useTaskWatchers(task.id);
  const people = useTeamDirectory({ size: 100, sort: 'name,asc' }, task.permissions.canEdit);
  const [userId, setUserId] = useState('');
  const watching = task.watchers.some((w) => w.id === user?.id);
  const candidates = (people.data?.content ?? []).filter((p) => !task.watchers.some((w) => w.id === p.id));

  async function unwatch(id: number) {
    try {
      const detail = await watchers.remove.mutateAsync(id);
      if (detail === null) onLostAccess();
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <div className="space-y-3">
      {user && task.permissions.canWatch && (
        <Button variant="outline" size="sm" disabled={watchers.add.isPending || watchers.remove.isPending} onClick={() => void (watching ? unwatch(user.id) : attempt(watchers.add.mutateAsync(user.id), 'You are now watching this task'))}>
          {watching ? <EyeOff aria-hidden /> : <Eye aria-hidden />}
          {watching ? 'Stop watching' : 'Watch'}
        </Button>
      )}
      {task.watchers.length === 0 ? (
        <Empty>Nobody is watching this task.</Empty>
      ) : (
        <ul className="divide-y rounded-lg border">
          {task.watchers.map((watcher) => (
            <li key={watcher.id} className="flex items-center gap-3 px-3 py-2">
              <UserAvatar name={watcher.fullName} size="sm" />
              <span className="flex-1 text-sm">{watcher.fullName}</span>
              {task.permissions.canEdit && watcher.id !== user?.id && (
                <Button variant="ghost" size="icon" className="size-7" aria-label={`Remove ${watcher.fullName}`} onClick={() => void unwatch(watcher.id)}>
                  <Trash2 className="size-3.5" />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {task.permissions.canEdit && (
        <div className="flex gap-2">
          <Select aria-label="Add watcher" value={userId} onChange={(event) => setUserId(event.target.value)}>
            <option value="">Add a watcher…</option>
            {candidates.map((p) => (
              <option key={p.id} value={p.id}>
                {p.fullName} — {p.department.name}
              </option>
            ))}
          </Select>
          <Button variant="outline" disabled={!userId || watchers.add.isPending} onClick={() => void attempt(watchers.add.mutateAsync(Number(userId))).then((ok) => ok && setUserId(''))}>
            Add
          </Button>
        </div>
      )}
    </div>
  );
}

export function HistorySection({ task }: { task: TaskDetail }) {
  if (task.history.length === 0) return <Empty>No changes yet.</Empty>;
  return (
    <ol className="relative space-y-4 border-l pl-5">
      {task.history.map((entry) => (
        <li key={entry.id} className="text-sm">
          <span className="absolute -left-1 mt-1.5 size-2 rounded-full bg-border" aria-hidden />
          <p>
            <span className="font-medium">{entry.changedBy?.fullName ?? 'System'}</span> {historyLabel(entry.field)}
            {(entry.oldValue !== null || entry.newValue !== null) && entry.field !== 'created' && (
              <span className="text-muted-foreground">
                {' '}
                {historyValue(entry.oldValue)} → {historyValue(entry.newValue)}
              </span>
            )}
          </p>
          <p className="text-xs text-muted-foreground">{formatRelative(entry.changedAt)}</p>
        </li>
      ))}
    </ol>
  );
}
