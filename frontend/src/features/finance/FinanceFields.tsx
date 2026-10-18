import { Store } from 'lucide-react';
import { useId, type ComponentProps } from 'react';
import type { UseFormRegisterReturn } from 'react-hook-form';

import { FormField } from '@/components/common/FormField';
import { DialogBody, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import type { VendorRef } from '@/features/vendors/api';

import type { useVendorChoices } from './finance-options';

/**
 * A currency code, typed or picked from the Finance choices (INR, AUD, USD and the currencies with a rate). Any
 * three-letter code is accepted, as the server does; one without a rate is flagged by the form.
 */
export function CurrencyInput({ choices, className, ...props }: ComponentProps<typeof Input> & { choices: string[] | undefined }) {
  const listId = useId();
  return (
    <>
      <Input maxLength={3} autoComplete="off" list={listId} className={`uppercase ${className ?? ''}`} {...props} />
      <datalist id={listId}>
        {(choices ?? []).map((c) => (
          <option key={c} value={c} />
        ))}
      </datalist>
    </>
  );
}

/**
 * The vendor of a licence or expense. Vendors can only be listed by people who may open Vendors (VENDOR_VIEW and the
 * module switch); everyone else sees the current vendor, which saving keeps as it is.
 */
export function VendorField({
  id,
  choices,
  current,
  error,
  ...field
}: UseFormRegisterReturn & { id: string; choices: ReturnType<typeof useVendorChoices>; current: VendorRef | null; error?: string }) {
  if (!choices.available && !choices.loading) {
    return (
      <div className="space-y-1.5">
        <p className="text-sm font-medium">Vendor</p>
        <p className="flex items-center gap-2 rounded-md border border-dashed px-3 py-2 text-sm text-muted-foreground">
          <Store className="size-4 shrink-0" aria-hidden />
          {current ? `${current.name} (kept)` : 'No vendor'}. Choosing one needs access to Vendors.
        </p>
      </div>
    );
  }
  // The registered select mounts once its options exist, so the form's value is selected in it.
  if (choices.loading) {
    return (
      <FormField id={id} label="Vendor">
        <Select id={id} disabled>
          <option>Loading…</option>
        </Select>
      </FormField>
    );
  }
  return (
    <FormField id={id} label="Vendor" error={error}>
      <Select id={id} aria-invalid={error ? true : undefined} {...field}>
        <option value="">No vendor</option>
        {choices.vendors.map((v) => (
          <option key={v.id} value={v.id}>
            {v.name}
            {v.status === 'BLACKLISTED' ? ' (blacklisted)' : ''}
          </option>
        ))}
      </Select>
    </FormField>
  );
}

/** A form dialog's body while its choices load. */
export function FormLoading({ title }: { title: string }) {
  return (
    <>
      <DialogHeader>
        <DialogTitle>{title}</DialogTitle>
      </DialogHeader>
      <DialogBody className="grid gap-4 sm:grid-cols-2" role="status" aria-label="Loading the form">
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} className="h-14" />
        ))}
      </DialogBody>
    </>
  );
}
