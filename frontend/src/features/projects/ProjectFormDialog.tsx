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
import { isSuperAdmin } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { useTeamDirectory } from '@/features/team/api';
import { applyServerErrors } from '@/lib/api/form-errors';

import { PROJECT_STATUSES, toUpdateProjectInput, useCreateProject, useUpdateProject, type ProjectDetail } from './api';
import { PROJECT_STATUS_LABELS } from './project-meta';

/** Mirrors ProjectDtos.CreateProject / UpdateProject. */
const projectSchema = z
  .object({
    name: z.string().trim().min(1, 'Name is required').max(200),
    description: z.string().max(20000),
    departmentId: z.string().min(1, 'Department is required'),
    ownerId: z.string(),
    startDate: z.string(),
    endDate: z.string(),
    status: z.enum(['PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED']),
    progressOverride: z.string().refine((v) => v === '' || (Number.isInteger(Number(v)) && Number(v) >= 0 && Number(v) <= 100), 'Enter a whole number from 0 to 100'),
  })
  .refine((v) => !v.startDate || !v.endDate || v.endDate >= v.startDate, { path: ['endDate'], message: 'The end date cannot be before the start date' });

type ProjectValues = z.infer<typeof projectSchema>;

const SERVER_FIELDS = ['name', 'description', 'departmentId', 'endDate', 'progressOverride'] as const;

interface ProjectFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this project; create a new one when absent. */
  project?: ProjectDetail;
  onSaved?: (project: ProjectDetail) => void;
}

export function ProjectFormDialog({ open, onOpenChange, project, onSaved }: ProjectFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        {open && (
          <ProjectForm
            project={project}
            onCancel={() => onOpenChange(false)}
            onSaved={(saved) => {
              onOpenChange(false);
              onSaved?.(saved);
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function ProjectForm({ project, onCancel, onSaved }: { project?: ProjectDetail; onCancel: () => void; onSaved: (project: ProjectDetail) => void }) {
  const { user } = useAuth();
  const departments = useDepartments();
  const people = useTeamDirectory({ status: 'ACTIVE', size: 100, sort: 'name,asc' });
  const create = useCreateProject();
  const update = useUpdateProject(project?.id ?? 0);
  const [banner, setBanner] = useState<string | null>(null);
  const editing = project !== undefined;

  const { register, handleSubmit, control, setError, formState: { errors, isSubmitting } } = useForm<ProjectValues>({
    resolver: zodResolver(projectSchema),
    defaultValues: {
      name: project?.name ?? '',
      description: project?.description ?? '',
      departmentId: String(project?.department.id ?? user?.department.id ?? ''),
      ownerId: String(project?.owner?.id ?? user?.id ?? ''),
      startDate: project?.startDate ?? '',
      endDate: project?.endDate ?? '',
      status: project?.status ?? 'PLANNING',
      progressOverride: project?.progressOverride === null || project?.progressOverride === undefined ? '' : String(project.progressOverride),
    },
  });
  const departmentId = Number(useWatch({ control, name: 'departmentId' }));
  const owners = (people.data?.content ?? []).filter((p) => isSuperAdmin(user) || p.department.id === departmentId || p.id === user?.id || p.id === project?.owner?.id);

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const base = {
      name: values.name,
      description: values.description.trim() || null,
      departmentId: Number(values.departmentId),
      ownerId: values.ownerId ? Number(values.ownerId) : null,
      startDate: values.startDate || null,
      endDate: values.endDate || null,
    };
    try {
      const saved = editing
        ? await update.mutateAsync(toUpdateProjectInput(project, { ...base, status: values.status, progressOverride: values.progressOverride === '' ? null : Number(values.progressOverride) }))
        : await create.mutateAsync(base);
      toast.success(editing ? 'Project updated' : `${saved.code} created`);
      onSaved(saved);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? `Edit ${project.code}` : 'New project'}</DialogTitle>
        <DialogDescription>{editing ? 'Changes are saved for everyone on the project.' : 'Progress is calculated from the project’s tasks.'}</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="name" label="Name" required error={errors.name?.message}>
          <Input id="name" autoFocus aria-invalid={errors.name ? true : undefined} {...register('name')} />
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
          <FormField id="ownerId" label="Owner">
            <Select id="ownerId" {...register('ownerId')}>
              <option value="">No owner</option>
              {owners.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.fullName}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="startDate" label="Start date">
            <Input id="startDate" type="date" {...register('startDate')} />
          </FormField>
          <FormField id="endDate" label="End date" error={errors.endDate?.message}>
            <Input id="endDate" type="date" aria-invalid={errors.endDate ? true : undefined} {...register('endDate')} />
          </FormField>
          {editing && (
            <>
              <FormField id="status" label="Status">
                <Select id="status" {...register('status')}>
                  {PROJECT_STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {PROJECT_STATUS_LABELS[s]}
                    </option>
                  ))}
                </Select>
              </FormField>
              <FormField id="progressOverride" label="Progress override (%)" hint="Leave empty to use task progress" error={errors.progressOverride?.message}>
                <Input id="progressOverride" type="number" min={0} max={100} aria-invalid={errors.progressOverride ? true : undefined} {...register('progressOverride')} />
              </FormField>
            </>
          )}
        </div>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Create project'}
        </Button>
      </DialogFooter>
    </form>
  );
}
