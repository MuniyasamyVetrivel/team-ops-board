import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle } from 'lucide-react';
import { useState } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { errorMessage } from '@/lib/api/errors';

import { useUpdateWorkflow, type ApprovalType } from './api';
import { stepsSchema, stepValues, toStepInputs } from './workflow-steps';
import { WorkflowStepsFields } from './WorkflowStepsFields';

/** Mirrors ApprovalDtos.UpdateWorkflow. */
const workflowSchema = z.object({ steps: stepsSchema });

type WorkflowValues = z.infer<typeof workflowSchema>;

export function WorkflowDialog({ type, onClose }: { type: ApprovalType | null; onClose: () => void }) {
  return (
    <Dialog open={type !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-xl">{type && <WorkflowForm type={type} onDone={onClose} />}</DialogContent>
    </Dialog>
  );
}

function WorkflowForm({ type, onDone }: { type: ApprovalType; onDone: () => void }) {
  const update = useUpdateWorkflow(type.id);
  const [banner, setBanner] = useState<string | null>(null);
  const form = useForm<WorkflowValues>({
    resolver: zodResolver(workflowSchema),
    defaultValues: { steps: stepValues(type.steps) },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setBanner(null);
    try {
      await update.mutateAsync(toStepInputs(values.steps));
      toast.success(`${type.name} workflow saved`);
      onDone();
    } catch (error) {
      setBanner(errorMessage(error));
    }
  });

  return (
    <FormProvider {...form}>
      <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
        <DialogHeader>
          <DialogTitle>{type.name} workflow</DialogTitle>
          <DialogDescription>Steps run in order. Changes apply to new requests; requests already submitted keep their steps.</DialogDescription>
        </DialogHeader>
        <DialogBody className="space-y-3">
          <FormBanner message={banner} />
          <WorkflowStepsFields />
        </DialogBody>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onDone}>
            Cancel
          </Button>
          <Button type="submit" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
            Save workflow
          </Button>
        </DialogFooter>
      </form>
    </FormProvider>
  );
}
