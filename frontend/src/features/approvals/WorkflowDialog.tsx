import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useFieldArray, useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Select } from '@/components/ui/select';
import { ROLE_LABELS, type RoleCode } from '@/features/auth/permissions';
import { useTeamDirectory } from '@/features/team/api';
import { errorMessage } from '@/lib/api/errors';

import { useUpdateWorkflow, type ApprovalType } from './api';
import { APPROVER_KIND_LABELS } from './approval-meta';

/** Mirrors ApprovalDtos.UpdateWorkflow: 1–5 steps; role and user steps need their approver. */
const workflowSchema = z.object({
  steps: z
    .array(
      z
        .object({ approverKind: z.enum(['DEPARTMENT_MANAGER', 'ROLE', 'USER']), roleCode: z.string(), userId: z.string() })
        .refine((s) => s.approverKind !== 'ROLE' || s.roleCode !== '', { path: ['roleCode'], message: 'Choose a role' })
        .refine((s) => s.approverKind !== 'USER' || s.userId !== '', { path: ['userId'], message: 'Choose a person' }),
    )
    .min(1, 'At least one step is required')
    .max(5, 'At most 5 steps'),
});

type WorkflowValues = z.infer<typeof workflowSchema>;

const ROLES: RoleCode[] = ['SUPER_ADMIN', 'DEPARTMENT_MANAGER', 'EMPLOYEE'];

export function WorkflowDialog({ type, onClose }: { type: ApprovalType | null; onClose: () => void }) {
  return (
    <Dialog open={type !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-xl">{type && <WorkflowForm type={type} onDone={onClose} />}</DialogContent>
    </Dialog>
  );
}

function WorkflowForm({ type, onDone }: { type: ApprovalType; onDone: () => void }) {
  const update = useUpdateWorkflow(type.id);
  const people = useTeamDirectory({ status: 'ACTIVE', size: 100, sort: 'name,asc' });
  const [banner, setBanner] = useState<string | null>(null);
  const { register, handleSubmit, control, formState: { errors, isSubmitting } } = useForm<WorkflowValues>({
    resolver: zodResolver(workflowSchema),
    defaultValues: {
      steps: type.steps.map((s) => ({ approverKind: s.approverKind, roleCode: s.role?.code ?? '', userId: s.user ? String(s.user.id) : '' })),
    },
  });
  const { fields, append, remove } = useFieldArray({ control, name: 'steps' });
  const steps = useWatch({ control, name: 'steps' });

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      await update.mutateAsync(
        values.steps.map((s) => ({
          approverKind: s.approverKind,
          roleCode: s.approverKind === 'ROLE' ? (s.roleCode as RoleCode) : null,
          userId: s.approverKind === 'USER' ? Number(s.userId) : null,
        })),
      );
      toast.success(`${type.name} workflow saved`);
      onDone();
    } catch (error) {
      setBanner(errorMessage(error));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{type.name} workflow</DialogTitle>
        <DialogDescription>Steps run in order. Changes apply to new requests; requests already submitted keep their steps.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-3">
        <FormBanner message={banner} />
        {errors.steps?.root?.message && <p className="text-sm text-destructive">{errors.steps.root.message}</p>}
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
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          Save workflow
        </Button>
      </DialogFooter>
    </form>
  );
}
