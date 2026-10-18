import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { DetailsWithHistory } from '@/features/history/DetailsWithHistory';
import { toastDeleted } from '@/features/recycle-bin/undo-toast';
import { errorMessage, toApiError } from '@/lib/api/errors';
import { applyServerErrors } from '@/lib/api/form-errors';

import { useDeleteRate, useFinanceContext, useSaveRate, type ExchangeRate } from './api';
import { BASE_CURRENCY, formatRate, parseAmount } from './finance-meta';
import { rateSchema, type RateValues } from './finance-schemas';

const SERVER_FIELDS = ['currency', 'effectiveFrom', 'rateToAud', 'source'] as const;

const CODE_FIELDS: Record<string, (typeof SERVER_FIELDS)[number]> = {
  RATE_EXISTS: 'effectiveFrom',
  BASE_CURRENCY: 'currency',
};

interface RateFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this rate; create a new one when absent. */
  rate?: ExchangeRate;
  /** View only (no FINANCE_EDIT): the fields are shown but can't be saved. */
  readOnly?: boolean;
}

export function RateFormDialog({ open, onOpenChange, rate, readOnly = false }: RateFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        {open && (
          <DetailsWithHistory type="EXCHANGE_RATE" id={rate?.id ?? null} listClassName="mx-6 mt-4">
            <RateForm rate={rate} readOnly={readOnly} onDone={() => onOpenChange(false)} />
          </DetailsWithHistory>
        )}
      </DialogContent>
    </Dialog>
  );
}

function RateForm({ rate, readOnly, onDone }: { rate?: ExchangeRate; readOnly: boolean; onDone: () => void }) {
  const editing = rate !== undefined;
  const context = useFinanceContext();
  const save = useSaveRate(rate?.id ?? null);
  const remove = useDeleteRate();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RateValues>({
    resolver: zodResolver(rateSchema),
    defaultValues: {
      currency: rate?.currency ?? '',
      effectiveFrom: rate?.effectiveFrom ?? context.data?.today ?? '',
      rateToAud: rate ? formatRate(rate.rateToAud).replace(/,/g, '') : '',
      source: rate?.source ?? '',
    },
  });

  const [currency, rateRaw] = useWatch({ control, name: ['currency', 'rateToAud'] });
  const value = /^\d{1,10}(\.\d{1,8})?$/.test(rateRaw.trim()) ? parseAmount(rateRaw) : null;
  const code = currency.trim().toUpperCase() || 'the currency';

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      const saved = await save.mutateAsync({
        version: rate?.version,
        currency: values.currency.trim().toUpperCase(),
        effectiveFrom: values.effectiveFrom,
        rateToAud: parseAmount(values.rateToAud) ?? 0,
        source: values.source.trim() || null,
      });
      toast.success(editing ? 'Rate updated' : `${saved.currency} rate added`);
      onDone();
    } catch (error) {
      const message = applyServerErrors(error, setError, SERVER_FIELDS);
      const field = CODE_FIELDS[toApiError(error)?.code ?? ''];
      if (field) setError(field, { type: 'server', message });
      else setBanner(message);
    }
  });

  async function onDelete() {
    if (!rate) return;
    try {
      await remove.mutateAsync(rate.id);
      toastDeleted('Rate deleted', 'EXCHANGE_RATE', rate.id);
      onDone();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  const invalid = (name: keyof RateValues) => (errors[name] ? true : undefined);

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{readOnly ? `${rate?.currency ?? ''} rate` : editing ? 'Edit rate' : 'New exchange rate'}</DialogTitle>
        <DialogDescription>
          From its date until the next {code} rate. Expenses already saved keep the rate they were converted at.
        </DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <FormBanner message={banner} />
        <fieldset disabled={readOnly} className="grid gap-4 sm:grid-cols-2">
          <FormField id="rate-currency" label="Currency" required hint={`Not ${BASE_CURRENCY}: it is always 1`} error={errors.currency?.message}>
            <Input id="rate-currency" maxLength={3} className="uppercase" placeholder="INR" aria-invalid={invalid('currency')} {...register('currency')} />
          </FormField>
          <FormField id="rate-effectiveFrom" label="Applies from" required error={errors.effectiveFrom?.message}>
            <Input id="rate-effectiveFrom" type="date" aria-invalid={invalid('effectiveFrom')} {...register('effectiveFrom')} />
          </FormField>
          <FormField
            id="rate-rateToAud"
            label={`1 unit in ${BASE_CURRENCY}`}
            required
            hint={value ? `1 ${BASE_CURRENCY} = ${formatRate(Math.round((1 / value) * 1e4) / 1e4)} ${code}` : 'e.g. 0.0182 for INR'}
            error={errors.rateToAud?.message}
          >
            <Input id="rate-rateToAud" inputMode="decimal" aria-invalid={invalid('rateToAud')} {...register('rateToAud')} />
          </FormField>
          <FormField id="rate-source" label="Source" hint="Where the rate came from" error={errors.source?.message}>
            <Input id="rate-source" placeholder="e.g. RBA, 1 Oct" aria-invalid={invalid('source')} {...register('source')} />
          </FormField>
        </fieldset>
      </DialogBody>
      <DialogFooter>
        {editing && !readOnly && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete rate'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          {readOnly ? 'Close' : 'Cancel'}
        </Button>
        {!readOnly && (
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
            {editing ? 'Save changes' : 'Add rate'}
          </Button>
        )}
      </DialogFooter>
    </form>
  );
}
