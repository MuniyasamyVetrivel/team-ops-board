import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle } from 'lucide-react';
import { useState } from 'react';
import { FormProvider, useForm, useFormContext } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { applyServerErrors } from '@/lib/api/form-errors';

import { useCreateApprovalType, useUpdateApprovalType, type ApprovalType } from './api';
import { suggestCode } from './approval-meta';
import { DEFAULT_STEP, stepsSchema, toStepInputs } from './workflow-steps';
import { WorkflowStepsFields } from './WorkflowStepsFields';

/** Mirrors ApprovalDtos.CreateType / UpdateType. */
const detailsShape = {
  name: z.string().trim().min(1, 'Name is required').max(100, 'Name is too long'),
  description: z.string().max(500, 'Description is too long'),
  requiresAmount: z.boolean(),
};

const createSchema = z.object({
  code: z
    .string()
    .trim()
    .regex(/^[A-Z][A-Z0-9_]{1,39}$/, 'Use 2–40 capital letters, digits or underscores, starting with a letter'),
  ...detailsShape,
  steps: stepsSchema,
});

const editSchema = z.object({ ...detailsShape, active: z.boolean() });

type CreateValues = z.infer<typeof createSchema>;
type EditValues = z.infer<typeof editSchema>;

/** `type === undefined` creates a new approval type; otherwise edits that type's details. */
export function ApprovalTypeDialog({ open, type, onClose }: { open: boolean; type?: ApprovalType; onClose: () => void }) {
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-w-xl">
        {open && (type ? <EditTypeForm type={type} onDone={onClose} /> : <CreateTypeForm onDone={onClose} />)}
      </DialogContent>
    </Dialog>
  );
}

function CreateTypeForm({ onDone }: { onDone: () => void }) {
  const create = useCreateApprovalType();
  const [banner, setBanner] = useState<string | null>(null);
  const form = useForm<CreateValues>({
    resolver: zodResolver(createSchema),
    defaultValues: { code: '', name: '', description: '', requiresAmount: false, steps: [DEFAULT_STEP] },
  });
  const { register, formState: { errors, isSubmitting, dirtyFields } } = form;

  const onSubmit = form.handleSubmit(async (values) => {
    setBanner(null);
    try {
      const created = await create.mutateAsync({
        code: values.code,
        name: values.name,
        description: values.description.trim() || null,
        requiresAmount: values.requiresAmount,
        steps: toStepInputs(values.steps),
      });
      toast.success(`${created.name} added`);
      onDone();
    } catch (error) {
      setBanner(applyServerErrors(error, form.setError, ['code', 'name', 'description']));
    }
  });

  return (
    <FormProvider {...form}>
      <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
        <DialogHeader>
          <DialogTitle>New approval type</DialogTitle>
          <DialogDescription>People choose the type when they raise a request; its workflow decides who approves it.</DialogDescription>
        </DialogHeader>
        <DialogBody className="space-y-4">
          <FormBanner message={banner} />
          <FormField id="type-name" label="Name" required error={errors.name?.message}>
            <Input
              id="type-name"
              autoFocus
              aria-invalid={errors.name ? true : undefined}
              aria-describedby="type-name-message"
              {...register('name', {
                onChange: (event: { target: { value: string } }) => {
                  if (!dirtyFields.code) form.setValue('code', suggestCode(event.target.value));
                },
              })}
            />
          </FormField>
          <FormField id="type-code" label="Code" required error={errors.code?.message} hint="Permanent; used in reports and imports.">
            <Input id="type-code" className="font-mono" aria-invalid={errors.code ? true : undefined} aria-describedby="type-code-message" {...register('code')} />
          </FormField>
          <DetailsFields />
          <div className="space-y-2">
            <p className="text-sm font-medium">Workflow</p>
            <WorkflowStepsFields />
          </div>
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onDone}>
            Cancel
          </Button>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
            Add approval type
          </Button>
        </DialogFooter>
      </form>
    </FormProvider>
  );
}

function EditTypeForm({ type, onDone }: { type: ApprovalType; onDone: () => void }) {
  const update = useUpdateApprovalType(type.id);
  const [banner, setBanner] = useState<string | null>(null);
  const form = useForm<EditValues>({
    resolver: zodResolver(editSchema),
    defaultValues: { name: type.name, description: type.description ?? '', requiresAmount: type.requiresAmount, active: type.active },
  });
  const { register, formState: { isSubmitting } } = form;

  const onSubmit = form.handleSubmit(async (values) => {
    setBanner(null);
    try {
      const saved = await update.mutateAsync({
        version: type.version,
        name: values.name,
        description: values.description.trim() || null,
        requiresAmount: values.requiresAmount,
        active: values.active,
      });
      toast.success(`${saved.name} saved`);
      onDone();
    } catch (error) {
      setBanner(applyServerErrors(error, form.setError, ['name', 'description']));
    }
  });

  return (
    <FormProvider {...form}>
      <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
        <DialogHeader>
          <DialogTitle>Edit {type.name}</DialogTitle>
          <DialogDescription>
            Code <span className="font-mono">{type.code}</span>. Changes apply to new requests.
          </DialogDescription>
        </DialogHeader>
        <DialogBody className="space-y-4">
          <FormBanner message={banner} />
          <FormField id="type-name" label="Name" required error={form.formState.errors.name?.message}>
            <Input id="type-name" aria-invalid={form.formState.errors.name ? true : undefined} aria-describedby="type-name-message" {...register('name')} />
          </FormField>
          <DetailsFields />
          <label className="flex items-start gap-2 text-sm">
            <Checkbox className="mt-0.5" {...register('active')} />
            <span>
              Active
              <span className="block text-xs text-muted-foreground">Inactive types can't be chosen for new requests; existing requests keep them.</span>
            </span>
          </label>
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onDone}>
            Cancel
          </Button>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
            Save
          </Button>
        </DialogFooter>
      </form>
    </FormProvider>
  );
}

/** Description and the amount rule, shared by both forms. */
function DetailsFields() {
  const { register, formState: { errors } } = useFormContext<DetailsValues>();
  return (
    <>
      <FormField id="type-description" label="Description" error={errors.description?.message}>
        <Textarea id="type-description" rows={2} aria-invalid={errors.description ? true : undefined} aria-describedby="type-description-message" {...register('description')} />
      </FormField>
      <label className="flex items-start gap-2 text-sm">
        <Checkbox className="mt-0.5" {...register('requiresAmount')} />
        <span>
          Needs an amount
          <span className="block text-xs text-muted-foreground">Requesters must enter an amount (purchases, expenses).</span>
        </span>
      </label>
    </>
  );
}

interface DetailsValues {
  description: string;
  requiresAmount: boolean;
}
