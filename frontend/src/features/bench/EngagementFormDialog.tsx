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
import { usePeople } from '@/features/celebrations/use-people';
import { toastDeleted } from '@/features/recycle-bin/undo-toast';
import { errorMessage } from '@/lib/api/errors';
import { applyServerErrors } from '@/lib/api/form-errors';

import { useClients, useDeleteEngagement, useSaveEngagement, type Engagement } from './api';
import { engagementSchema, type EngagementValues } from './bench-schemas';

const SERVER_FIELDS = ['clientId', 'name', 'location', 'startDate', 'endDate', 'accountManagerId', 'notes'] as const;

interface EngagementFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  engagement?: Engagement;
  /** Preselected client for a new engagement. */
  clientId?: number;
  onSaved?: (id: number) => void;
  onDeleted?: () => void;
}

export function EngagementFormDialog({ open, onOpenChange, ...props }: EngagementFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">{open && <EngagementForm {...props} onDone={() => onOpenChange(false)} />}</DialogContent>
    </Dialog>
  );
}

function EngagementForm({ engagement, clientId, onSaved, onDeleted, onDone }: Omit<EngagementFormDialogProps, 'open' | 'onOpenChange'> & { onDone: () => void }) {
  const editing = engagement !== undefined;
  const clients = useClients({ size: 100, sort: 'name,asc' });
  const { people } = usePeople();
  const save = useSaveEngagement(engagement?.id ?? null);
  const remove = useDeleteEngagement();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<EngagementValues>({
    resolver: zodResolver(engagementSchema),
    defaultValues: {
      clientId: String(engagement?.client.id ?? clientId ?? ''),
      name: engagement?.name ?? '',
      location: engagement?.location ?? '',
      startDate: engagement?.startDate ?? '',
      endDate: engagement?.endDate ?? '',
      accountManagerId: engagement?.accountManager ? String(engagement.accountManager.id) : '',
      notes: engagement?.notes ?? '',
    },
  });

  // Inactive clients take no new engagements, but an engagement keeps showing its own client.
  const clientOptions = (clients.data?.content ?? []).filter((c) => c.status === 'ACTIVE' || c.id === engagement?.client.id || c.id === clientId);
  const manager = engagement?.accountManager;
  const managers = manager && !people.some((p) => p.id === manager.id) ? [{ id: manager.id, fullName: manager.fullName, detail: null }, ...people] : people;

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      const saved = await save.mutateAsync({
        version: engagement?.version,
        clientId: Number(values.clientId),
        name: values.name.trim(),
        location: values.location.trim() || null,
        startDate: values.startDate,
        endDate: values.endDate || null,
        accountManagerId: values.accountManagerId ? Number(values.accountManagerId) : null,
        notes: values.notes.trim() || null,
      });
      toast.success(editing ? 'Engagement updated' : `${saved.name} created`);
      onDone();
      onSaved?.(saved.id);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  async function onDelete() {
    if (!engagement) return;
    try {
      await remove.mutateAsync(engagement.id);
      toastDeleted('Engagement deleted', 'CLIENT_ENGAGEMENT', engagement.id);
      onDone();
      onDeleted?.();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  const invalid = (name: keyof EngagementValues) => (errors[name] ? true : undefined);

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? 'Edit engagement' : 'New engagement'}</DialogTitle>
        <DialogDescription>A piece of client work consultants are allocated to. Separate from internal Projects.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <FormBanner message={banner} />
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="engagement-clientId" label="Client" required error={errors.clientId?.message}>
            <Select id="engagement-clientId" aria-invalid={invalid('clientId')} disabled={clients.isPending} {...register('clientId')}>
              <option value="">{clients.isPending ? 'Loading…' : 'Choose a client'}</option>
              {clientOptions.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="engagement-name" label="Engagement name" required error={errors.name?.message}>
            <Input id="engagement-name" placeholder="e.g. S/4HANA regression testing" aria-invalid={invalid('name')} {...register('name')} />
          </FormField>
          <FormField id="engagement-startDate" label="Start date" required error={errors.startDate?.message}>
            <Input id="engagement-startDate" type="date" aria-invalid={invalid('startDate')} {...register('startDate')} />
          </FormField>
          <FormField id="engagement-endDate" label="End date" hint="Leave empty while it has no end date" error={errors.endDate?.message}>
            <Input id="engagement-endDate" type="date" aria-invalid={invalid('endDate')} {...register('endDate')} />
          </FormField>
          <FormField id="engagement-location" label="Location" error={errors.location?.message}>
            <Input id="engagement-location" placeholder="e.g. Sydney (onsite)" aria-invalid={invalid('location')} {...register('location')} />
          </FormField>
          <FormField id="engagement-accountManagerId" label="Account manager" error={errors.accountManagerId?.message}>
            <Select id="engagement-accountManagerId" aria-invalid={invalid('accountManagerId')} {...register('accountManagerId')}>
              <option value="">No account manager</option>
              {managers.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.fullName}
                  {p.detail ? ` — ${p.detail}` : ''}
                </option>
              ))}
            </Select>
          </FormField>
        </div>
        <FormField id="engagement-notes" label="Notes" error={errors.notes?.message}>
          <Textarea id="engagement-notes" rows={3} aria-invalid={invalid('notes')} {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete engagement'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Create engagement'}
        </Button>
      </DialogFooter>
    </form>
  );
}
