import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowRightLeft, LoaderCircle, TriangleAlert, Trash2 } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { Link } from 'react-router';
import { toast } from 'sonner';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import type { ApprovalListItem } from '@/features/approvals/api';
import type { DepartmentListItem } from '@/features/departments/api';
import { toastDeleted } from '@/features/recycle-bin/undo-toast';
import { errorMessage, toApiError } from '@/lib/api/errors';
import { applyServerErrors } from '@/lib/api/form-errors';
import { formatLocalDate } from '@/lib/format';
import { useDebouncedValue } from '@/lib/use-debounced-value';

import { EXPENSE_CATEGORIES, useConversion, useDeleteExpense, useFinanceContext, useSaveExpense, type Expense, type FinanceContext } from './api';
import { CurrencyInput, FormLoading, VendorField } from './FinanceFields';
import { BASE_CURRENCY, CATEGORY_LABELS, formatAud, formatRate, parseAmount } from './finance-meta';
import { useApprovalChoices, useDepartmentChoices, useVendorChoices } from './finance-options';
import { FINANCE_PATHS } from './finance-paths';
import { expenseSchema, type ExpenseValues } from './finance-schemas';

const SERVER_FIELDS = ['expenseDate', 'category', 'vendorId', 'description', 'amount', 'gst', 'currency', 'departmentId', 'approvalId', 'notes'] as const;

/** Business errors that belong to one field. */
const CODE_FIELDS: Record<string, (typeof SERVER_FIELDS)[number]> = {
  NO_EXCHANGE_RATE: 'currency',
  INVALID_GST: 'gst',
  UNKNOWN_VENDOR: 'vendorId',
  VENDOR_BLACKLISTED: 'vendorId',
  UNKNOWN_DEPARTMENT: 'departmentId',
  UNKNOWN_APPROVAL: 'approvalId',
};

interface ExpenseFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this expense; create a new one when absent. */
  expense?: Expense;
  onSaved?: (id: number) => void;
  onDeleted?: () => void;
}

export function ExpenseFormDialog({ open, onOpenChange, ...props }: ExpenseFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">{open && <ExpenseForm {...props} onDone={() => onOpenChange(false)} />}</DialogContent>
    </Dialog>
  );
}

type FormProps = Omit<ExpenseFormDialogProps, 'open' | 'onOpenChange'> & { onDone: () => void };

/** Waits for the choices (today, currencies, vendors, departments, approvals) so every select mounts with its value. */
function ExpenseForm(props: FormProps) {
  const context = useFinanceContext();
  const vendorChoices = useVendorChoices(props.expense?.vendor);
  const departmentChoices = useDepartmentChoices(props.expense?.department?.id);
  const approvalChoices = useApprovalChoices();
  if (context.isPending || vendorChoices.loading || departmentChoices.loading || approvalChoices.loading) {
    return <FormLoading title={props.expense ? 'Edit expense' : 'New expense'} />;
  }
  return (
    <ExpenseFormBody
      {...props}
      context={context.data}
      vendorChoices={vendorChoices}
      departments={departmentChoices.departments}
      approvals={approvalChoices.allowed ? approvalChoices.approvals : null}
    />
  );
}

function ExpenseFormBody({
  expense,
  onSaved,
  onDeleted,
  onDone,
  context,
  vendorChoices,
  departments,
  approvals,
}: FormProps & {
  context: FinanceContext | undefined;
  vendorChoices: ReturnType<typeof useVendorChoices>;
  departments: DepartmentListItem[];
  /** Null without APPROVAL_VIEW: the expense keeps its approval as it is. */
  approvals: ApprovalListItem[] | null;
}) {
  const editing = expense !== undefined;
  const save = useSaveExpense(expense?.id ?? null);
  const remove = useDeleteExpense();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ExpenseValues>({
    resolver: zodResolver(expenseSchema),
    defaultValues: {
      // Today on the business calendar (the server's), never the browser clock.
      expenseDate: expense?.expenseDate ?? context?.today ?? '',
      category: expense?.category ?? 'SOFTWARE',
      vendorId: expense?.vendorId != null ? String(expense.vendorId) : '',
      description: expense?.description ?? '',
      amount: expense ? expense.amount.toFixed(2) : '',
      gst: expense?.gst != null ? expense.gst.toFixed(2) : '',
      // A new expense starts in the Finance default currency (Admin settings → Finance).
      currency: expense?.currency ?? context?.defaultCurrency ?? BASE_CURRENCY,
      departmentId: expense?.department ? String(expense.department.id) : '',
      approvalId: expense?.approval ? String(expense.approval.id) : '',
      notes: expense?.notes ?? '',
    },
  });

  const [amountRaw, currencyRaw, expenseDate] = useWatch({ control, name: ['amount', 'currency', 'expenseDate'] });
  const currency = currencyRaw.trim().toUpperCase();
  const amount = /^\d{1,12}(\.\d{1,2})?$/.test(amountRaw.trim().replace(/,/g, '')) ? parseAmount(amountRaw) : null;
  // An edit keeps its stored rate unless the date or currency changes (ExpenseService), so the preview does too.
  const keepsRate = editing && expense.expenseDate === expenseDate && expense.currency === currency;

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      const saved = await save.mutateAsync({
        version: expense?.version,
        expenseDate: values.expenseDate,
        category: values.category,
        vendorId: values.vendorId ? Number(values.vendorId) : null,
        description: values.description.trim(),
        amount: parseAmount(values.amount) ?? 0,
        gst: parseAmount(values.gst),
        currency: values.currency.trim().toUpperCase(),
        departmentId: values.departmentId ? Number(values.departmentId) : null,
        approvalId: values.approvalId ? Number(values.approvalId) : null,
        notes: values.notes.trim() || null,
      });
      toast.success(editing ? 'Expense updated' : 'Expense added');
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
    if (!expense) return;
    try {
      await remove.mutateAsync(expense.id);
      toastDeleted('Expense deleted', 'FINANCE_EXPENSE', expense.id);
      onDone();
      onDeleted?.();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  const invalid = (name: keyof ExpenseValues) => (errors[name] ? true : undefined);

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? 'Edit expense' : 'New expense'}</DialogTitle>
        <DialogDescription>The AUD amount is worked out at the exchange rate in force on the expense date and kept, even if rates change later.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <FormBanner message={banner} />
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="expense-description" label="Description" required error={errors.description?.message}>
            <Input id="expense-description" placeholder="e.g. Flights to Sydney for the client workshop" aria-invalid={invalid('description')} {...register('description')} />
          </FormField>
          <FormField id="expense-expenseDate" label="Date" required error={errors.expenseDate?.message}>
            <Input id="expense-expenseDate" type="date" aria-invalid={invalid('expenseDate')} {...register('expenseDate')} />
          </FormField>
          <FormField id="expense-category" label="Category" required error={errors.category?.message}>
            <Select id="expense-category" {...register('category')}>
              {EXPENSE_CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {CATEGORY_LABELS[c]}
                </option>
              ))}
            </Select>
          </FormField>
          <VendorField id="expense-vendorId" choices={vendorChoices} current={expense?.vendor ?? null} error={errors.vendorId?.message} {...register('vendorId')} />
          <div className="grid grid-cols-[1fr_7rem] gap-3">
            <FormField id="expense-amount" label="Amount" required hint="The total paid, GST included" error={errors.amount?.message}>
              <Input id="expense-amount" inputMode="decimal" placeholder="e.g. 1250.00" aria-invalid={invalid('amount')} {...register('amount')} />
            </FormField>
            <FormField id="expense-currency" label="Currency" required error={errors.currency?.message}>
              <CurrencyInput id="expense-currency" choices={context?.currencies} aria-invalid={invalid('currency')} {...register('currency')} />
            </FormField>
          </div>
          <FormField id="expense-gst" label="GST included" hint="Leave empty if there was none" error={errors.gst?.message}>
            <Input id="expense-gst" inputMode="decimal" placeholder="e.g. 113.64" aria-invalid={invalid('gst')} {...register('gst')} />
          </FormField>
          <FormField id="expense-departmentId" label="Department" error={errors.departmentId?.message}>
            <Select id="expense-departmentId" aria-invalid={invalid('departmentId')} {...register('departmentId')}>
              <option value="">No department</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </Select>
          </FormField>
          {approvals ? (
            <FormField id="expense-approvalId" label="Approval" hint="Optional: the approval this spend went through" error={errors.approvalId?.message}>
              <Select id="expense-approvalId" aria-invalid={invalid('approvalId')} {...register('approvalId')}>
                <option value="">Not linked</option>
                {expense?.approval && !approvals.some((a) => a.id === expense.approval?.id) && <option value={expense.approval.id}>{`${expense.approval.code} · ${expense.approval.title}`}</option>}
                {approvals.map((a) => (
                  <option key={a.id} value={a.id}>
                    {`${a.code} · ${a.title}`}
                  </option>
                ))}
              </Select>
            </FormField>
          ) : (
            <div className="space-y-1.5">
              <p className="text-sm font-medium">Approval</p>
              <p className="rounded-md border border-dashed px-3 py-2 text-sm text-muted-foreground">
                {expense?.approval ? `${expense.approval.code} (kept)` : 'Not linked'}. Linking one needs access to Approvals.
              </p>
            </div>
          )}
        </div>
        <AudPreview amount={amount} currency={currency} date={expenseDate} stored={keepsRate ? expense : undefined} />
        <FormField id="expense-notes" label="Notes" error={errors.notes?.message}>
          <Textarea id="expense-notes" rows={3} aria-invalid={invalid('notes')} {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete expense'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Add expense'}
        </Button>
      </DialogFooter>
    </form>
  );
}

/**
 * What the expense will read in AUD: the stored rate when an edit keeps it, otherwise the server's conversion at the
 * rate in force on the date (debounced). Flags a currency with no rate on or before the date, which the server refuses.
 */
function AudPreview({ amount, currency, date, stored }: { amount: number | null; currency: string; date: string; stored?: Expense }) {
  const debouncedAmount = useDebouncedValue(amount);
  const conversion = useConversion(stored || currency === BASE_CURRENCY ? null : debouncedAmount, currency, date);
  if (amount === null || !/^[A-Z]{3}$/.test(currency) || !date || currency === BASE_CURRENCY) return null;

  if (stored) {
    return (
      <Preview>
        {formatAud(Math.round(amount * stored.audRate * 100) / 100)} at the stored rate of {formatRate(stored.audRate)}
        {stored.audRateDate ? ` (from ${formatLocalDate(stored.audRateDate)})` : ''}
      </Preview>
    );
  }
  if (conversion.isError) {
    const missing = toApiError(conversion.error)?.code === 'NO_EXCHANGE_RATE';
    return (
      <p className="flex items-start gap-2 rounded-md border border-status-warning/40 bg-status-warning/5 px-3 py-2 text-sm text-status-warning" role="alert">
        <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
        <span>
          {missing ? `There is no ${currency} exchange rate on or before ${formatLocalDate(date)}, so this expense can't be saved yet. ` : `Couldn't work out the AUD amount: ${errorMessage(conversion.error)} `}
          {missing && (
            <Link to={FINANCE_PATHS.rates} className="font-medium underline">
              Add a rate
            </Link>
          )}
        </span>
      </p>
    );
  }
  if (!conversion.data) return <Preview>Working out the AUD amount…</Preview>;
  return (
    <Preview>
      {formatAud(conversion.data.aud)} at {formatRate(conversion.data.rate)}
      {conversion.data.rateFrom ? ` (the ${currency} rate from ${formatLocalDate(conversion.data.rateFrom)})` : ''}
    </Preview>
  );
}

function Preview({ children }: { children: ReactNode }) {
  return (
    <p className="flex items-center gap-2 rounded-md bg-muted/60 px-3 py-2 text-sm" aria-live="polite">
      <ArrowRightLeft className="size-4 shrink-0 text-muted-foreground" aria-hidden />
      <span>In AUD: {children}</span>
    </p>
  );
}
