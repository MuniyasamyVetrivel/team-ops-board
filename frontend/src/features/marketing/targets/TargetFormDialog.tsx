import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle, Trash2 } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useAuth } from '@/features/auth/use-auth';
import { applyServerErrors } from '@/lib/api/form-errors';
import { errorMessage } from '@/lib/api/errors';

import { useMarketingContext } from '../api';
import { formatMetric, periodLabel } from '../marketing-format';
import { useCreateTarget, useDeleteTarget, useUpdateTarget, type TargetItem, type TypeRef } from './api';
import { ACTUAL_SOURCE_LABELS, parseValue, UNIT_FORMATS, valueField } from './target-meta';

const SERVER_FIELDS = ['targetValue', 'actualValue', 'ownerId', 'notes'] as const;

interface TargetFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this target… */
  target?: TargetItem;
  /** …or create one for this type and month. */
  type?: TypeRef;
  month: number;
  year: number;
  /** Whether the month is in the future (no actual can be entered). */
  planned: boolean;
}

export function TargetFormDialog({ open, onOpenChange, ...props }: TargetFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">{open && <TargetForm {...props} onDone={() => onOpenChange(false)} />}</DialogContent>
    </Dialog>
  );
}

function TargetForm({ target, type: newType, month, year, planned, onDone }: Omit<TargetFormDialogProps, 'open' | 'onOpenChange'> & { onDone: () => void }) {
  const { user } = useAuth();
  const context = useMarketingContext();
  const create = useCreateTarget();
  const update = useUpdateTarget(target?.id ?? 0);
  const remove = useDeleteTarget();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const type = (target?.type ?? newType)!;
  const editing = target !== undefined;
  // The actual is typed in only when the server does not compute it, and never for a planned month.
  const actualEditable = editing ? target.actualEditable : !type.automatic && !planned;

  const schema = useMemo(
    () =>
      z.object({
        targetValue: valueField(type.unit, { required: true }),
        actualValue: valueField(type.unit, { required: false }),
        ownerId: z.string(),
        notes: z.string().max(1000, 'At most 1000 characters'),
      }),
    [type.unit],
  );
  type Values = z.infer<typeof schema>;

  const owners = context.data?.owners ?? [];
  const ownerOptions = target?.owner && !owners.some((o) => o.id === target.owner?.id) ? [...owners, target.owner] : owners;
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      targetValue: target?.targetValue.toString() ?? '',
      actualValue: target && target.actualOrigin === 'MANUAL' && target.actual !== null ? target.actual.toString() : '',
      ownerId: String(target ? (target.owner?.id ?? '') : owners.some((o) => o.id === user?.id) ? user?.id : ''),
      notes: target?.notes ?? '',
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const body = {
      targetValue: parseValue(values.targetValue)!,
      // Not editable here: send a kept hand-entered actual back unchanged (null would clear it).
      actualValue: actualEditable ? parseValue(values.actualValue) : target?.actualOrigin === 'MANUAL' ? target.actual : null,
      ownerId: values.ownerId ? Number(values.ownerId) : null,
      departmentId: target?.department.id ?? user?.department.id ?? 0,
      notes: values.notes.trim() || null,
    };
    try {
      if (editing) {
        await update.mutateAsync({ ...body, version: target.version });
        toast.success(`${type.name} target updated`);
      } else {
        await create.mutateAsync({ ...body, typeId: type.id, month, year });
        toast.success(`${type.name} target set for ${periodLabel(month, year)}`);
      }
      onDone();
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  async function onDelete() {
    if (!target) return;
    try {
      await remove.mutateAsync(target.id);
      toast.success(`${type.name} target removed`);
      onDone();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  const format = UNIT_FORMATS[type.unit];
  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>
          {type.name} · {periodLabel(month, year)}
        </DialogTitle>
        <DialogDescription>{editing ? 'Changes are audited with the old and new values.' : 'One target per type and month; earlier months are kept as they were.'}</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="targetValue" label="Target" required error={errors.targetValue?.message}>
          <Input id="targetValue" inputMode="decimal" autoFocus aria-invalid={errors.targetValue ? true : undefined} aria-describedby="targetValue-message" {...register('targetValue')} />
        </FormField>
        {actualEditable ? (
          <FormField id="actualValue" label="Actual" hint="Leave empty if nothing has been achieved yet" error={errors.actualValue?.message}>
            <Input id="actualValue" inputMode="decimal" aria-invalid={errors.actualValue ? true : undefined} aria-describedby="actualValue-message" {...register('actualValue')} />
          </FormField>
        ) : (
          <div className="rounded-lg border bg-muted/40 px-3 py-2.5 text-sm">
            <p className="font-medium">Actual</p>
            <p className="text-muted-foreground">
              {planned
                ? 'Entered once the month has started.'
                : target?.actualOrigin === 'MANUAL'
                  ? `${formatMetric(target.actual, format)}, recorded by hand before this became automatic (kept as it was).`
                  : `Calculated automatically: ${ACTUAL_SOURCE_LABELS[type.actualSource].toLowerCase()}${target ? ` (${formatMetric(target.actual, format)} so far)` : ''}.`}
            </p>
          </div>
        )}
        <FormField id="ownerId" label="Owner">
          <Select id="ownerId" {...register('ownerId')}>
            <option value="">No owner</option>
            {ownerOptions.map((owner) => (
              <option key={owner.id} value={owner.id}>
                {owner.fullName}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField id="notes" label="Notes" error={errors.notes?.message}>
          <Textarea id="notes" rows={2} aria-invalid={errors.notes ? true : undefined} aria-describedby="notes-message" {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm remove' : 'Remove target'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Set target'}
        </Button>
      </DialogFooter>
    </form>
  );
}
