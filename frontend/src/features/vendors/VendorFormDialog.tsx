import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle, Plus, Trash2, UserRound } from 'lucide-react';
import { useState } from 'react';
import { useFieldArray, useForm, type Control, type FieldErrors, type UseFormRegister } from 'react-hook-form';
import { toast } from 'sonner';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { toastDeleted } from '@/features/recycle-bin/undo-toast';
import { errorMessage } from '@/lib/api/errors';
import { applyServerErrors } from '@/lib/api/form-errors';

import { useDeleteVendor, useSaveVendor, VENDOR_CATEGORIES, VENDOR_STATUSES, type Vendor } from './api';
import { CATEGORY_LABELS, VENDOR_STATUS_META } from './vendor-meta';
import { MAX_CONTACTS, vendorSchema, type VendorValues } from './vendor-schemas';

const SERVER_FIELDS = ['name', 'taxId', 'category', 'country', 'address', 'website', 'paymentTerms', 'status', 'rating', 'notes', 'contacts'] as const;

interface VendorFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this vendor; create a new one when absent. */
  vendor?: Vendor;
  onSaved?: (id: number) => void;
  onDeleted?: () => void;
}

export function VendorFormDialog({ open, onOpenChange, ...props }: VendorFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">{open && <VendorForm {...props} onDone={() => onOpenChange(false)} />}</DialogContent>
    </Dialog>
  );
}

function VendorForm({ vendor, onSaved, onDeleted, onDone }: Omit<VendorFormDialogProps, 'open' | 'onOpenChange'> & { onDone: () => void }) {
  const editing = vendor !== undefined;
  const save = useSaveVendor(vendor?.id ?? null);
  const remove = useDeleteVendor();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<VendorValues>({
    resolver: zodResolver(vendorSchema),
    defaultValues: {
      name: vendor?.name ?? '',
      taxId: vendor?.taxId ?? '',
      category: vendor?.category ?? 'SOFTWARE',
      country: vendor?.country ?? '',
      address: vendor?.address ?? '',
      website: vendor?.website ?? '',
      paymentTerms: vendor?.paymentTerms ?? '',
      status: vendor?.status ?? 'ACTIVE',
      rating: vendor?.rating ? (String(vendor.rating) as VendorValues['rating']) : '',
      notes: vendor?.notes ?? '',
      contacts: (vendor?.contacts ?? []).map((c) => ({ id: c.id, name: c.name, role: c.role ?? '', email: c.email ?? '', phone: c.phone ?? '' })),
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const optional = (value: string) => value.trim() || null;
    try {
      const saved = await save.mutateAsync({
        version: vendor?.version,
        name: values.name.trim(),
        taxId: optional(values.taxId),
        category: values.category,
        country: optional(values.country),
        address: optional(values.address),
        website: optional(values.website),
        paymentTerms: optional(values.paymentTerms),
        status: values.status,
        rating: values.rating ? Number(values.rating) : null,
        notes: optional(values.notes),
        contacts: values.contacts.map((c) => ({ id: c.id, name: c.name.trim(), role: optional(c.role), email: optional(c.email), phone: optional(c.phone) })),
      });
      toast.success(editing ? 'Vendor updated' : `${saved.name} added`);
      onDone();
      onSaved?.(saved.id);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  async function onDelete() {
    if (!vendor) return;
    try {
      await remove.mutateAsync(vendor.id);
      toastDeleted('Vendor deleted', 'VENDOR', vendor.id);
      onDone();
      onDeleted?.();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  const invalid = (name: keyof VendorValues) => (errors[name] ? true : undefined);

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? `Edit ${vendor.name}` : 'New vendor'}</DialogTitle>
        <DialogDescription>A company you buy from. Contracts, licences and expenses are linked to it.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-6">
        <FormBanner message={banner} />
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="vendor-name" label="Name" required error={errors.name?.message} className="sm:col-span-2">
            <Input id="vendor-name" autoFocus aria-invalid={invalid('name')} {...register('name')} />
          </FormField>
          <FormField id="vendor-category" label="Category">
            <Select id="vendor-category" {...register('category')}>
              {VENDOR_CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {CATEGORY_LABELS[c]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="vendor-status" label="Status" hint="A blacklisted vendor takes no new contracts">
            <Select id="vendor-status" {...register('status')}>
              {VENDOR_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {VENDOR_STATUS_META[s].label}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="vendor-taxId" label="ABN / GST / tax ID" error={errors.taxId?.message}>
            <Input id="vendor-taxId" aria-invalid={invalid('taxId')} {...register('taxId')} />
          </FormField>
          <FormField id="vendor-country" label="Country" error={errors.country?.message}>
            <Input id="vendor-country" placeholder="e.g. Australia" aria-invalid={invalid('country')} {...register('country')} />
          </FormField>
          <FormField id="vendor-website" label="Website" error={errors.website?.message}>
            <Input id="vendor-website" type="url" placeholder="https://" aria-invalid={invalid('website')} {...register('website')} />
          </FormField>
          <FormField id="vendor-paymentTerms" label="Payment terms" error={errors.paymentTerms?.message}>
            <Input id="vendor-paymentTerms" placeholder="e.g. Net 30" aria-invalid={invalid('paymentTerms')} {...register('paymentTerms')} />
          </FormField>
          <FormField id="vendor-rating" label="Rating">
            <Select id="vendor-rating" {...register('rating')}>
              <option value="">Not rated</option>
              {['5', '4', '3', '2', '1'].map((r) => (
                <option key={r} value={r}>
                  {r} / 5
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="vendor-address" label="Address" error={errors.address?.message} className="sm:col-span-2">
            <Textarea id="vendor-address" rows={2} aria-invalid={invalid('address')} {...register('address')} />
          </FormField>
        </div>
        <ContactFields control={control} register={register} errors={errors} />
        <FormField id="vendor-notes" label="Notes" error={errors.notes?.message}>
          <Textarea id="vendor-notes" rows={3} aria-invalid={invalid('notes')} {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete vendor'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Add vendor'}
        </Button>
      </DialogFooter>
    </form>
  );
}

function ContactFields({ control, register, errors }: { control: Control<VendorValues>; register: UseFormRegister<VendorValues>; errors: FieldErrors<VendorValues> }) {
  const { fields, append, remove } = useFieldArray({ control, name: 'contacts', keyName: 'key' });

  return (
    <section aria-label="Contacts" className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-medium">Contacts</h3>
          <p className="text-xs text-muted-foreground">The first contact is the main one.</p>
        </div>
        <Button type="button" variant="outline" size="sm" disabled={fields.length >= MAX_CONTACTS} onClick={() => append({ id: null, name: '', role: '', email: '', phone: '' })}>
          <Plus aria-hidden />
          Add contact
        </Button>
      </div>
      {fields.length === 0 ? (
        <p className="flex items-center gap-2 rounded-lg border border-dashed px-4 py-3 text-sm text-muted-foreground">
          <UserRound className="size-4" aria-hidden />
          No contacts yet.
        </p>
      ) : (
        <ol className="space-y-3">
          {fields.map((field, index) => {
            const fieldErrors = errors.contacts?.[index];
            const id = (name: string) => `contact-${index}-${name}`;
            return (
              <li key={field.key} className="rounded-lg border p-4">
                <div className="grid gap-3 sm:grid-cols-2">
                  <FormField id={id('name')} label="Name" required error={fieldErrors?.name?.message}>
                    <Input id={id('name')} aria-invalid={fieldErrors?.name ? true : undefined} {...register(`contacts.${index}.name`)} />
                  </FormField>
                  <FormField id={id('role')} label="Role" error={fieldErrors?.role?.message}>
                    <Input id={id('role')} placeholder="e.g. Account manager" {...register(`contacts.${index}.role`)} />
                  </FormField>
                  <FormField id={id('email')} label="Email" error={fieldErrors?.email?.message}>
                    <Input id={id('email')} type="email" aria-invalid={fieldErrors?.email ? true : undefined} {...register(`contacts.${index}.email`)} />
                  </FormField>
                  <FormField id={id('phone')} label="Phone" error={fieldErrors?.phone?.message}>
                    <Input id={id('phone')} type="tel" {...register(`contacts.${index}.phone`)} />
                  </FormField>
                </div>
                <div className="mt-2 flex justify-end">
                  <Button type="button" variant="ghost" size="sm" className="text-destructive" onClick={() => remove(index)}>
                    <Trash2 aria-hidden />
                    Remove contact
                  </Button>
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}
