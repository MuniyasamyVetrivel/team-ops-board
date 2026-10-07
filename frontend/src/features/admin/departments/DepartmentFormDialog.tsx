import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useCreateDepartment, useUpdateDepartment, type DepartmentDetail, type DepartmentStatus } from '@/features/departments/api';
import { useTeamDirectory } from '@/features/team/api';
import { applyServerErrors } from '@/lib/api/form-errors';

/** Mirrors CreateDepartmentRequest / UpdateDepartmentRequest. */
const departmentSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(100),
  code: z
    .string()
    .trim()
    .regex(/^[A-Za-z0-9_]{2,40}$/, '2–40 letters, digits or underscores'),
  description: z.string().trim().max(500, 'At most 500 characters'),
  managerId: z.string(),
  status: z.enum(['ACTIVE', 'INACTIVE']),
});

type DepartmentFormValues = z.infer<typeof departmentSchema>;

const SERVER_FIELDS = ['name', 'code', 'description', 'managerId', 'status'] as const;

interface DepartmentFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit mode when provided; create mode otherwise. */
  department?: DepartmentDetail;
  onSaved?: (department: DepartmentDetail) => void;
}

export function DepartmentFormDialog({ open, onOpenChange, department, onSaved }: DepartmentFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        {/* Remount on open so defaults reflect the current record. */}
        {open && <DepartmentForm department={department} onDone={(saved) => { onOpenChange(false); onSaved?.(saved); }} onCancel={() => onOpenChange(false)} />}
      </DialogContent>
    </Dialog>
  );
}

function DepartmentForm({ department, onDone, onCancel }: { department?: DepartmentDetail; onDone: (d: DepartmentDetail) => void; onCancel: () => void }) {
  const isEdit = department !== undefined;
  const create = useCreateDepartment();
  const update = useUpdateDepartment(department?.id ?? 0);
  const people = useTeamDirectory({ size: 100, sort: 'name,asc' });
  const [banner, setBanner] = useState<string | null>(null);

  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<DepartmentFormValues>({
    resolver: zodResolver(departmentSchema),
    defaultValues: {
      name: department?.name ?? '',
      code: department?.code ?? '',
      description: department?.description ?? '',
      managerId: department?.manager ? String(department.manager.id) : '',
      status: department?.status ?? 'ACTIVE',
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const managerId = values.managerId ? Number(values.managerId) : null;
    try {
      const saved = isEdit
        ? await update.mutateAsync({ name: values.name, description: values.description || null, managerId, status: values.status as DepartmentStatus })
        : await create.mutateAsync({ name: values.name, code: values.code, description: values.description || null, managerId });
      toast.success(isEdit ? 'Department saved' : `${saved.name} was created`);
      onDone(saved);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  // A disabled current manager is still listed so the form does not silently drop them.
  const managers = (people.data?.content ?? []).filter((p) => p.status === 'ACTIVE');

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{isEdit ? `Edit ${department.name}` : 'New department'}</DialogTitle>
        <DialogDescription>
          {isEdit ? 'The code cannot be changed after creation.' : 'Departments group people for workload, reporting and access.'}
        </DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <div className="grid gap-4 sm:grid-cols-[1fr_10rem]">
          <FormField id="name" label="Name" required error={errors.name?.message}>
            <Input id="name" aria-invalid={errors.name ? true : undefined} {...register('name')} />
          </FormField>
          <FormField id="code" label="Code" required error={errors.code?.message}>
            <Input id="code" className="uppercase" disabled={isEdit} aria-invalid={errors.code ? true : undefined} {...register('code')} />
          </FormField>
        </div>
        <FormField id="description" label="Description" error={errors.description?.message}>
          <Textarea id="description" rows={3} {...register('description')} />
        </FormField>
        <FormField id="managerId" label="Department manager" hint="Usually someone with the Department Manager role" error={errors.managerId?.message}>
          <Select id="managerId" {...register('managerId')}>
            <option value="">No manager</option>
            {department?.manager && department.manager.status !== 'ACTIVE' && (
              <option value={department.manager.id}>{department.manager.fullName} (disabled)</option>
            )}
            {managers.map((person) => (
              <option key={person.id} value={person.id}>
                {person.fullName} — {person.department.name}
              </option>
            ))}
          </Select>
        </FormField>
        {isEdit && (
          <FormField id="status" label="Status" hint="A department with active users cannot be deactivated" error={errors.status?.message}>
            <Select id="status" {...register('status')}>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </Select>
          </FormField>
        )}
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {isEdit ? 'Save' : 'Create department'}
        </Button>
      </DialogFooter>
    </form>
  );
}
