import { zodResolver } from '@hookform/resolvers/zod';
import { EyeOff, LoaderCircle, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { canSeeFinance } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { toastDeleted } from '@/features/recycle-bin/undo-toast';
import { errorMessage } from '@/lib/api/errors';
import { applyServerErrors } from '@/lib/api/form-errors';
import { formatLocalDate } from '@/lib/format';

import { useDeleteContract, useSaveContract, useVendors, type Contract } from './api';
import { noticeByFor } from './vendor-meta';
import { contractSchema, MAX_NOTICE_DAYS, type ContractValues } from './vendor-schemas';

const SERVER_FIELDS = ['vendorId', 'title', 'startDate', 'endDate', 'value', 'currency', 'renewalDate', 'noticePeriodDays', 'notes'] as const;

interface ContractFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this contract; create a new one when absent. */
  contract?: Contract;
  /** Preselected vendor for a new contract (from the vendor page). */
  vendorId?: number;
  onSaved?: (id: number) => void;
  onDeleted?: () => void;
}

export function ContractFormDialog({ open, onOpenChange, ...props }: ContractFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">{open && <ContractForm {...props} onDone={() => onOpenChange(false)} />}</DialogContent>
    </Dialog>
  );
}

function ContractForm({ contract, vendorId, onSaved, onDeleted, onDone }: Omit<ContractFormDialogProps, 'open' | 'onOpenChange'> & { onDone: () => void }) {
  const { user } = useAuth();
  const editing = contract !== undefined;
  // The server hides the value from people without FINANCE_VIEW and keeps it when they save.
  const valueHidden = contract?.valueHidden ?? false;
  const vendors = useVendors({ size: 100, sort: 'name,asc' });
  const save = useSaveContract(contract?.id ?? null);
  const remove = useDeleteContract();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ContractValues>({
    resolver: zodResolver(contractSchema),
    defaultValues: {
      vendorId: String(contract?.vendor.id ?? vendorId ?? ''),
      title: contract?.title ?? '',
      startDate: contract?.startDate ?? '',
      endDate: contract?.endDate ?? '',
      value: contract?.value != null ? contract.value.toFixed(2) : '',
      currency: contract?.currency ?? 'INR',
      renewalDate: contract?.renewalDate ?? '',
      autoRenew: contract?.autoRenew ?? false,
      noticePeriodDays: contract?.noticePeriodDays != null ? String(contract.noticePeriodDays) : '',
      notes: contract?.notes ?? '',
    },
  });

  const [renewalDate, endDate, noticeDays, startDate] = useWatch({ control, name: ['renewalDate', 'endDate', 'noticePeriodDays', 'startDate'] });
  const noticeBy = noticeByFor(renewalDate, endDate, noticeDays);
  // Blacklisted vendors take no new contracts (VENDOR_BLACKLISTED), but a contract keeps showing its own vendor.
  const vendorOptions = (vendors.data?.content ?? []).filter((v) => v.status !== 'BLACKLISTED' || v.id === contract?.vendor.id);

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      const saved = await save.mutateAsync({
        version: contract?.version,
        vendorId: Number(values.vendorId),
        title: values.title.trim(),
        startDate: values.startDate,
        endDate: values.endDate || null,
        value: valueHidden || values.value === '' ? null : Number(values.value.replace(/,/g, '')),
        currency: values.currency.trim().toUpperCase(),
        renewalDate: values.renewalDate || null,
        autoRenew: values.autoRenew,
        noticePeriodDays: values.noticePeriodDays === '' ? null : Number(values.noticePeriodDays),
        notes: values.notes.trim() || null,
      });
      toast.success(editing ? 'Contract updated' : `${saved.title} added`);
      onDone();
      onSaved?.(saved.id);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  async function onDelete() {
    if (!contract) return;
    try {
      await remove.mutateAsync(contract.id);
      toastDeleted('Contract deleted', 'VENDOR_CONTRACT', contract.id);
      onDone();
      onDeleted?.();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  const invalid = (name: keyof ContractValues) => (errors[name] ? true : undefined);

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? 'Edit contract' : 'New contract'}</DialogTitle>
        <DialogDescription>Reminders go out 30 and 7 days before the renewal date, or the end date when there is no renewal date.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <FormBanner message={banner} />
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="contract-vendorId" label="Vendor" required error={errors.vendorId?.message}>
            <Select id="contract-vendorId" aria-invalid={invalid('vendorId')} disabled={vendors.isPending} {...register('vendorId')}>
              <option value="">{vendors.isPending ? 'Loading…' : 'Choose a vendor'}</option>
              {vendorOptions.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="contract-title" label="Title" required error={errors.title?.message}>
            <Input id="contract-title" placeholder="e.g. Jira Cloud Premium" aria-invalid={invalid('title')} {...register('title')} />
          </FormField>
          <FormField id="contract-startDate" label="Start date" required error={errors.startDate?.message}>
            <Input id="contract-startDate" type="date" aria-invalid={invalid('startDate')} {...register('startDate')} />
          </FormField>
          <FormField id="contract-endDate" label="End date" hint="Leave empty while it has no end date" error={errors.endDate?.message}>
            <Input id="contract-endDate" type="date" min={startDate || undefined} aria-invalid={invalid('endDate')} {...register('endDate')} />
          </FormField>
          {valueHidden ? (
            <div className="space-y-1.5">
              <p className="text-sm font-medium">Value</p>
              <p className="flex items-center gap-2 rounded-md border border-dashed px-3 py-2 text-sm text-muted-foreground">
                <EyeOff className="size-4 shrink-0" aria-hidden />
                Hidden. Only people who can see finance can see or change it; saving keeps it as it is.
              </p>
            </div>
          ) : (
            <FormField
              id="contract-value"
              label="Value"
              hint={canSeeFinance(user) ? undefined : "Finance data: you won't be able to see it after saving"}
              error={errors.value?.message}
            >
              <Input id="contract-value" inputMode="decimal" placeholder="e.g. 18500.00" aria-invalid={invalid('value')} {...register('value')} />
            </FormField>
          )}
          <FormField id="contract-currency" label="Currency" required error={errors.currency?.message}>
            <Input id="contract-currency" maxLength={3} className="uppercase" aria-invalid={invalid('currency')} {...register('currency')} />
          </FormField>
          <FormField id="contract-renewalDate" label="Renewal date" hint={endDate ? 'Leave empty to use the end date' : undefined} error={errors.renewalDate?.message}>
            <Input id="contract-renewalDate" type="date" min={startDate || undefined} aria-invalid={invalid('renewalDate')} {...register('renewalDate')} />
          </FormField>
          <FormField
            id="contract-noticePeriodDays"
            label="Notice period (days)"
            hint={noticeBy ? `Give notice by ${formatLocalDate(noticeBy)}` : 'Days of notice the vendor needs before the renewal'}
            error={errors.noticePeriodDays?.message}
          >
            <Input id="contract-noticePeriodDays" type="number" min={0} max={MAX_NOTICE_DAYS} inputMode="numeric" aria-invalid={invalid('noticePeriodDays')} {...register('noticePeriodDays')} />
          </FormField>
        </div>
        <label className="flex items-start gap-3 rounded-lg border p-3">
          <Checkbox className="mt-0.5" {...register('autoRenew')} />
          <span>
            <span className="block text-sm font-medium">Renews automatically</span>
            <span className="block text-xs text-muted-foreground">It rolls over on the renewal date unless notice is given in time.</span>
          </span>
        </label>
        <FormField id="contract-notes" label="Notes" error={errors.notes?.message}>
          <Textarea id="contract-notes" rows={3} aria-invalid={invalid('notes')} {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete contract'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Add contract'}
        </Button>
      </DialogFooter>
    </form>
  );
}
