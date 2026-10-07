import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { hasPermission, isSuperAdmin } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { useTeamDirectory } from '@/features/team/api';
import { applyServerErrors } from '@/lib/api/form-errors';

import { useCreateTask, useProjectOptions } from './api';
import { PRIORITY_LABELS } from './task-meta';
import { PRIORITIES, type TaskDetail } from './types';

/** Mirrors TaskRequests.CreateTask. */
const createTaskSchema = z
  .object({
    title: z.string().trim().min(1, 'Title is required').max(250),
    description: z.string().max(20000),
    departmentId: z.string().min(1, 'Department is required'),
    projectId: z.string(),
    assigneeId: z.string(),
    priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'URGENT']),
    startDate: z.string(),
    dueDate: z.string(),
    estimatedHours: z.string().refine((v) => v === '' || (Number(v) >= 0 && Number(v) <= 9999), 'Enter 0–9999 hours'),
    tags: z.string(),
  })
  .refine((v) => !v.startDate || !v.dueDate || v.dueDate >= v.startDate, {
    path: ['dueDate'],
    message: 'Due date cannot be before the start date',
  });

type CreateTaskValues = z.infer<typeof createTaskSchema>;

const SERVER_FIELDS = ['title', 'description', 'estimatedHours', 'dueDate'] as const;

interface CreateTaskDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: (task: TaskDetail) => void;
  /** Pre-select "assign to me" (My Tasks). */
  assignToMe?: boolean;
}

export function CreateTaskDialog({ open, onOpenChange, onCreated, assignToMe = false }: CreateTaskDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        {open && <CreateTaskForm assignToMe={assignToMe} onCancel={() => onOpenChange(false)} onCreated={(task) => { onOpenChange(false); onCreated(task); }} />}
      </DialogContent>
    </Dialog>
  );
}

function CreateTaskForm({ assignToMe, onCancel, onCreated }: { assignToMe: boolean; onCancel: () => void; onCreated: (task: TaskDetail) => void }) {
  const { user } = useAuth();
  const departments = useDepartments();
  const projects = useProjectOptions();
  const canAssignOthers = hasPermission(user, 'TASK_ASSIGN');
  const people = useTeamDirectory({ size: 100, sort: 'name,asc' }, canAssignOthers);
  const createTask = useCreateTask();
  const [banner, setBanner] = useState<string | null>(null);

  const { register, handleSubmit, control, setError, formState: { errors, isSubmitting } } = useForm<CreateTaskValues>({
    resolver: zodResolver(createTaskSchema),
    defaultValues: {
      title: '',
      description: '',
      departmentId: user ? String(user.department.id) : '',
      projectId: '',
      assigneeId: assignToMe && user ? String(user.id) : '',
      priority: 'MEDIUM',
      startDate: '',
      dueDate: '',
      estimatedHours: '',
      tags: '',
    },
  });

  const departmentId = Number(useWatch({ control, name: 'departmentId' }));
  // Only offer people the creator can actually assign: everyone for Super Admin, otherwise the chosen department.
  const assignable = (people.data?.content ?? []).filter((p) => isSuperAdmin(user) || p.department.id === departmentId || p.id === user?.id);

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      const created = await createTask.mutateAsync({
        title: values.title,
        description: values.description.trim() || null,
        departmentId: Number(values.departmentId),
        projectId: values.projectId ? Number(values.projectId) : null,
        assigneeId: values.assigneeId ? Number(values.assigneeId) : null,
        priority: values.priority,
        startDate: values.startDate || null,
        dueDate: values.dueDate || null,
        estimatedHours: values.estimatedHours === '' ? null : Number(values.estimatedHours),
        tags: values.tags.split(',').map((t) => t.trim()).filter(Boolean),
      });
      toast.success(`${created.code} created`);
      onCreated(created);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>New task</DialogTitle>
        <DialogDescription>Give it an owner and a due date so it shows up in workload and deadlines.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="title" label="Title" required error={errors.title?.message}>
          <Input id="title" autoFocus aria-invalid={errors.title ? true : undefined} {...register('title')} />
        </FormField>
        <FormField id="description" label="Description" error={errors.description?.message}>
          <Textarea id="description" rows={3} {...register('description')} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="departmentId" label="Department" required error={errors.departmentId?.message}>
            <Select id="departmentId" {...register('departmentId')}>
              {departments.data
                ?.filter((d) => d.status === 'ACTIVE')
                .map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
            </Select>
          </FormField>
          <FormField id="assigneeId" label="Assignee">
            <Select id="assigneeId" {...register('assigneeId')}>
              <option value="">Unassigned</option>
              {user && <option value={user.id}>Me ({user.fullName})</option>}
              {canAssignOthers &&
                assignable
                  .filter((p) => p.id !== user?.id)
                  .map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.fullName}
                    </option>
                  ))}
            </Select>
          </FormField>
          <FormField id="priority" label="Priority">
            <Select id="priority" {...register('priority')}>
              {PRIORITIES.map((p) => (
                <option key={p} value={p}>
                  {PRIORITY_LABELS[p]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="projectId" label="Project">
            <Select id="projectId" {...register('projectId')}>
              <option value="">No project</option>
              {projects.data?.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.code} · {p.name}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="startDate" label="Start date">
            <Input id="startDate" type="date" {...register('startDate')} />
          </FormField>
          <FormField id="dueDate" label="Due date" error={errors.dueDate?.message}>
            <Input id="dueDate" type="date" aria-invalid={errors.dueDate ? true : undefined} {...register('dueDate')} />
          </FormField>
          <FormField id="estimatedHours" label="Estimate (hours)" hint="Used for workload %" error={errors.estimatedHours?.message}>
            <Input id="estimatedHours" type="number" min={0} step={0.5} {...register('estimatedHours')} />
          </FormField>
          <FormField id="tags" label="Tags" hint="Comma separated">
            <Input id="tags" placeholder="client, documentation" {...register('tags')} />
          </FormField>
        </div>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          Create task
        </Button>
      </DialogFooter>
    </form>
  );
}
