import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { toastDeleted } from '@/features/recycle-bin/undo-toast';
import { errorMessage, toApiError } from '@/lib/api/errors';
import { applyServerErrors } from '@/lib/api/form-errors';

import type { DepartmentListItem } from '@/features/departments/api';

import { BILLING_CYCLES, LICENCE_STATUSES, useCurrentRates, useDeleteLicence, useFinanceContext, useSaveLicence, type FinanceContext, type Licence } from './api';
import { BASE_CURRENCY, BILLING_LABELS, LICENCE_STATUS_META, parseAmount } from './finance-meta';
import { useDepartmentChoices, useVendorChoices } from './finance-options';
import { licenceSchema, MAX_SEATS, type LicenceValues } from './finance-schemas';
import { CurrencyInput, FormLoading, VendorField } from './FinanceFields';
import { PeoplePicker, type PickedPerson } from './PeoplePicker';

const SERVER_FIELDS = ['name', 'vendorId', 'ownerId', 'departmentId', 'seatsPaid', 'billingCycle', 'cost', 'currency', 'startDate', 'renewalDate', 'paymentMethod', 'cardLast4', 'status', 'assignedUserIds', 'notes'] as const;

/** Business errors that belong to one field. */
const CODE_FIELDS: Record<string, (typeof SERVER_FIELDS)[number]> = {
  LICENCE_NAME_TAKEN: 'name',
  CARD_NUMBER_NOT_ALLOWED: 'paymentMethod',
  INVALID_CARD_LAST4: 'cardLast4',
  INVALID_DATES: 'renewalDate',
  UNKNOWN_VENDOR: 'vendorId',
  VENDOR_BLACKLISTED: 'vendorId',
  UNKNOWN_DEPARTMENT: 'departmentId',
};

interface LicenceFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this licence; create a new one when absent. */
  licence?: Licence;
  onSaved?: (id: number) => void;
  onDeleted?: () => void;
}

export function LicenceFormDialog({ open, onOpenChange, ...props }: LicenceFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">{open && <LicenceForm {...props} onDone={() => onOpenChange(false)} />}</DialogContent>
    </Dialog>
  );
}

function person(user: { id: number; fullName: string; jobTitle?: string | null; photoVersion?: number | null }): PickedPerson {
  return { id: user.id, fullName: user.fullName, detail: user.jobTitle ?? null, photoVersion: user.photoVersion ?? null };
}

/** Waits for the choices (currencies, vendors, departments) so every select mounts with its value among its options. */
function LicenceForm(props: Omit<LicenceFormDialogProps, 'open' | 'onOpenChange'> & { onDone: () => void }) {
  const context = useFinanceContext();
  const vendorChoices = useVendorChoices(props.licence?.vendor);
  const departmentChoices = useDepartmentChoices(props.licence?.department?.id);
  if (context.isPending || vendorChoices.loading || departmentChoices.loading) return <FormLoading title={props.licence ? 'Edit licence' : 'New tool or licence'} />;
  return <LicenceFormBody {...props} context={context.data} vendorChoices={vendorChoices} departments={departmentChoices.departments} />;
}

function LicenceFormBody({
  licence,
  onSaved,
  onDeleted,
  onDone,
  context,
  vendorChoices,
  departments,
}: Omit<LicenceFormDialogProps, 'open' | 'onOpenChange'> & {
  onDone: () => void;
  context: FinanceContext | undefined;
  vendorChoices: ReturnType<typeof useVendorChoices>;
  departments: DepartmentListItem[];
}) {
  const editing = licence !== undefined;
  const rates = useCurrentRates();
  const save = useSaveLicence(licence?.id ?? null);
  const remove = useDeleteLicence();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [owner, setOwner] = useState<PickedPerson[]>(licence?.owner ? [person(licence.owner)] : []);
  const [assigned, setAssigned] = useState<PickedPerson[]>((licence?.assignedUsers ?? []).map(person));

  const {
    register,
    control,
    handleSubmit,
    setError,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<LicenceValues>({
    resolver: zodResolver(licenceSchema),
    defaultValues: {
      name: licence?.name ?? '',
      vendorId: licence?.vendorId != null ? String(licence.vendorId) : '',
      ownerId: licence?.owner ? String(licence.owner.id) : '',
      departmentId: licence?.department ? String(licence.department.id) : '',
      seatsPaid: licence?.seatsPaid != null ? String(licence.seatsPaid) : '',
      billingCycle: licence?.billingCycle ?? 'MONTHLY',
      cost: licence?.cost != null ? licence.cost.toFixed(2) : '',
      // A new licence starts in the Finance default currency (Admin settings → Finance).
      currency: licence?.currency ?? context?.defaultCurrency ?? BASE_CURRENCY,
      startDate: licence?.startDate ?? '',
      renewalDate: licence?.renewalDate ?? '',
      paymentMethod: licence?.paymentMethod ?? '',
      cardLast4: licence?.cardLast4 ?? '',
      status: licence?.status ?? 'ACTIVE',
      assignedUserIds: licence?.assignedUsers.map((u) => u.id) ?? [],
      notes: licence?.notes ?? '',
    },
  });

  const [currency, status, seatsPaid, startDate] = useWatch({ control, name: ['currency', 'status', 'seatsPaid', 'startDate'] });
  const code = currency.trim().toUpperCase();
  const noRate = code !== BASE_CURRENCY && /^[A-Z]{3}$/.test(code) && rates.data !== undefined && !rates.data.some((r) => r.currency === code);
  const paidSeats = /^\d+$/.test(seatsPaid) ? Number(seatsPaid) : null;

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      const saved = await save.mutateAsync({
        version: licence?.version,
        name: values.name.trim(),
        vendorId: values.vendorId ? Number(values.vendorId) : null,
        ownerId: owner[0]?.id ?? null,
        departmentId: values.departmentId ? Number(values.departmentId) : null,
        seatsPaid: values.seatsPaid === '' ? null : Number(values.seatsPaid),
        billingCycle: values.billingCycle,
        cost: parseAmount(values.cost),
        currency: values.currency.trim().toUpperCase(),
        startDate: values.startDate || null,
        renewalDate: values.renewalDate || null,
        paymentMethod: values.paymentMethod.trim() || null,
        cardLast4: values.cardLast4.trim() || null,
        status: values.status,
        assignedUserIds: assigned.map((p) => p.id),
        notes: values.notes.trim() || null,
      });
      toast.success(editing ? 'Licence updated' : `${saved.name} added`);
      onDone();
      onSaved?.(saved.id);
    } catch (error) {
      const message = applyServerErrors(error, setError, SERVER_FIELDS);
      const field = CODE_FIELDS[toApiError(error)?.code ?? ''];
      if (field) setError(field, { type: 'server', message });
      else setBanner(message);
    }
  });

  async function onDelete() {
    if (!licence) return;
    try {
      await remove.mutateAsync(licence.id);
      toastDeleted('Licence deleted', 'FINANCE_LICENCE', licence.id);
      onDone();
      onDeleted?.();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  const invalid = (name: keyof LicenceValues) => (errors[name] ? true : undefined);

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? 'Edit licence' : 'New tool or licence'}</DialogTitle>
        <DialogDescription>Renewal reminders go out 30 and 7 days before the renewal date of active and trial licences.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <FormBanner message={banner} />
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="licence-name" label="Name" required error={errors.name?.message}>
            <Input id="licence-name" placeholder="e.g. Jira Software" aria-invalid={invalid('name')} {...register('name')} />
          </FormField>
          <FormField id="licence-status" label="Status" error={errors.status?.message}>
            <Select id="licence-status" {...register('status')}>
              {LICENCE_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {LICENCE_STATUS_META[s].label}
                </option>
              ))}
            </Select>
          </FormField>
          <VendorField id="licence-vendorId" choices={vendorChoices} current={licence?.vendor ?? null} error={errors.vendorId?.message} {...register('vendorId')} />
          <FormField id="licence-departmentId" label="Department" error={errors.departmentId?.message}>
            <Select id="licence-departmentId" aria-invalid={invalid('departmentId')} {...register('departmentId')}>
              <option value="">No department</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="licence-billingCycle" label="Billing" required error={errors.billingCycle?.message}>
            <Select id="licence-billingCycle" {...register('billingCycle')}>
              {BILLING_CYCLES.map((b) => (
                <option key={b} value={b}>
                  {BILLING_LABELS[b]}
                </option>
              ))}
            </Select>
          </FormField>
          <div className="grid grid-cols-[1fr_7rem] gap-3">
            <FormField id="licence-cost" label="Cost" hint="Per billing period" error={errors.cost?.message}>
              <Input id="licence-cost" inputMode="decimal" placeholder="e.g. 120.00" aria-invalid={invalid('cost')} {...register('cost')} />
            </FormField>
            <FormField id="licence-currency" label="Currency" required error={errors.currency?.message}>
              <CurrencyInput id="licence-currency" choices={context?.currencies} aria-invalid={invalid('currency')} {...register('currency')} />
            </FormField>
          </div>
          <FormField id="licence-startDate" label="Start date" error={errors.startDate?.message}>
            <Input id="licence-startDate" type="date" aria-invalid={invalid('startDate')} {...register('startDate')} />
          </FormField>
          <FormField id="licence-renewalDate" label={status === 'TRIAL' ? 'Trial ends' : 'Renewal date'} error={errors.renewalDate?.message}>
            <Input id="licence-renewalDate" type="date" min={startDate || undefined} aria-invalid={invalid('renewalDate')} {...register('renewalDate')} />
          </FormField>
          <FormField id="licence-seatsPaid" label="Seats paid" hint="Leave empty for a tool without seats" error={errors.seatsPaid?.message}>
            <Input id="licence-seatsPaid" type="number" min={0} max={MAX_SEATS} inputMode="numeric" aria-invalid={invalid('seatsPaid')} {...register('seatsPaid')} />
          </FormField>
          <FormField id="licence-owner" label="Owner" hint="Who looks after it">
            <PeoplePicker
              id="licence-owner"
              value={owner}
              onChange={(next) => {
                setOwner(next);
                setValue('ownerId', next[0] ? String(next[0].id) : '', { shouldDirty: true });
              }}
              invalid={errors.ownerId ? true : undefined}
            />
          </FormField>
          <FormField id="licence-paymentMethod" label="Payment method" hint="A label only, e.g. Corporate Amex. Never a card number." error={errors.paymentMethod?.message}>
            <Input id="licence-paymentMethod" autoComplete="off" aria-invalid={invalid('paymentMethod')} {...register('paymentMethod')} />
          </FormField>
          <FormField id="licence-cardLast4" label="Card last 4 digits" error={errors.cardLast4?.message}>
            <Input id="licence-cardLast4" inputMode="numeric" maxLength={4} autoComplete="off" placeholder="4242" aria-invalid={invalid('cardLast4')} {...register('cardLast4')} />
          </FormField>
        </div>
        {noRate && (
          <p className="rounded-md border border-status-warning/40 bg-status-warning/5 px-3 py-2 text-sm text-status-warning">
            There is no {code} exchange rate yet, so this licence is left out of the annualised AUD cost until one is added.
          </p>
        )}
        <FormField
          id="licence-assigned"
          label="Assigned users"
          hint={paidSeats !== null ? `${assigned.length} of ${paidSeats} seats used${assigned.length > paidSeats ? ': more users than seats' : ''}` : `${assigned.length} assigned. Each one uses a seat.`}
          error={errors.assignedUserIds?.message}
        >
          <PeoplePicker
            id="licence-assigned"
            multiple
            value={assigned}
            onChange={(next) => {
              setAssigned(next);
              setValue('assignedUserIds', next.map((p) => p.id), { shouldDirty: true });
            }}
            placeholder="Add people by name or email"
          />
        </FormField>
        <FormField id="licence-notes" label="Notes" error={errors.notes?.message}>
          <Textarea id="licence-notes" rows={3} aria-invalid={invalid('notes')} {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete licence'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Add licence'}
        </Button>
      </DialogFooter>
    </form>
  );
}
