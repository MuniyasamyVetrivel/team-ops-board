import { useQueryClient } from '@tanstack/react-query';
import { Check, Pencil, X } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { toast } from 'sonner';

import { ErrorState } from '@/components/common/ErrorState';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import { isSuperAdmin } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { useTeamDirectory } from '@/features/team/api';
import { errorMessage, toApiError } from '@/lib/api/errors';
import { formatDate } from '@/lib/format';

import { taskKeys, toUpdateInput, useAssignTask, useChangeStatus, useProjectOptions, useTask, useUpdateTask } from './api';
import { DueBadge, TaskStatusBadge } from './TaskBadges';
import { AttachmentsSection, ChecklistSection, CommentsSection, DependenciesSection, HistorySection, WatchersSection } from './TaskDrawerSections';
import { PRIORITY_LABELS, STATUS_LABELS } from './task-meta';
import { ALL_STATUSES, PRIORITIES, type TaskDetail, type TaskPriority, type TaskStatus, type UpdateTaskInput } from './types';

interface TaskDrawerProps {
  taskId: number | null;
  onClose: () => void;
}

export function TaskDrawer({ taskId, onClose }: TaskDrawerProps) {
  const query = useTask(taskId);
  return (
    <Dialog open={taskId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent aria-describedby={undefined} className="sm:max-w-3xl">
        {query.isPending ? (
          <div className="space-y-4 p-6" role="status" aria-label="Loading task">
            <DialogTitle className="sr-only">Loading task</DialogTitle>
            <Skeleton className="h-8 w-2/3" />
            <Skeleton className="h-40" />
            <Skeleton className="h-60" />
          </div>
        ) : query.isError ? (
          <div className="p-6">
            <DialogTitle className="sr-only">Task unavailable</DialogTitle>
            <ErrorState error={query.error} title="Couldn't open this task" onRetry={() => void query.refetch()} />
          </div>
        ) : (
          <TaskDrawerBody key={query.data.id} task={query.data} onClose={onClose} />
        )}
      </SheetContent>
    </Dialog>
  );
}

/** Saves one field at a time by sending the full update with the task's version. */
function useFieldSaver(task: TaskDetail) {
  const update = useUpdateTask(task.id);
  const queryClient = useQueryClient();
  return {
    pending: update.isPending,
    save: async (patch: Partial<Omit<UpdateTaskInput, 'version'>>) => {
      try {
        await update.mutateAsync(toUpdateInput(task, patch));
      } catch (error) {
        const stale = toApiError(error)?.code === 'STALE_UPDATE';
        if (stale) void queryClient.invalidateQueries({ queryKey: taskKeys.detail(task.id) });
        toast.error(stale ? 'Someone else just changed this task. It has been reloaded; please try again.' : errorMessage(error));
      }
    },
  };
}

function TaskDrawerBody({ task, onClose }: { task: TaskDetail; onClose: () => void }) {
  const { permissions } = task;
  const doneItems = task.checklist.filter((item) => item.done).length;

  return (
    <>
      <div className="space-y-3 border-b px-6 py-5 pr-12">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="font-mono text-muted-foreground">{task.code}</span>
          <TaskStatusBadge status={task.status} />
          <DueBadge dueDate={task.dueDate} state={task.dueState} />
          {!permissions.canEdit && <span className="text-xs text-muted-foreground">View only</span>}
        </div>
        <TitleEditor task={task} />
        <DialogDescription className="sr-only">Task details for {task.title}</DialogDescription>
      </div>

      <div className="flex-1 overflow-y-auto">
        <div className="space-y-6 px-6 py-5">
          {/* Remount on each saved version so uncontrolled inputs show the saved values. */}
          <Properties key={task.version} task={task} />
          <DescriptionEditor task={task} />
        </div>

        <Tabs defaultValue="comments" className="pb-6">
          <TabsList>
            <TabsTrigger value="comments">Comments ({task.comments.length})</TabsTrigger>
            <TabsTrigger value="checklist">
              Checklist ({doneItems}/{task.checklist.length})
            </TabsTrigger>
            <TabsTrigger value="files">Files ({task.attachments.length})</TabsTrigger>
            <TabsTrigger value="dependencies">Depends on ({task.dependencies.length})</TabsTrigger>
            <TabsTrigger value="watchers">Watchers ({task.watchers.length})</TabsTrigger>
            <TabsTrigger value="history">History</TabsTrigger>
          </TabsList>
          <div className="px-6 pt-4">
            <TabsContent value="comments">
              <CommentsSection task={task} />
            </TabsContent>
            <TabsContent value="checklist">
              <ChecklistSection task={task} />
            </TabsContent>
            <TabsContent value="files">
              <AttachmentsSection task={task} />
            </TabsContent>
            <TabsContent value="dependencies">
              <DependenciesSection task={task} />
            </TabsContent>
            <TabsContent value="watchers">
              <WatchersSection task={task} onLostAccess={onClose} />
            </TabsContent>
            <TabsContent value="history">
              <HistorySection task={task} />
            </TabsContent>
          </div>
        </Tabs>
      </div>
    </>
  );
}

function TitleEditor({ task }: { task: TaskDetail }) {
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(task.title);
  const { save, pending } = useFieldSaver(task);

  if (!editing) {
    return (
      <div className="flex items-start gap-2">
        <DialogTitle className="text-xl leading-snug">{task.title}</DialogTitle>
        {task.permissions.canEdit && (
          <Button variant="ghost" size="icon" className="size-7 shrink-0" onClick={() => setEditing(true)} aria-label="Edit title">
            <Pencil className="size-3.5" />
          </Button>
        )}
      </div>
    );
  }
  return (
    <form
      className="flex items-center gap-2"
      onSubmit={(event) => {
        event.preventDefault();
        if (!title.trim()) return;
        void save({ title: title.trim() }).then(() => setEditing(false));
      }}
    >
      <DialogTitle className="sr-only">{task.title}</DialogTitle>
      <Input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={250} autoFocus aria-label="Title" />
      <Button type="submit" size="icon" disabled={pending || !title.trim()} aria-label="Save title">
        <Check />
      </Button>
      <Button type="button" variant="ghost" size="icon" onClick={() => { setTitle(task.title); setEditing(false); }} aria-label="Cancel">
        <X />
      </Button>
    </form>
  );
}

function Property({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[7rem_1fr] items-center gap-3 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <div className="min-w-0">{children}</div>
    </div>
  );
}

/** Inline-editable properties. Selects save immediately; text inputs save on blur. */
function Properties({ task }: { task: TaskDetail }) {
  const { user } = useAuth();
  const { permissions } = task;
  const { save, pending } = useFieldSaver(task);
  const changeStatus = useChangeStatus(task.id);
  const assign = useAssignTask(task.id);
  const departments = useDepartments();
  const projects = useProjectOptions();
  const people = useTeamDirectory({ size: 100, sort: 'name,asc' }, permissions.canAssign);
  const disabled = !permissions.canEdit || pending;

  // Managers may only assign within their scope; offer the task's department (plus self) unless Super Admin.
  const assignable = (people.data?.content ?? []).filter((p) => isSuperAdmin(user) || p.department.id === task.department.id);
  const statuses = ALL_STATUSES.filter((s) => s !== 'CANCELLED' || permissions.canCancel || task.status === 'CANCELLED');

  async function run(action: Promise<unknown>, success?: string) {
    try {
      await action;
      if (success) toast.success(success);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <div className="grid gap-x-8 gap-y-3 sm:grid-cols-2">
      <Property label="Status">
        <Select
          aria-label="Status"
          value={task.status}
          disabled={!permissions.canEdit || changeStatus.isPending}
          onChange={(event) => {
            const next = event.target.value as TaskStatus;
            const reopening = (task.status === 'COMPLETED' || task.status === 'CANCELLED') && next !== 'COMPLETED' && next !== 'CANCELLED';
            void run(changeStatus.mutateAsync(next), reopening ? 'Task reopened' : next === 'COMPLETED' ? 'Marked as completed' : undefined);
          }}
        >
          {statuses.map((s) => (
            <option key={s} value={s}>
              {STATUS_LABELS[s]}
            </option>
          ))}
        </Select>
      </Property>
      <Property label="Assignee">
        <Select
          aria-label="Assignee"
          value={task.assignee?.id ?? ''}
          disabled={!permissions.canEdit || assign.isPending}
          onChange={(event) => void run(assign.mutateAsync(event.target.value ? Number(event.target.value) : null))}
        >
          <option value="">Unassigned</option>
          {task.assignee && task.assignee.id !== user?.id && !assignable.some((p) => p.id === task.assignee?.id) && (
            <option value={task.assignee.id}>{task.assignee.fullName}</option>
          )}
          {user && <option value={user.id}>Me ({user.fullName})</option>}
          {permissions.canAssign &&
            assignable
              .filter((p) => p.id !== user?.id)
              .map((p) => (
                <option key={p.id} value={p.id}>
                  {p.fullName}
                </option>
              ))}
        </Select>
      </Property>
      <Property label="Priority">
        <Select aria-label="Priority" value={task.priority} disabled={disabled} onChange={(event) => void save({ priority: event.target.value as TaskPriority })}>
          {PRIORITIES.map((p) => (
            <option key={p} value={p}>
              {PRIORITY_LABELS[p]}
            </option>
          ))}
        </Select>
      </Property>
      <Property label="Due date">
        <Input
          type="date"
          aria-label="Due date"
          defaultValue={task.dueDate ?? ''}
          disabled={disabled}
          onBlur={(event) => {
            const value = event.target.value || null;
            if (value !== task.dueDate) void save({ dueDate: value });
          }}
        />
      </Property>
      <Property label="Start date">
        <Input
          type="date"
          aria-label="Start date"
          defaultValue={task.startDate ?? ''}
          disabled={disabled}
          onBlur={(event) => {
            const value = event.target.value || null;
            if (value !== task.startDate) void save({ startDate: value });
          }}
        />
      </Property>
      <Property label="Department">
        <Select
          aria-label="Department"
          value={task.department.id}
          disabled={disabled}
          onChange={(event) => void save({ departmentId: Number(event.target.value) })}
        >
          {departments.data?.map((d) => (
            <option key={d.id} value={d.id} disabled={d.status !== 'ACTIVE'}>
              {d.name}
            </option>
          ))}
        </Select>
      </Property>
      <Property label="Estimate">
        <HoursInput label="Estimate (hours)" value={task.estimatedHours} disabled={disabled} onSave={(hours) => save({ estimatedHours: hours })} />
      </Property>
      <Property label="Logged">
        <HoursInput label="Logged hours" value={task.actualHours} disabled={disabled} onSave={(hours) => save({ actualHours: hours })} />
      </Property>
      <Property label="Project">
        <Select
          aria-label="Project"
          value={task.project?.id ?? ''}
          disabled={disabled}
          onChange={(event) => void save({ projectId: event.target.value ? Number(event.target.value) : null })}
        >
          <option value="">No project</option>
          {task.project && !projects.data?.some((p) => p.id === task.project?.id) && <option value={task.project.id}>{task.project.name}</option>}
          {projects.data?.map((p) => (
            <option key={p.id} value={p.id}>
              {p.code} · {p.name}
            </option>
          ))}
        </Select>
      </Property>
      <Property label="Tags">
        <Input
          aria-label="Tags"
          placeholder={disabled ? 'No tags' : 'Comma separated'}
          defaultValue={task.tags.join(', ')}
          disabled={disabled}
          onBlur={(event) => {
            const tags = event.target.value.split(',').map((t) => t.trim()).filter(Boolean);
            if (tags.join(',') !== task.tags.join(',')) void save({ tags });
          }}
        />
      </Property>
      <Property label="Created">
        <span className="text-muted-foreground">
          {formatDate(task.createdAt)}
          {task.createdBy ? ` by ${task.createdBy.fullName}` : ''}
        </span>
      </Property>
      {task.completedAt && (
        <Property label="Completed">
          <span className="text-muted-foreground">{formatDate(task.completedAt)}</span>
        </Property>
      )}
    </div>
  );
}

function HoursInput({ label, value, disabled, onSave }: { label: string; value: number | null; disabled: boolean; onSave: (hours: number | null) => Promise<void> }) {
  return (
    <div className="relative">
      <Input
        type="number"
        min={0}
        max={9999}
        step={0.5}
        aria-label={label}
        defaultValue={value ?? ''}
        disabled={disabled}
        className="pr-8"
        onBlur={(event) => {
          const raw = event.target.value;
          const next = raw === '' ? null : Number(raw);
          if (next !== null && (Number.isNaN(next) || next < 0)) return;
          if (next !== (value === null ? null : Number(value))) void onSave(next);
        }}
      />
      <span className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-xs text-muted-foreground">h</span>
    </div>
  );
}

function DescriptionEditor({ task }: { task: TaskDetail }) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(task.description ?? '');
  const { save, pending } = useFieldSaver(task);

  return (
    <section>
      <div className="mb-2 flex items-center justify-between">
        <h3 className="text-sm font-semibold">Description</h3>
        {task.permissions.canEdit && !editing && (
          <Button variant="ghost" size="sm" onClick={() => setEditing(true)}>
            <Pencil aria-hidden />
            Edit
          </Button>
        )}
      </div>
      {editing ? (
        <div className="space-y-2">
          <Textarea value={text} onChange={(event) => setText(event.target.value)} rows={6} autoFocus aria-label="Description" />
          <div className="flex justify-end gap-2">
            <Button variant="outline" size="sm" onClick={() => { setText(task.description ?? ''); setEditing(false); }}>
              Cancel
            </Button>
            <Button size="sm" disabled={pending} onClick={() => void save({ description: text.trim() || null }).then(() => setEditing(false))}>
              Save
            </Button>
          </div>
        </div>
      ) : task.description ? (
        <p className="text-sm whitespace-pre-wrap">{task.description}</p>
      ) : (
        <p className="text-sm text-muted-foreground">No description.</p>
      )}
    </section>
  );
}
