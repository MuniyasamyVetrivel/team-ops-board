import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowRight, LoaderCircle } from 'lucide-react';
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
import { applyServerErrors } from '@/lib/api/form-errors';

import { useApprovalTypes, useSubmitApproval, type ApprovalDetail } from './api';
import { templateApprover } from './approval-meta';

/** Mirrors ApprovalDtos.Submit; the amount is required only for types that ask for one (checked on submit). */
const submitSchema = z.object({
  typeId: z.string().min(1, 'Choose a request type'),
  title: z.string().trim().min(1, 'Title is required').max(250),
  description: z.string().max(20000),
  amount: z.string().refine((v) => v === '' || (Number(v) >= 0 && Number(v) <= 999999999999.99), 'Enter a positive amount'),
  currency: z.string().regex(/^[A-Z]{3}$/, 'Use a 3-letter currency code'),
  dueDate: z.string(),
});

type SubmitValues = z.infer<typeof submitSchema>;

const SERVER_FIELDS = ['title', 'description', 'amount', 'currency'] as const;

export function NewApprovalDialog({ open, onOpenChange, onCreated }: { open: boolean; onOpenChange: (open: boolean) => void; onCreated: (approval: ApprovalDetail) => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        {open && (
          <NewApprovalForm
            onCancel={() => onOpenChange(false)}
            onCreated={(approval) => {
              onOpenChange(false);
              onCreated(approval);
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function NewApprovalForm({ onCancel, onCreated }: { onCancel: () => void; onCreated: (approval: ApprovalDetail) => void }) {
  const types = useApprovalTypes();
  const submit = useSubmitApproval();
  const [banner, setBanner] = useState<string | null>(null);
  const { register, handleSubmit, control, setError, formState: { errors, isSubmitting } } = useForm<SubmitValues>({
    resolver: zodResolver(submitSchema),
    defaultValues: { typeId: '', title: '', description: '', amount: '', currency: 'INR', dueDate: '' },
  });
  const typeId = Number(useWatch({ control, name: 'typeId' }));
  const type = types.data?.find((t) => t.id === typeId);

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    if (type?.requiresAmount && values.amount === '') {
      setError('amount', { type: 'required', message: `${type.name} requests need an amount` });
      return;
    }
    try {
      const created = await submit.mutateAsync({
        typeId: Number(values.typeId),
        title: values.title,
        description: values.description.trim() || null,
        amount: values.amount === '' ? null : Number(values.amount),
        currency: values.currency,
        dueDate: values.dueDate || null,
      });
      toast.success(created.status === 'APPROVED' ? `${created.code} approved` : `${created.code} submitted for approval`);
      onCreated(created);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>New request</DialogTitle>
        <DialogDescription>Your request goes through each approval step in order.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="typeId" label="Request type" required error={errors.typeId?.message} hint={type?.description ?? undefined}>
          <Select id="typeId" aria-invalid={errors.typeId ? true : undefined} {...register('typeId')}>
            <option value="">Choose a type…</option>
            {types.data
              ?.filter((t) => t.active)
              .map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
          </Select>
        </FormField>
        {type && (
          <div className="rounded-lg bg-muted/60 px-3 py-2 text-xs text-muted-foreground" aria-label="Approval steps">
            <span className="font-medium text-foreground">Approval path: </span>
            {type.steps.map((step, i) => (
              <span key={step.stepOrder}>
                {i > 0 && <ArrowRight className="mx-1 inline size-3" aria-label="then" />}
                {templateApprover(step)}
              </span>
            ))}
          </div>
        )}
        <FormField id="title" label="Title" required error={errors.title?.message}>
          <Input id="title" aria-invalid={errors.title ? true : undefined} {...register('title')} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-[1fr_6rem_1fr]">
          <FormField id="amount" label="Amount" required={type?.requiresAmount} error={errors.amount?.message}>
            <Input id="amount" type="number" min={0} step="0.01" aria-invalid={errors.amount ? true : undefined} {...register('amount')} />
          </FormField>
          <FormField id="currency" label="Currency" error={errors.currency?.message}>
            <Input id="currency" maxLength={3} {...register('currency')} />
          </FormField>
          <FormField id="dueDate" label="Needed by">
            <Input id="dueDate" type="date" {...register('dueDate')} />
          </FormField>
        </div>
        <FormField id="description" label="Details" hint="Why it is needed, links to quotes, anything the approver should know">
          <Textarea id="description" rows={4} {...register('description')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          Submit request
        </Button>
      </DialogFooter>
    </form>
  );
}
