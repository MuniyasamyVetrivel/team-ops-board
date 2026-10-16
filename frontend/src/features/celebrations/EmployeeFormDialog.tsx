import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import type { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { useMarketingContext } from '@/features/marketing/api';
import { toastDeleted } from '@/features/recycle-bin/undo-toast';
import { errorMessage } from '@/lib/api/errors';
import { applyServerErrors } from '@/lib/api/form-errors';

import { useDeleteEmployee, useSaveEmployee, type Employee } from './api';
import { EMPLOYEE_STATUS_LABELS } from './celebration-meta';
import { employeeSchema } from './celebration-schemas';
import { usePeople } from './use-people';

const SERVER_FIELDS = ['employeeCode', 'firstName', 'lastName', 'dateOfBirth', 'designation', 'reportingManager', 'dateOfJoining', 'workEmail', 'officeLocation'] as const;

type Values = z.infer<ReturnType<typeof employeeSchema>>;

interface EmployeeFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this employee; create a new one when absent. */
  employee?: Employee;
  onSaved?: (id: number) => void;
  onDeleted?: () => void;
}

export function EmployeeFormDialog({ open, onOpenChange, employee, onSaved, onDeleted }: EmployeeFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">{open && <EmployeeForm employee={employee} onDone={() => onOpenChange(false)} onSaved={onSaved} onDeleted={onDeleted} />}</DialogContent>
    </Dialog>
  );
}

function EmployeeForm({ employee, onDone, onSaved, onDeleted }: { employee?: Employee; onDone: () => void; onSaved?: (id: number) => void; onDeleted?: () => void }) {
  const { user } = useAuth();
  const context = useMarketingContext(hasPermission(user, 'MARKETING_VIEW'));
  const departments = useDepartments();
  const { people } = usePeople();
  const save = useSaveEmployee(employee?.id ?? null);
  const remove = useDeleteEmployee();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const editing = employee !== undefined;

  const { register, control, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(employeeSchema(context.data?.today ?? null)),
    defaultValues: {
      employeeCode: employee?.employeeCode ?? '',
      firstName: employee?.firstName ?? '',
      lastName: employee?.lastName ?? '',
      dateOfBirth: employee?.dateOfBirth ?? '',
      designation: employee?.designation ?? '',
      reportingManager: employee?.reportingManager ?? '',
      dateOfJoining: employee?.dateOfJoining ?? '',
      departmentId: employee?.department ? String(employee.department.id) : '',
      workEmail: employee?.workEmail ?? '',
      officeLocation: employee?.officeLocation ?? '',
      userId: employee?.user ? String(employee.user.id) : '',
      status: employee?.status ?? 'ACTIVE',
    },
  });
  const linked = useWatch({ control, name: 'userId' }) !== '';
  const users = employee?.user && !people.some((p) => p.id === employee.user?.id) ? [{ id: employee.user.id, fullName: employee.user.fullName, detail: null }, ...people] : people;
  const autofill = linked ? 'Leave empty to use the linked user’s' : undefined;

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const text = (value: string) => value.trim() || null;
    try {
      const saved = await save.mutateAsync({
        version: employee?.version,
        employeeCode: values.employeeCode.trim(),
        firstName: text(values.firstName),
        lastName: text(values.lastName),
        dateOfBirth: values.dateOfBirth,
        designation: text(values.designation),
        reportingManager: text(values.reportingManager),
        dateOfJoining: values.dateOfJoining || null,
        departmentId: values.departmentId ? Number(values.departmentId) : null,
        workEmail: text(values.workEmail),
        officeLocation: text(values.officeLocation),
        userId: values.userId ? Number(values.userId) : null,
        status: values.status,
      });
      toast.success(editing ? 'Employee updated' : `${saved.fullName} added`);
      onDone();
      onSaved?.(saved.id);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  async function onDelete() {
    if (!employee) return;
    try {
      await remove.mutateAsync(employee.id);
      toastDeleted('Employee deleted', 'EMPLOYEE', employee.id);
      onDone();
      onDeleted?.();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  const invalid = (name: keyof Values) => (errors[name] ? true : undefined);

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? 'Edit employee' : 'New employee'}</DialogTitle>
        <DialogDescription>Birthdays and work anniversaries repeat every year from these dates. Only editors see the birth year.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <FormBanner message={banner} />
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="employee-userId" label="App user" hint="Optional. Fills the name, designation, manager and department." className="sm:col-span-2">
            <Select id="employee-userId" {...register('userId')}>
              <option value="">Not an app user</option>
              {users.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.fullName}
                  {p.detail ? ` — ${p.detail}` : ''}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="employee-firstName" label="First name" required={!linked} hint={autofill} error={errors.firstName?.message}>
            <Input id="employee-firstName" autoFocus aria-invalid={invalid('firstName')} {...register('firstName')} />
          </FormField>
          <FormField id="employee-lastName" label="Last name" required={!linked} error={errors.lastName?.message}>
            <Input id="employee-lastName" aria-invalid={invalid('lastName')} {...register('lastName')} />
          </FormField>
          <FormField id="employee-employeeCode" label="Employee ID" required error={errors.employeeCode?.message}>
            <Input id="employee-employeeCode" aria-invalid={invalid('employeeCode')} {...register('employeeCode')} />
          </FormField>
          <FormField id="employee-dateOfBirth" label="Date of birth" required error={errors.dateOfBirth?.message}>
            <Input id="employee-dateOfBirth" type="date" max={context.data?.today} aria-invalid={invalid('dateOfBirth')} {...register('dateOfBirth')} />
          </FormField>
          <FormField id="employee-designation" label="Designation" required={!linked} error={errors.designation?.message}>
            <Input id="employee-designation" aria-invalid={invalid('designation')} {...register('designation')} />
          </FormField>
          <FormField id="employee-reportingManager" label="Reporting manager" required={!linked} error={errors.reportingManager?.message}>
            <Input id="employee-reportingManager" aria-invalid={invalid('reportingManager')} {...register('reportingManager')} />
          </FormField>
          <FormField id="employee-dateOfJoining" label="Date of joining" hint="Needed for work anniversaries" error={errors.dateOfJoining?.message}>
            <Input id="employee-dateOfJoining" type="date" aria-invalid={invalid('dateOfJoining')} {...register('dateOfJoining')} />
          </FormField>
          <FormField id="employee-departmentId" label="Department">
            <Select id="employee-departmentId" {...register('departmentId')}>
              <option value="">{linked ? 'The linked user’s department' : 'No department'}</option>
              {departments.data
                ?.filter((d) => d.status === 'ACTIVE')
                .map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
            </Select>
          </FormField>
          <FormField id="employee-workEmail" label="Work email" error={errors.workEmail?.message}>
            <Input id="employee-workEmail" type="email" aria-invalid={invalid('workEmail')} {...register('workEmail')} />
          </FormField>
          <FormField id="employee-officeLocation" label="Office location" error={errors.officeLocation?.message}>
            <Input id="employee-officeLocation" aria-invalid={invalid('officeLocation')} {...register('officeLocation')} />
          </FormField>
          <FormField id="employee-status" label="Status" hint="People who left stop appearing on the calendar">
            <Select id="employee-status" {...register('status')}>
              {(['ACTIVE', 'LEFT'] as const).map((s) => (
                <option key={s} value={s}>
                  {EMPLOYEE_STATUS_LABELS[s]}
                </option>
              ))}
            </Select>
          </FormField>
        </div>
      </DialogBody>
      <DialogFooter>
        {editing && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete employee'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Add employee'}
        </Button>
      </DialogFooter>
    </form>
  );
}
