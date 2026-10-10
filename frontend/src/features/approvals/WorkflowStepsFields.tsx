import { Plus, Trash2 } from 'lucide-react';
import { useFieldArray, useFormContext, useWatch } from 'react-hook-form';

import { Button } from '@/components/ui/button';
import { Select } from '@/components/ui/select';
import { ROLE_LABELS, type RoleCode } from '@/features/auth/permissions';
import { useTeamDirectory } from '@/features/team/api';

import { APPROVER_KIND_LABELS } from './approval-meta';
import type { StepsFormValues } from './workflow-steps';

const ROLES: RoleCode[] = ['SUPER_ADMIN', 'DEPARTMENT_MANAGER', 'EMPLOYEE'];

/** The ordered approver steps of a workflow, shared by the workflow editor and the new approval type form. */
export function WorkflowStepsFields() {
  const { register, control, formState: { errors } } = useFormContext<StepsFormValues>();
  const people = useTeamDirectory({ status: 'ACTIVE', size: 100, sort: 'name,asc' });
  const { fields, append, remove } = useFieldArray({ control, name: 'steps' });
  const steps = useWatch({ control, name: 'steps' });

  return (
    <div className="space-y-3">
      {errors.steps?.root?.message && <p className="text-sm text-destructive">{errors.steps.root.message}</p>}
      {errors.steps?.message && <p className="text-sm text-destructive">{errors.steps.message}</p>}
      <ol className="space-y-3">
        {fields.map((field, index) => {
          const kind = steps?.[index]?.approverKind;
          const stepErrors = errors.steps?.[index];
          return (
            <li key={field.id} className="flex flex-wrap items-start gap-2 rounded-lg border p-3">
              <span className="mt-2 w-14 text-sm text-muted-foreground">Step {index + 1}</span>
              <div className="min-w-48 flex-1">
                <Select aria-label={`Step ${index + 1} approver`} {...register(`steps.${index}.approverKind`)}>
                  {(Object.keys(APPROVER_KIND_LABELS) as (keyof typeof APPROVER_KIND_LABELS)[]).map((k) => (
                    <option key={k} value={k}>
                      {APPROVER_KIND_LABELS[k]}
                    </option>
                  ))}
                </Select>
              </div>
              {kind === 'ROLE' && (
                <div className="min-w-40 flex-1">
                  <Select aria-label={`Step ${index + 1} role`} aria-invalid={stepErrors?.roleCode ? true : undefined} {...register(`steps.${index}.roleCode`)}>
                    <option value="">Choose a role…</option>
                    {ROLES.map((r) => (
                      <option key={r} value={r}>
                        {ROLE_LABELS[r]}
                      </option>
                    ))}
                  </Select>
                  {stepErrors?.roleCode && <p className="mt-1 text-sm text-destructive">{stepErrors.roleCode.message}</p>}
                </div>
              )}
              {kind === 'USER' && (
                <div className="min-w-40 flex-1">
                  <Select aria-label={`Step ${index + 1} person`} aria-invalid={stepErrors?.userId ? true : undefined} {...register(`steps.${index}.userId`)}>
                    <option value="">Choose a person…</option>
                    {people.data?.content.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.fullName}
                      </option>
                    ))}
                  </Select>
                  {stepErrors?.userId && <p className="mt-1 text-sm text-destructive">{stepErrors.userId.message}</p>}
                </div>
              )}
              <Button type="button" variant="ghost" size="icon" className="size-9" aria-label={`Remove step ${index + 1}`} disabled={fields.length === 1} onClick={() => remove(index)}>
                <Trash2 />
              </Button>
            </li>
          );
        })}
      </ol>
      <Button type="button" variant="outline" size="sm" disabled={fields.length >= 5} onClick={() => append({ approverKind: 'ROLE', roleCode: 'SUPER_ADMIN', userId: '' })}>
        <Plus aria-hidden />
        Add step
      </Button>
    </div>
  );
}
