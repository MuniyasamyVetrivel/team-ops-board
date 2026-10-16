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
import { toastDeleted } from '@/features/recycle-bin/undo-toast';
import { errorMessage } from '@/lib/api/errors';
import { applyServerErrors } from '@/lib/api/form-errors';

import { useDeleteClient, useSaveClient, type Client } from './api';
import { CLIENT_STATUS_LABELS } from './bench-meta';
import { clientSchema, type ClientValues } from './bench-schemas';

const SERVER_FIELDS = ['name', 'location', 'status', 'notes'] as const;

interface ClientFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  client?: Client;
  onSaved?: (id: number) => void;
  onDeleted?: () => void;
}

export function ClientFormDialog({ open, onOpenChange, ...props }: ClientFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">{open && <ClientForm {...props} onDone={() => onOpenChange(false)} />}</DialogContent>
    </Dialog>
  );
}

function ClientForm({ client, onSaved, onDeleted, onDone }: Omit<ClientFormDialogProps, 'open' | 'onOpenChange'> & { onDone: () => void }) {
  const editing = client !== undefined;
  const save = useSaveClient(client?.id ?? null);
  const remove = useDeleteClient();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ClientValues>({
    resolver: zodResolver(clientSchema),
    defaultValues: { name: client?.name ?? '', location: client?.location ?? '', status: client?.status ?? 'ACTIVE', notes: client?.notes ?? '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      const saved = await save.mutateAsync({
        version: client?.version,
        name: values.name.trim(),
        location: values.location.trim() || null,
        status: values.status,
        notes: values.notes.trim() || null,
      });
      toast.success(editing ? 'Client updated' : `${saved.name} added`);
      onDone();
      onSaved?.(saved.id);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  async function onDelete() {
    if (!client) return;
    try {
      await remove.mutateAsync(client.id);
      toastDeleted('Client deleted', 'CLIENT', client.id);
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
        <DialogTitle>{editing ? 'Edit client' : 'New client'}</DialogTitle>
        <DialogDescription>Clients own engagements. Mark a client inactive when there is no current business.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="client-name" label="Name" required error={errors.name?.message}>
          <Input id="client-name" autoFocus aria-invalid={errors.name ? true : undefined} {...register('name')} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="client-location" label="Location" error={errors.location?.message}>
            <Input id="client-location" aria-invalid={errors.location ? true : undefined} {...register('location')} />
          </FormField>
          <FormField id="client-status" label="Status">
            <Select id="client-status" {...register('status')}>
              {(['ACTIVE', 'INACTIVE'] as const).map((s) => (
                <option key={s} value={s}>
                  {CLIENT_STATUS_LABELS[s]}
                </option>
              ))}
            </Select>
          </FormField>
        </div>
        <FormField id="client-notes" label="Notes" error={errors.notes?.message}>
          <Textarea id="client-notes" rows={3} aria-invalid={errors.notes ? true : undefined} {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete client'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Add client'}
        </Button>
      </DialogFooter>
    </form>
  );
}
