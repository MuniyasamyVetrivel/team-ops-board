import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import type { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { errorMessage } from '@/lib/api/errors';
import { applyServerErrors } from '@/lib/api/form-errors';

import { DAY_CATEGORIES, REGIONS, useDeleteImportantDay, useSaveImportantDay, type ImportantDay } from './api';
import { DAY_CATEGORY_LABELS, RECURRENCE_LABELS, REGION_LABELS } from './celebration-meta';
import { importantDaySchema } from './celebration-schemas';

type Values = z.infer<typeof importantDaySchema>;

interface ImportantDayFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  day?: ImportantDay;
  onSaved?: (id: number) => void;
  onDeleted?: () => void;
}

export function ImportantDayFormDialog({ open, onOpenChange, day, onSaved, onDeleted }: ImportantDayFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">{open && <DayForm day={day} onDone={() => onOpenChange(false)} onSaved={onSaved} onDeleted={onDeleted} />}</DialogContent>
    </Dialog>
  );
}

function DayForm({ day, onDone, onSaved, onDeleted }: { day?: ImportantDay; onDone: () => void; onSaved?: (id: number) => void; onDeleted?: () => void }) {
  const save = useSaveImportantDay(day?.id ?? null);
  const remove = useDeleteImportantDay();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const editing = day !== undefined;
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(importantDaySchema),
    defaultValues: {
      name: day?.name ?? '',
      date: day?.date ?? '',
      category: day?.category ?? 'FESTIVAL',
      region: day?.region ?? 'GLOBAL',
      recurrence: day?.recurrence ?? 'YEARLY',
      description: day?.description ?? '',
      marketingNotes: day?.marketingNotes ?? '',
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      const saved = await save.mutateAsync({
        version: day?.version,
        name: values.name.trim(),
        date: values.date,
        category: values.category,
        region: values.region,
        recurrence: values.recurrence,
        description: values.description.trim() || null,
        marketingNotes: values.marketingNotes.trim() || null,
      });
      toast.success(editing ? 'Important day updated' : `${saved.name} added`);
      onDone();
      onSaved?.(saved.id);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, ['name', 'date', 'category', 'region', 'recurrence', 'description', 'marketingNotes'] as const));
    }
  });

  async function onDelete() {
    if (!day) return;
    try {
      await remove.mutateAsync(day.id);
      toast.success('Important day deleted');
      onDone();
      onDeleted?.();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? 'Edit important day' : 'New important day'}</DialogTitle>
        <DialogDescription>Yearly days repeat on the same date. Festivals whose date moves (such as Diwali) are entered once per year.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="day-name" label="Name" required error={errors.name?.message}>
          <Input id="day-name" autoFocus aria-invalid={errors.name ? true : undefined} {...register('name')} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="day-date" label="Date" required error={errors.date?.message} hint="For a yearly day, its first year">
            <Input id="day-date" type="date" aria-invalid={errors.date ? true : undefined} {...register('date')} />
          </FormField>
          <FormField id="day-recurrence" label="Repeat" required>
            <Select id="day-recurrence" {...register('recurrence')}>
              {(['YEARLY', 'ONE_OFF'] as const).map((r) => (
                <option key={r} value={r}>
                  {RECURRENCE_LABELS[r]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="day-category" label="Category" required>
            <Select id="day-category" {...register('category')}>
              {DAY_CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {DAY_CATEGORY_LABELS[c]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="day-region" label="Region" required>
            <Select id="day-region" {...register('region')}>
              {REGIONS.map((r) => (
                <option key={r} value={r}>
                  {REGION_LABELS[r]}
                </option>
              ))}
            </Select>
          </FormField>
        </div>
        <FormField id="day-description" label="Description" error={errors.description?.message}>
          <Textarea id="day-description" rows={2} {...register('description')} />
        </FormField>
        <FormField id="day-marketingNotes" label="Marketing notes" error={errors.marketingNotes?.message} hint="Creative, channels, message">
          <Textarea id="day-marketingNotes" rows={3} {...register('marketingNotes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete day'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Add important day'}
        </Button>
      </DialogFooter>
    </form>
  );
}
