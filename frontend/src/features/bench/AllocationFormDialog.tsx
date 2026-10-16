import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle, Trash2, TriangleAlert } from 'lucide-react';
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
import { toastDeleted } from '@/features/recycle-bin/undo-toast';
import { errorMessage } from '@/lib/api/errors';
import { applyServerErrors } from '@/lib/api/form-errors';
import { useDebouncedValue } from '@/lib/use-debounced-value';

import { useAllocationCheck, useConsultants, useDeleteAllocation, useEngagements, useSaveAllocation, type Allocation, type AllocationCheckQuery, type OverAllocation } from './api';
import { consultantName, dateRange, formatBenchDate } from './bench-meta';
import { allocationSchema, outsideEngagement, type AllocationValues } from './bench-schemas';

const SERVER_FIELDS = ['consultantId', 'engagementId', 'role', 'startDate', 'endDate', 'allocationPercent', 'notes'] as const;

interface AllocationFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this allocation; create a new one when absent. */
  allocation?: Allocation;
  /** Preselected for a new allocation (from a consultant's profile or an engagement). */
  consultantId?: number;
  engagementId?: number;
  onSaved?: (id: number) => void;
  onDeleted?: () => void;
}

export function AllocationFormDialog({ open, onOpenChange, ...props }: AllocationFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">{open && <AllocationForm {...props} onDone={() => onOpenChange(false)} />}</DialogContent>
    </Dialog>
  );
}

function AllocationForm({ allocation, consultantId, engagementId, onSaved, onDeleted, onDone }: Omit<AllocationFormDialogProps, 'open' | 'onOpenChange'> & { onDone: () => void }) {
  const editing = allocation !== undefined;
  const consultants = useConsultants({ size: 100, sort: 'name,asc' });
  const engagements = useEngagements({ size: 100, sort: 'start,desc' });
  const save = useSaveAllocation(allocation?.id ?? null);
  const remove = useDeleteAllocation();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<AllocationValues>({
    resolver: zodResolver(allocationSchema),
    defaultValues: {
      consultantId: String(allocation?.consultant.id ?? consultantId ?? ''),
      engagementId: String(allocation?.engagement.id ?? engagementId ?? ''),
      role: allocation?.role ?? '',
      startDate: allocation?.startDate ?? '',
      endDate: allocation?.endDate ?? '',
      allocationPercent: allocation ? String(allocation.allocationPercent) : '100',
      billable: allocation?.billable ?? true,
      notes: allocation?.notes ?? '',
    },
  });

  const watched = useWatch({ control });
  // Consultants who left can't be given new work (CONSULTANT_LEFT); ended engagements take no new allocations.
  const consultantOptions = (consultants.data?.content ?? []).filter((c) => c.employmentStatus !== 'LEFT' || c.id === allocation?.consultant.id);
  const engagementOptions = (engagements.data?.content ?? []).filter((e) => e.status !== 'ENDED' || e.id === allocation?.engagement.id || e.id === engagementId);
  const engagement = engagementOptions.find((e) => String(e.id) === watched.engagementId);
  const consultant = consultantOptions.find((c) => String(c.id) === watched.consultantId);

  const overAllocations = useOverAllocationCheck(watched, allocation?.id ?? null);

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const outside = engagement ? outsideEngagement(values, engagement) : null;
    if (outside) {
      setError(outside.field, { type: 'engagement', message: outside.message });
      return;
    }
    try {
      const saved = await save.mutateAsync({
        version: allocation?.version,
        consultantId: Number(values.consultantId),
        engagementId: Number(values.engagementId),
        role: values.role.trim(),
        startDate: values.startDate,
        endDate: values.endDate || null,
        allocationPercent: Number(values.allocationPercent),
        billable: values.billable,
        notes: values.notes.trim() || null,
      });
      const name = consultantName(saved.allocation.consultant.fullName);
      if (saved.overAllocations.length > 0) {
        const peak = Math.max(...saved.overAllocations.map((o) => o.totalPercent));
        toast.warning(editing ? 'Allocation updated' : 'Allocation saved', { description: `${name} is now over 100% allocated (up to ${peak}%) for part of this period.` });
      } else {
        toast.success(editing ? 'Allocation updated' : `${name} allocated`);
      }
      onDone();
      onSaved?.(saved.allocation.id);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  async function onDelete() {
    if (!allocation) return;
    try {
      await remove.mutateAsync(allocation.id);
      toastDeleted('Allocation deleted', 'ALLOCATION', allocation.id);
      onDone();
      onDeleted?.();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  const invalid = (name: keyof AllocationValues) => (errors[name] ? true : undefined);

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? 'Edit allocation' : 'New allocation'}</DialogTitle>
        <DialogDescription>Put a consultant on a client engagement for a share of their time. Going above 100% in total is allowed, with a warning.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <FormBanner message={banner} />
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="allocation-consultantId" label="Consultant" required error={errors.consultantId?.message}>
            <Select id="allocation-consultantId" aria-invalid={invalid('consultantId')} disabled={consultants.isPending} {...register('consultantId')}>
              <option value="">{consultants.isPending ? 'Loading…' : 'Choose a consultant'}</option>
              {consultantOptions.map((c) => (
                <option key={c.id} value={c.id}>
                  {consultantName(c.employee?.fullName)}
                  {c.employee ? ` (${c.employee.employeeCode})` : ''} — {c.allocatedPercent}% today
                </option>
              ))}
            </Select>
          </FormField>
          <FormField
            id="allocation-engagementId"
            label="Engagement"
            required
            hint={engagement ? `Runs ${dateRange(engagement.startDate, engagement.endDate)}` : undefined}
            error={errors.engagementId?.message}
          >
            <Select id="allocation-engagementId" aria-invalid={invalid('engagementId')} disabled={engagements.isPending} {...register('engagementId')}>
              <option value="">{engagements.isPending ? 'Loading…' : 'Choose an engagement'}</option>
              {engagementOptions.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.client.name} · {e.name}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="allocation-role" label="Role" required error={errors.role?.message}>
            <Input id="allocation-role" placeholder="e.g. Test automation lead" aria-invalid={invalid('role')} {...register('role')} />
          </FormField>
          <FormField id="allocation-allocationPercent" label="Allocation (%)" required error={errors.allocationPercent?.message}>
            <Input id="allocation-allocationPercent" type="number" min={1} max={100} step={1} inputMode="numeric" aria-invalid={invalid('allocationPercent')} {...register('allocationPercent')} />
          </FormField>
          <FormField id="allocation-startDate" label="Start date" required error={errors.startDate?.message}>
            <Input id="allocation-startDate" type="date" min={engagement?.startDate} max={engagement?.endDate ?? undefined} aria-invalid={invalid('startDate')} {...register('startDate')} />
          </FormField>
          <FormField id="allocation-endDate" label="End date" hint={engagement?.endDate ? undefined : 'Leave empty while it has no end date'} error={errors.endDate?.message}>
            <Input id="allocation-endDate" type="date" min={watched.startDate || engagement?.startDate} max={engagement?.endDate ?? undefined} aria-invalid={invalid('endDate')} {...register('endDate')} />
          </FormField>
        </div>
        <label className="flex items-start gap-3 rounded-lg border p-3">
          <Checkbox className="mt-0.5" {...register('billable')} />
          <span>
            <span className="block text-sm font-medium">Billable</span>
            <span className="block text-xs text-muted-foreground">Billed to the client. Non-billable time (shadowing, pre-sales, internal) still counts towards utilisation.</span>
          </span>
        </label>
        <OverAllocationWarning name={consultant ? consultantName(consultant.employee?.fullName) : 'This consultant'} stretches={overAllocations} />
        <FormField id="allocation-notes" label="Notes" error={errors.notes?.message}>
          <Textarea id="allocation-notes" rows={3} aria-invalid={invalid('notes')} {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete allocation'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {overAllocations.length > 0 ? 'Save anyway' : editing ? 'Save changes' : 'Save allocation'}
        </Button>
      </DialogFooter>
    </form>
  );
}

/**
 * Asks the server (debounced) where the allocation as entered would take the consultant above 100%, leaving out the
 * allocation being edited. Nothing is checked until consultant, start date and a valid percentage are filled in.
 */
function useOverAllocationCheck(values: Partial<AllocationValues>, excludeId: number | null): OverAllocation[] {
  const percent = Number(values.allocationPercent);
  const ready =
    Boolean(values.consultantId) && Boolean(values.startDate) && Number.isInteger(percent) && percent >= 1 && percent <= 100 && (!values.endDate || !values.startDate || values.endDate >= values.startDate);
  const query: AllocationCheckQuery | null = ready
    ? { consultantId: Number(values.consultantId), startDate: values.startDate!, endDate: values.endDate || null, allocationPercent: percent, excludeId }
    : null;
  // Debounce a string: a fresh object every render would never settle.
  const key = useDebouncedValue(query ? JSON.stringify(query) : '', 400);
  const check = useAllocationCheck(key ? (JSON.parse(key) as AllocationCheckQuery) : null);
  return ready && key ? (check.data ?? []) : [];
}

export function OverAllocationWarning({ name, stretches }: { name: string; stretches: OverAllocation[] }) {
  if (stretches.length === 0) return null;
  return (
    <div role="alert" className="rounded-lg border border-status-warning/40 bg-status-warning/10 p-4 text-sm">
      <p className="flex items-center gap-2 font-medium text-status-warning">
        <TriangleAlert className="size-4 shrink-0" aria-hidden />
        Over 100% allocated
      </p>
      <p className="mt-1 text-foreground">{name} would be allocated above 100% here. You can still save; the dashboard and bench count them at 100% at most.</p>
      <ul className="mt-2 space-y-0.5">
        {stretches.map((s) => (
          <li key={s.from} className="flex flex-wrap gap-x-2 tabular-nums">
            <span>{s.to ? `${formatBenchDate(s.from)} – ${formatBenchDate(s.to)}` : `From ${formatBenchDate(s.from)}`}</span>
            <span className="font-semibold">{s.totalPercent}% in total</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
