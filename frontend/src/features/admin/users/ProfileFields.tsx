import { useFormContext } from 'react-hook-form';

import { FormField } from '@/components/common/FormField';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { useDepartments } from '@/features/departments/api';
import { useTeamDirectory } from '@/features/team/api';

import type { ProfileFormInput } from './schemas';

function invalid(error: unknown) {
  return error ? true : undefined;
}

/**
 * Profile inputs shared by the create dialog and the edit tab. Must be rendered inside a react-hook-form
 * {@code <FormProvider>} whose values include the profile fields.
 * @param userId the user being edited, excluded from the reports-to list
 */
export function ProfileFields({ userId }: { userId?: number }) {
  const {
    register,
    formState: { errors },
  } = useFormContext<ProfileFormInput>();
  const departments = useDepartments();
  const people = useTeamDirectory({ size: 100, sort: 'name,asc' });
  const managers = (people.data?.content ?? []).filter((person) => person.id !== userId);

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <FormField id="firstName" label="First name" required error={errors.firstName?.message}>
        <Input id="firstName" aria-invalid={invalid(errors.firstName)} aria-describedby="firstName-message" {...register('firstName')} />
      </FormField>
      <FormField id="lastName" label="Last name" error={errors.lastName?.message}>
        <Input id="lastName" aria-invalid={invalid(errors.lastName)} {...register('lastName')} />
      </FormField>
      <FormField id="email" label="Work email" required error={errors.email?.message} className="sm:col-span-2">
        <Input id="email" type="email" autoComplete="off" aria-invalid={invalid(errors.email)} aria-describedby="email-message" {...register('email')} />
      </FormField>
      <FormField id="jobTitle" label="Job title" error={errors.jobTitle?.message}>
        <Input id="jobTitle" {...register('jobTitle')} />
      </FormField>
      <FormField id="departmentId" label="Department" required error={errors.departmentId?.message}>
        <Select id="departmentId" aria-invalid={invalid(errors.departmentId)} {...register('departmentId')}>
          <option value="">Select a department…</option>
          {departments.data?.map((department) => (
            <option key={department.id} value={department.id} disabled={department.status !== 'ACTIVE'}>
              {department.name}
              {department.status !== 'ACTIVE' ? ' (inactive)' : ''}
            </option>
          ))}
        </Select>
      </FormField>
      <FormField id="reportsToId" label="Reports to" error={errors.reportsToId?.message}>
        <Select id="reportsToId" {...register('reportsToId')}>
          <option value="">No manager</option>
          {managers.map((person) => (
            <option key={person.id} value={person.id}>
              {person.fullName}
              {person.jobTitle ? ` — ${person.jobTitle}` : ''}
            </option>
          ))}
        </Select>
      </FormField>
      <FormField
        id="weeklyCapacityHours"
        label="Weekly capacity (hours)"
        required
        hint="Used to calculate workload %"
        error={errors.weeklyCapacityHours?.message}
      >
        <Input id="weeklyCapacityHours" type="number" min={1} max={80} step={0.5} aria-invalid={invalid(errors.weeklyCapacityHours)} {...register('weeklyCapacityHours')} />
      </FormField>
      <FormField id="phone" label="Phone" error={errors.phone?.message}>
        <Input id="phone" type="tel" {...register('phone')} />
      </FormField>
      <FormField id="location" label="Location" error={errors.location?.message}>
        <Input id="location" {...register('location')} />
      </FormField>
      <FormField id="workingHours" label="Working hours" hint="e.g. 09:30 – 18:30 IST" error={errors.workingHours?.message}>
        <Input id="workingHours" {...register('workingHours')} />
      </FormField>
    </div>
  );
}
