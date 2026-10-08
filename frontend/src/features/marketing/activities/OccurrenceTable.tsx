import { zodResolver } from '@hookform/resolvers/zod';
import { Ban, CircleCheck, LoaderCircle, RotateCcw } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Textarea } from '@/components/ui/textarea';
import { DueBadge, TaskStatusBadge } from '@/features/tasks/TaskBadges';
import { errorMessage } from '@/lib/api/errors';
import { cn } from '@/lib/utils';

import { useOccurrenceAction, type OccurrenceItem } from './api';
import { OccurrenceStatusBadge } from './ActivityBadges';
import { formatInstantDate } from './activity-meta';

const isOpen = (o: OccurrenceItem) => o.status === 'PENDING' || o.status === 'IN_PROGRESS';

interface OccurrenceTableProps {
  occurrences: OccurrenceItem[];
  /** Show the activity column (lists across activities). */
  showActivity?: boolean;
  dimmed?: boolean;
}

/**
 * Occurrences with their due state, status and task. An occurrence with a task follows it (open the task to complete
 * it); the others can be completed, skipped or reopened here when the server allows it (`canAct`).
 */
export function OccurrenceTable({ occurrences, showActivity, dimmed }: OccurrenceTableProps) {
  const [acting, setActing] = useState<{ occurrence: OccurrenceItem; action: 'complete' | 'skip' } | null>(null);
  const reopen = useOccurrenceAction();
  const anyActions = occurrences.some((o) => o.canAct);

  async function onReopen(occurrence: OccurrenceItem) {
    try {
      await reopen.mutateAsync({ id: occurrence.id, action: 'reopen' });
      toast.success(`${occurrence.periodLabel} reopened`);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            {showActivity && <TableHead>Activity</TableHead>}
            <TableHead>Period</TableHead>
            <TableHead>Due</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Task</TableHead>
            <TableHead>Assignee</TableHead>
            <TableHead>Completed</TableHead>
            {anyActions && (
              <TableHead>
                <span className="sr-only">Actions</span>
              </TableHead>
            )}
          </TableRow>
        </TableHeader>
        <TableBody className={cn(dimmed && 'opacity-60')}>
          {occurrences.map((o) => (
            <TableRow key={o.id}>
              {showActivity && (
                <TableCell className="max-w-56">
                  <Link to={`/digital-marketing/activities/${o.activityId}`} className="block truncate font-medium hover:underline">
                    {o.activityName}
                  </Link>
                </TableCell>
              )}
              <TableCell className="text-sm whitespace-nowrap">
                {o.periodLabel}
                {o.notes && (
                  <p className="max-w-48 truncate text-xs text-muted-foreground" title={o.notes}>
                    {o.notes}
                  </p>
                )}
              </TableCell>
              <TableCell>
                <DueBadge dueDate={o.dueDate} state={o.dueState} />
              </TableCell>
              <TableCell>
                <OccurrenceStatusBadge status={o.status} />
              </TableCell>
              <TableCell className="max-w-64">
                {o.task ? (
                  <Link to={`/tasks?task=${o.task.id}`} className="group flex min-w-0 items-center gap-2 text-sm">
                    <span className="shrink-0 font-mono text-xs text-muted-foreground">{o.task.code}</span>
                    <span className="truncate group-hover:underline">{o.task.title}</span>
                    <TaskStatusBadge status={o.task.status} />
                  </Link>
                ) : (
                  <span className="text-sm text-muted-foreground">No task</span>
                )}
              </TableCell>
              <TableCell className="max-w-44">{o.assignee ? <UserCell name={o.assignee.fullName} /> : <span className="text-sm text-muted-foreground">—</span>}</TableCell>
              <TableCell className="text-sm whitespace-nowrap text-muted-foreground">
                {o.completedAt ? `${formatInstantDate(o.completedAt)}${o.completedBy ? ` · ${o.completedBy.fullName}` : ''}` : '—'}
              </TableCell>
              {anyActions && (
                <TableCell>
                  {o.canAct &&
                    (isOpen(o) ? (
                      <div className="flex gap-1">
                        <Button size="sm" variant="outline" onClick={() => setActing({ occurrence: o, action: 'complete' })}>
                          <CircleCheck aria-hidden />
                          Complete{' '}
                          <span className="sr-only">{o.periodLabel}</span>
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setActing({ occurrence: o, action: 'skip' })}>
                          <Ban aria-hidden />
                          Skip{' '}
                          <span className="sr-only">{o.periodLabel}</span>
                        </Button>
                      </div>
                    ) : (
                      <Button size="sm" variant="ghost" disabled={reopen.isPending} onClick={() => void onReopen(o)}>
                        <RotateCcw aria-hidden />
                        Reopen{' '}
                          <span className="sr-only">{o.periodLabel}</span>
                      </Button>
                    ))}
                </TableCell>
              )}
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <Dialog open={acting !== null} onOpenChange={(open) => !open && setActing(null)}>
        <DialogContent className="max-w-md">{acting && <ActionForm occurrence={acting.occurrence} action={acting.action} onDone={() => setActing(null)} />}</DialogContent>
      </Dialog>
    </>
  );
}

const notesSchema = z.object({ notes: z.string().max(1000, 'At most 1000 characters') });

function ActionForm({ occurrence, action, onDone }: { occurrence: OccurrenceItem; action: 'complete' | 'skip'; onDone: () => void }) {
  const mutation = useOccurrenceAction();
  const [banner, setBanner] = useState<string | null>(null);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<z.infer<typeof notesSchema>>({ resolver: zodResolver(notesSchema), defaultValues: { notes: '' } });
  const completing = action === 'complete';

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      await mutation.mutateAsync({ id: occurrence.id, action, notes: values.notes.trim() || null });
      toast.success(`${occurrence.activityName} · ${occurrence.periodLabel} ${completing ? 'completed' : 'skipped'}`);
      onDone();
    } catch (error) {
      setBanner(errorMessage(error));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>
          {completing ? 'Complete' : 'Skip'} {occurrence.periodLabel}
        </DialogTitle>
        <DialogDescription>{occurrence.activityName}. The next occurrence is created straight away; this one stays in the history.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="occurrence-notes" label="Notes" hint={completing ? 'Optional, e.g. what was found' : 'Optional, e.g. why it was not needed'} error={errors.notes?.message}>
          <Textarea id="occurrence-notes" rows={3} autoFocus aria-describedby="occurrence-notes-message" {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {completing ? 'Mark completed' : 'Skip this period'}
        </Button>
      </DialogFooter>
    </form>
  );
}
