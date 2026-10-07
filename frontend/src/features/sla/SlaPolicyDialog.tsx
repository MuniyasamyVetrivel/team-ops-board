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
import { PRIORITY_LABELS } from '@/features/tasks/task-meta';
import { formatMinutes } from '@/features/tickets/ticket-meta';
import { applyServerErrors } from '@/lib/api/form-errors';

import { useUpdateSlaPolicy, type SlaPolicy } from './api';

/** Mirrors SlaDtos.UpdatePolicy: 1 minute to 30 days, resolution not shorter than first response. */
const MAX_MINUTES = 43_200;
const minutes = z.coerce.number<string>().int('Whole minutes only').min(1, 'At least 1 minute').max(MAX_MINUTES, 'At most 30 days');
const policySchema = z
  .object({ firstResponseMinutes: minutes, resolutionMinutes: minutes })
  .refine((v) => v.resolutionMinutes >= v.firstResponseMinutes, {
    path: ['resolutionMinutes'],
    message: 'Resolution cannot be shorter than first response',
  });

type PolicyInput = z.input<typeof policySchema>;
type PolicyValues = z.output<typeof policySchema>;

const SERVER_FIELDS = ['firstResponseMinutes', 'resolutionMinutes'] as const;

export function SlaPolicyDialog({ policy, onClose }: { policy: SlaPolicy | null; onClose: () => void }) {
  return (
    <Dialog open={policy !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-md">{policy && <PolicyForm policy={policy} onDone={onClose} />}</DialogContent>
    </Dialog>
  );
}

function PolicyForm({ policy, onDone }: { policy: SlaPolicy; onDone: () => void }) {
  const update = useUpdateSlaPolicy(policy.id);
  const [banner, setBanner] = useState<string | null>(null);
  const { register, handleSubmit, control, setError, formState: { errors, isSubmitting } } = useForm<PolicyInput, unknown, PolicyValues>({
    resolver: zodResolver(policySchema),
    defaultValues: { firstResponseMinutes: String(policy.firstResponseMinutes), resolutionMinutes: String(policy.resolutionMinutes) },
  });
  const first = Number(useWatch({ control, name: 'firstResponseMinutes' }));
  const resolution = Number(useWatch({ control, name: 'resolutionMinutes' }));

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      await update.mutateAsync({ version: policy.version, ...values });
      toast.success(`${PRIORITY_LABELS[policy.priority]} SLA updated`);
      onDone();
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{PRIORITY_LABELS[policy.priority]} SLA</DialogTitle>
        <DialogDescription>New targets apply to tickets raised from now on. Existing tickets keep their due times.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="firstResponseMinutes" label="First response (minutes)" required error={errors.firstResponseMinutes?.message}
          hint={Number.isFinite(first) && first > 0 ? `= ${formatMinutes(first)}` : undefined}>
          <Input id="firstResponseMinutes" type="number" min={1} max={MAX_MINUTES} aria-invalid={errors.firstResponseMinutes ? true : undefined} {...register('firstResponseMinutes')} />
        </FormField>
        <FormField id="resolutionMinutes" label="Resolution (minutes)" required error={errors.resolutionMinutes?.message}
          hint={Number.isFinite(resolution) && resolution > 0 ? `= ${formatMinutes(resolution)}` : undefined}>
          <Input id="resolutionMinutes" type="number" min={1} max={MAX_MINUTES} aria-invalid={errors.resolutionMinutes ? true : undefined} {...register('resolutionMinutes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          Save targets
        </Button>
      </DialogFooter>
    </form>
  );
}
