import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import type { DepartmentListItem } from '@/features/departments/api';
import { DetailsWithHistory } from '@/features/history/DetailsWithHistory';
import { toastDeleted } from '@/features/recycle-bin/undo-toast';
import { errorMessage, toApiError } from '@/lib/api/errors';
import { applyServerErrors } from '@/lib/api/form-errors';

import { useDeleteBudget, useSaveBudget, type Budget } from './api';
import { FormLoading } from './FinanceFields';
import { BASE_CURRENCY, monthLabel, parseAmount } from './finance-meta';
import { useDepartmentChoices } from './finance-options';
import { budgetSchema, type BudgetValues } from './finance-schemas';

const SERVER_FIELDS = ['departmentId', 'month', 'amount', 'notes'] as const;

const CODE_FIELDS: Record<string, (typeof SERVER_FIELDS)[number]> = {
  BUDGET_EXISTS: 'departmentId',
  UNKNOWN_DEPARTMENT: 'departmentId',
  INVALID_PERIOD: 'month',
};

interface BudgetFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this budget (its department and month stay fixed); create a new one when absent. */
  budget?: Budget;
  /** Preselected department for a new budget. */
  departmentId?: number;
  /** The month the page shows, for a new budget. */
  year: number;
  month: number;
}

export function BudgetFormDialog({ open, onOpenChange, ...props }: BudgetFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        {open && (
          <DetailsWithHistory type="FINANCE_BUDGET" id={props.budget?.id ?? null} listClassName="mx-6 mt-4">
            <BudgetForm {...props} onDone={() => onOpenChange(false)} />
          </DetailsWithHistory>
        )}
      </DialogContent>
    </Dialog>
  );
}

type FormProps = Omit<BudgetFormDialogProps, 'open' | 'onOpenChange'> & { onDone: () => void };

function BudgetForm(props: FormProps) {
  const { departments, loading } = useDepartmentChoices(props.budget?.department.id ?? props.departmentId);
  if (loading) return <FormLoading title={props.budget ? 'Edit budget' : 'New budget'} />;
  return <BudgetFormBody {...props} departments={departments} />;
}

function BudgetFormBody({ budget, departmentId, year, month, onDone, departments }: FormProps & { departments: DepartmentListItem[] }) {
  const editing = budget !== undefined;
  const save = useSaveBudget(budget?.id ?? null);
  const remove = useDeleteBudget();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const period = budget ? { year: budget.year, month: budget.month } : { year, month };

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<BudgetValues>({
    resolver: zodResolver(budgetSchema),
    defaultValues: {
      departmentId: String(budget?.department.id ?? departmentId ?? ''),
      month: `${period.year}-${String(period.month).padStart(2, '0')}`,
      amount: budget ? budget.amount.toFixed(2) : '',
      notes: budget?.notes ?? '',
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const [y, m] = values.month.split('-').map(Number);
    try {
      await save.mutateAsync({
        version: budget?.version,
        departmentId: Number(values.departmentId),
        year: y ?? period.year,
        month: m ?? period.month,
        amount: parseAmount(values.amount) ?? 0,
        notes: values.notes.trim() || null,
      });
      toast.success(editing ? 'Budget updated' : 'Budget set');
      onDone();
    } catch (error) {
      const message = applyServerErrors(error, setError, SERVER_FIELDS);
      const field = CODE_FIELDS[toApiError(error)?.code ?? ''];
      if (field) setError(field, { type: 'server', message });
      else setBanner(message);
    }
  });

  async function onDelete() {
    if (!budget) return;
    try {
      await remove.mutateAsync(budget.id);
      toastDeleted('Budget deleted', 'FINANCE_BUDGET', budget.id);
      onDone();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? `${budget.department.name} budget` : 'New budget'}</DialogTitle>
        <DialogDescription>
          {editing ? `${monthLabel(budget.year, budget.month, 'long')}. The department and month can't change; delete it and add a new one instead.` : `In ${BASE_CURRENCY}, for one department and month.`}
        </DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <FormBanner message={banner} />
        {!editing && (
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="budget-departmentId" label="Department" required error={errors.departmentId?.message}>
              <Select id="budget-departmentId" aria-invalid={errors.departmentId ? true : undefined} {...register('departmentId')}>
                <option value="">Choose a department</option>
                {departments.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </Select>
            </FormField>
            <FormField id="budget-month" label="Month" required error={errors.month?.message}>
              <Input id="budget-month" type="month" aria-invalid={errors.month ? true : undefined} {...register('month')} />
            </FormField>
          </div>
        )}
        <FormField id="budget-amount" label={`Amount (${BASE_CURRENCY})`} required error={errors.amount?.message}>
          <Input id="budget-amount" inputMode="decimal" placeholder="e.g. 25000.00" aria-invalid={errors.amount ? true : undefined} {...register('amount')} />
        </FormField>
        <FormField id="budget-notes" label="Notes" error={errors.notes?.message}>
          <Textarea id="budget-notes" rows={3} aria-invalid={errors.notes ? true : undefined} {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete budget'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Set budget'}
        </Button>
      </DialogFooter>
    </form>
  );
}
