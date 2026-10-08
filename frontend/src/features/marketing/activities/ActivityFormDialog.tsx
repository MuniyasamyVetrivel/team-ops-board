import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle, Plus, Trash2, X } from 'lucide-react';
import { useState } from 'react';
import { useFieldArray, useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { PRIORITY_LABELS } from '@/features/tasks/task-meta';
import { PRIORITIES } from '@/features/tasks/types';
import { applyServerErrors } from '@/lib/api/form-errors';
import { errorMessage } from '@/lib/api/errors';

import { useMarketingContext } from '../api';
import { FREQUENCIES, useCreateActivity, useDeleteActivity, useUpdateActivity, type ActivityDetail, type Frequency } from './api';
import { FREQUENCY_LABELS, PERIOD_NAMES, TITLE_PLACEHOLDERS } from './activity-meta';

const MAX_CHECKLIST = 30;

/** Mirrors ActivityDtos.CreateActivity / UpdateActivity. Frequency and start date are undefined when locked (disabled). */
const activitySchema = z
  .object({
    name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
    description: z.string().max(2000, 'At most 2000 characters'),
    departmentId: z.string().min(1, 'Department is required'),
    ownerId: z.string(),
    defaultAssigneeId: z.string(),
    frequency: z.enum(['DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY']).optional(),
    startDate: z.string().optional(),
    endDate: z.string(),
    dueOffsetDays: z.string().trim().refine((v) => /^\d+$/.test(v) && Number(v) <= 365, 'A whole number of days from 0 to 365'),
    generatesTasks: z.boolean(),
    taskTitleTemplate: z.string().trim().max(250, 'At most 250 characters'),
    taskPriority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'URGENT']),
    checklist: z.array(z.object({ value: z.string().trim().max(500, 'At most 500 characters') })).max(MAX_CHECKLIST),
    active: z.boolean(),
  })
  .refine((v) => v.startDate === undefined || v.startDate !== '', { path: ['startDate'], message: 'Start date is required' })
  .refine((v) => !v.endDate || !v.startDate || v.endDate >= v.startDate, { path: ['endDate'], message: 'The end date cannot be before the start date' })
  .refine((v) => !v.generatesTasks || v.taskTitleTemplate !== '', { path: ['taskTitleTemplate'], message: 'Enter the task title, or turn off task generation' });

type ActivityValues = z.infer<typeof activitySchema>;

const SERVER_FIELDS = ['name', 'description', 'departmentId', 'ownerId', 'defaultAssigneeId', 'frequency', 'startDate', 'endDate', 'dueOffsetDays', 'taskTitleTemplate'] as const;

interface ActivityFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this activity; create a new one when absent. */
  activity?: ActivityDetail;
  onSaved?: (activity: ActivityDetail) => void;
  onDeleted?: () => void;
}

export function ActivityFormDialog({ open, onOpenChange, activity, onSaved, onDeleted }: ActivityFormDialogProps) {
  // Defaults (business today, owner list) come from the marketing context, so the form waits for it.
  const context = useMarketingContext();
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        {open && !context.data && (
          <div className="space-y-3 p-6" role="status" aria-label="Loading the form">
            <Skeleton className="h-8 w-1/2" />
            <Skeleton className="h-40" />
          </div>
        )}
        {open && context.data && (
          <ActivityForm
            activity={activity}
            onCancel={() => onOpenChange(false)}
            onSaved={(saved) => {
              onOpenChange(false);
              onSaved?.(saved);
            }}
            onDeleted={() => {
              onOpenChange(false);
              onDeleted?.();
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function ActivityForm({ activity, onCancel, onSaved, onDeleted }: { activity?: ActivityDetail; onCancel: () => void; onSaved: (activity: ActivityDetail) => void; onDeleted: () => void }) {
  const { user } = useAuth();
  const context = useMarketingContext();
  const departments = useDepartments();
  const create = useCreateActivity();
  const update = useUpdateActivity(activity?.id ?? 0);
  const remove = useDeleteActivity();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const editing = activity !== undefined;
  const locked = activity?.locked ?? false;

  const { register, handleSubmit, control, setError, formState: { errors, isSubmitting } } = useForm<ActivityValues>({
    resolver: zodResolver(activitySchema),
    defaultValues: {
      name: activity?.name ?? '',
      description: activity?.description ?? '',
      departmentId: String(activity?.department.id ?? user?.department.id ?? ''),
      ownerId: String(activity ? (activity.owner?.id ?? '') : (context.data?.owners.some((o) => o.id === user?.id) ? user?.id : '')),
      defaultAssigneeId: String(activity?.defaultAssignee?.id ?? ''),
      frequency: activity?.frequency ?? 'MONTHLY',
      startDate: activity?.startDate ?? context.data?.today ?? '',
      endDate: activity?.endDate ?? '',
      dueOffsetDays: String(activity?.dueOffsetDays ?? 0),
      generatesTasks: activity ? activity.taskTitleTemplate !== null : true,
      taskTitleTemplate: activity?.taskTitleTemplate ?? '',
      taskPriority: activity?.taskPriority ?? 'MEDIUM',
      checklist: (activity?.checklist ?? []).map((value) => ({ value })),
      active: activity?.active ?? true,
    },
  });
  const checklist = useFieldArray({ control, name: 'checklist' });
  const frequency: Frequency = useWatch({ control, name: 'frequency' }) ?? activity?.frequency ?? 'MONTHLY';
  const generatesTasks = useWatch({ control, name: 'generatesTasks' });

  const owners = context.data?.owners ?? [];
  const extra = [activity?.owner, activity?.defaultAssignee].filter((u): u is NonNullable<typeof u> => Boolean(u) && !owners.some((o) => o.id === u?.id));
  const people = [...owners, ...extra];

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const body = {
      name: values.name,
      description: values.description.trim() || null,
      departmentId: Number(values.departmentId),
      ownerId: values.ownerId ? Number(values.ownerId) : null,
      defaultAssigneeId: values.defaultAssigneeId ? Number(values.defaultAssigneeId) : null,
      // Locked fields are disabled and not submitted: keep the activity's own.
      frequency: (locked ? activity?.frequency : values.frequency) ?? 'MONTHLY',
      startDate: (locked ? activity?.startDate : values.startDate) ?? '',
      endDate: values.endDate || null,
      dueOffsetDays: Number(values.dueOffsetDays),
      taskTitleTemplate: values.generatesTasks ? values.taskTitleTemplate : null,
      taskPriority: values.taskPriority,
      checklist: values.checklist.map((item) => item.value).filter((value) => value !== ''),
    };
    try {
      const saved = editing ? await update.mutateAsync({ ...body, version: activity.version, active: values.active }) : await create.mutateAsync(body);
      toast.success(editing ? 'Activity updated' : `${saved.name} created`);
      onSaved(saved);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  async function onDelete() {
    if (!activity) return;
    try {
      await remove.mutateAsync(activity.id);
      toast.success('Activity deleted');
      onDeleted();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? 'Edit activity' : 'New recurring activity'}</DialogTitle>
        <DialogDescription>{editing ? 'Changes apply to occurrences created from now on; past occurrences stay as they were.' : 'Each period gets an occurrence; completing it creates the next one.'}</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <FormBanner message={banner} />
        <FormField id="activity-name" label="Activity name" required error={errors.name?.message}>
          <Input id="activity-name" autoFocus placeholder="Monthly SEO Ranking Update" aria-invalid={errors.name ? true : undefined} aria-describedby="activity-name-message" {...register('name')} />
        </FormField>
        <FormField id="activity-description" label="Description" error={errors.description?.message}>
          <Textarea id="activity-description" rows={2} aria-invalid={errors.description ? true : undefined} {...register('description')} />
        </FormField>

        <fieldset className="space-y-3">
          <legend className="text-sm font-semibold">Schedule</legend>
          {locked && <p className="text-xs text-muted-foreground">This activity has {activity?.occurrenceCount} occurrences, so its frequency and start date are fixed.</p>}
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="activity-frequency" label="Frequency" required>
              <Select id="activity-frequency" disabled={locked} {...register('frequency')}>
                {FREQUENCIES.map((f) => (
                  <option key={f} value={f}>
                    {FREQUENCY_LABELS[f]}
                  </option>
                ))}
              </Select>
            </FormField>
            <FormField id="activity-offset" label="Due after (days)" hint={`Days after each ${PERIOD_NAMES[frequency]} starts; never after it ends`} error={errors.dueOffsetDays?.message}>
              <Input id="activity-offset" inputMode="numeric" aria-invalid={errors.dueOffsetDays ? true : undefined} aria-describedby="activity-offset-message" {...register('dueOffsetDays')} />
            </FormField>
            <FormField id="activity-start" label="Start date" required error={errors.startDate?.message}>
              <Input id="activity-start" type="date" disabled={locked} aria-invalid={errors.startDate ? true : undefined} aria-describedby="activity-start-message" {...register('startDate')} />
            </FormField>
            <FormField id="activity-end" label="End date" hint="Leave empty to keep it running" error={errors.endDate?.message}>
              <Input id="activity-end" type="date" aria-invalid={errors.endDate ? true : undefined} aria-describedby="activity-end-message" {...register('endDate')} />
            </FormField>
          </div>
        </fieldset>

        <fieldset className="space-y-3">
          <legend className="text-sm font-semibold">People</legend>
          <div className="grid gap-4 sm:grid-cols-3">
            <FormField id="activity-department" label="Department" required error={errors.departmentId?.message}>
              <Select id="activity-department" {...register('departmentId')}>
                {departments.data
                  ?.filter((d) => d.status === 'ACTIVE' || d.id === activity?.department.id)
                  .map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name}
                    </option>
                  ))}
              </Select>
            </FormField>
            <FormField id="activity-owner" label="Owner" error={errors.ownerId?.message}>
              <Select id="activity-owner" {...register('ownerId')}>
                <option value="">No owner</option>
                {people.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.fullName}
                  </option>
                ))}
              </Select>
            </FormField>
            <FormField id="activity-assignee" label="Assignee" hint="The owner when empty" error={errors.defaultAssigneeId?.message}>
              <Select id="activity-assignee" aria-describedby="activity-assignee-message" {...register('defaultAssigneeId')}>
                <option value="">Same as owner</option>
                {people.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.fullName}
                  </option>
                ))}
              </Select>
            </FormField>
          </div>
        </fieldset>

        <fieldset className="space-y-3">
          <legend className="text-sm font-semibold">Tasks</legend>
          <label className="flex items-center gap-2 text-sm">
            <Checkbox {...register('generatesTasks')} />
            Create a task for each occurrence
          </label>
          {generatesTasks ? (
            <div className="grid gap-4 sm:grid-cols-[1fr_10rem]">
              <FormField id="activity-template" label="Task title" required hint={`Placeholders: ${TITLE_PLACEHOLDERS.join(' ')}, e.g. “Update {month} keyword rankings”`} error={errors.taskTitleTemplate?.message}>
                <Input id="activity-template" placeholder="Update {month} keyword rankings" aria-invalid={errors.taskTitleTemplate ? true : undefined} aria-describedby="activity-template-message" {...register('taskTitleTemplate')} />
              </FormField>
              <FormField id="activity-priority" label="Task priority">
                <Select id="activity-priority" {...register('taskPriority')}>
                  {PRIORITIES.map((p) => (
                    <option key={p} value={p}>
                      {PRIORITY_LABELS[p]}
                    </option>
                  ))}
                </Select>
              </FormField>
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">Occurrences are completed or skipped on the activity itself.</p>
          )}
        </fieldset>

        <fieldset className="space-y-2">
          <legend className="text-sm font-semibold">Checklist</legend>
          <p className="text-xs text-muted-foreground">{generatesTasks ? 'Copied into every generated task.' : 'A reminder of the steps for each occurrence.'}</p>
          <ul className="space-y-2">
            {checklist.fields.map((field, index) => (
              <li key={field.id} className="flex items-start gap-2">
                <div className="flex-1">
                  <Input aria-label={`Checklist item ${index + 1}`} aria-invalid={errors.checklist?.[index]?.value ? true : undefined} {...register(`checklist.${index}.value`)} />
                  {errors.checklist?.[index]?.value && <p className="mt-1 text-xs text-destructive">{errors.checklist[index]?.value?.message}</p>}
                </div>
                <Button type="button" variant="ghost" size="icon" aria-label={`Remove checklist item ${index + 1}`} onClick={() => checklist.remove(index)}>
                  <X aria-hidden />
                </Button>
              </li>
            ))}
          </ul>
          <Button type="button" variant="outline" size="sm" disabled={checklist.fields.length >= MAX_CHECKLIST} onClick={() => checklist.append({ value: '' })}>
            <Plus aria-hidden />
            Add checklist item
          </Button>
        </fieldset>

        {editing && (
          <label className="flex items-center gap-2 text-sm">
            <Checkbox {...register('active')} />
            Active (creates new occurrences)
          </label>
        )}
      </DialogBody>
      <DialogFooter>
        {editing && activity.occurrenceCount === 0 && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete activity'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Create activity'}
        </Button>
      </DialogFooter>
    </form>
  );
}
