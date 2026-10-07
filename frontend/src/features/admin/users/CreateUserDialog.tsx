import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle } from 'lucide-react';
import { useState } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { toast } from 'sonner';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { isSuperAdmin, ROLE_LABELS, type RoleCode } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { applyServerErrors } from '@/lib/api/form-errors';

import { useCreateUser, type UserDetail } from './api';
import { ProfileFields } from './ProfileFields';
import { createUserSchema, toProfilePayload, type CreateUserFormInput, type CreateUserFormValues } from './schemas';

const SERVER_FIELDS = ['email', 'password', 'firstName', 'lastName', 'jobTitle', 'phone', 'location', 'workingHours', 'departmentId', 'reportsToId', 'weeklyCapacityHours'] as const;

interface CreateUserDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: (user: UserDetail) => void;
}

export function CreateUserDialog({ open, onOpenChange, onCreated }: CreateUserDialogProps) {
  const { user: actor } = useAuth();
  const createUser = useCreateUser();
  const [banner, setBanner] = useState<string | null>(null);
  const roles: RoleCode[] = isSuperAdmin(actor) ? ['EMPLOYEE', 'DEPARTMENT_MANAGER', 'SUPER_ADMIN'] : ['EMPLOYEE', 'DEPARTMENT_MANAGER'];

  const form = useForm<CreateUserFormInput, unknown, CreateUserFormValues>({
    resolver: zodResolver(createUserSchema),
    defaultValues: {
      email: '', firstName: '', lastName: '', jobTitle: '', phone: '', location: '', workingHours: '',
      departmentId: '', reportsToId: '', weeklyCapacityHours: 40, password: '', role: 'EMPLOYEE',
    },
  });
  const { register, handleSubmit, reset, setError, formState: { errors, isSubmitting } } = form;

  function close(next: boolean) {
    if (!next) {
      reset();
      setBanner(null);
    }
    onOpenChange(next);
  }

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      const created = await createUser.mutateAsync({
        ...toProfilePayload(values),
        password: values.password,
        roles: [values.role],
        permissions: [],
      });
      toast.success(`${created.fullName} was added`);
      close(false);
      onCreated(created);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Add user</DialogTitle>
          <DialogDescription>They can sign in straight away with the password you set. Extra permissions can be granted afterwards.</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
          <DialogBody>
            <FormBanner message={banner} />
            <FormProvider {...form}>
              <ProfileFields />
            </FormProvider>
            <div className="mt-4 grid gap-4 border-t pt-4 sm:grid-cols-2">
              <FormField id="role" label="Role" required error={errors.role?.message}>
                <Select id="role" {...register('role')}>
                  {roles.map((role) => (
                    <option key={role} value={role}>
                      {ROLE_LABELS[role]}
                    </option>
                  ))}
                </Select>
              </FormField>
              <FormField id="password" label="Initial password" required hint="8+ characters with a letter and a number" error={errors.password?.message}>
                <Input id="password" type="password" autoComplete="new-password" aria-invalid={errors.password ? true : undefined} {...register('password')} />
              </FormField>
            </div>
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => close(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
              Add user
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
