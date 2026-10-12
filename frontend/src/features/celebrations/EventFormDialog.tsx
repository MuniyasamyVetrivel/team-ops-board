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
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { toLocalInput } from '@/features/calendar/calendar-dates';
import { useEmailCampaigns } from '@/features/marketing/email/api';
import { errorMessage } from '@/lib/api/errors';
import { applyServerErrors } from '@/lib/api/form-errors';

import { EVENT_STATUSES, EVENT_TYPES, useDeleteMarketingEvent, useSaveMarketingEvent, type MarketingEvent } from './api';
import { EVENT_STATUS_STYLE, EVENT_TYPE_LABELS } from './celebration-meta';
import { eventSchema } from './celebration-schemas';
import { usePeople } from './use-people';

type Values = z.infer<typeof eventSchema>;

interface EventFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  event?: MarketingEvent;
  onSaved?: (id: number) => void;
  onDeleted?: () => void;
}

export function EventFormDialog({ open, onOpenChange, event, onSaved, onDeleted }: EventFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">{open && <EventForm event={event} onDone={() => onOpenChange(false)} onSaved={onSaved} onDeleted={onDeleted} />}</DialogContent>
    </Dialog>
  );
}

function EventForm({ event, onDone, onSaved, onDeleted }: { event?: MarketingEvent; onDone: () => void; onSaved?: (id: number) => void; onDeleted?: () => void }) {
  const { user } = useAuth();
  const canSeeCampaigns = hasPermission(user, 'CAMPAIGN_VIEW');
  const campaigns = useEmailCampaigns({ status: ['SCHEDULED', 'DRAFT'], size: 100 }, canSeeCampaigns);
  const { people } = usePeople();
  const save = useSaveMarketingEvent(event?.id ?? null);
  const remove = useDeleteMarketingEvent();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const editing = event !== undefined;
  const owners = event && !people.some((p) => p.id === event.owner.id) ? [{ id: event.owner.id, fullName: event.owner.fullName, detail: null }, ...people] : people;
  const linked = event?.emailCampaign;

  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(eventSchema),
    defaultValues: {
      title: event?.title ?? '',
      eventType: event?.eventType ?? 'WEBINAR',
      status: event?.status ?? 'PLANNED',
      startAt: event ? toLocalInput(event.startAt) : '',
      endAt: event?.endAt ? toLocalInput(event.endAt) : '',
      location: event?.location ?? '',
      onlineLink: event?.onlineLink ?? '',
      registrationLink: event?.registrationLink ?? '',
      description: event?.description ?? '',
      ownerId: event ? String(event.owner.id) : user && people.some((p) => p.id === user.id) ? String(user.id) : '',
      emailCampaignId: event?.emailCampaignId ? String(event.emailCampaignId) : '',
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const text = (value: string) => value.trim() || null;
    try {
      const saved = await save.mutateAsync({
        version: event?.version,
        title: values.title.trim(),
        eventType: values.eventType,
        status: values.status,
        startAt: new Date(values.startAt).toISOString(),
        endAt: values.endAt ? new Date(values.endAt).toISOString() : null,
        location: text(values.location),
        onlineLink: text(values.onlineLink),
        registrationLink: text(values.registrationLink),
        description: text(values.description),
        ownerId: Number(values.ownerId),
        emailCampaignId: values.emailCampaignId ? Number(values.emailCampaignId) : null,
      });
      toast.success(editing ? 'Event updated' : `${saved.title} added`);
      onDone();
      onSaved?.(saved.id);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, ['title', 'eventType', 'startAt', 'endAt', 'location', 'onlineLink', 'registrationLink', 'description', 'ownerId'] as const));
    }
  });

  async function onDelete() {
    if (!event) return;
    try {
      await remove.mutateAsync(event.id);
      toast.success('Event deleted');
      onDone();
      onDeleted?.();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  const invalid = (name: keyof Values) => (errors[name] ? true : undefined);

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? 'Edit event' : 'New event'}</DialogTitle>
        <DialogDescription>Upcoming events, webinars, event marketing and email marketing appear on everyone’s calendar.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="event-title" label="Title" required error={errors.title?.message}>
          <Input id="event-title" autoFocus aria-invalid={invalid('title')} {...register('title')} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="event-eventType" label="Type" required>
            <Select id="event-eventType" {...register('eventType')}>
              {EVENT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {EVENT_TYPE_LABELS[t]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="event-status" label="Status" required>
            <Select id="event-status" {...register('status')}>
              {EVENT_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {EVENT_STATUS_STYLE[s].label}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="event-startAt" label="Starts" required error={errors.startAt?.message}>
            <Input id="event-startAt" type="datetime-local" aria-invalid={invalid('startAt')} {...register('startAt')} />
          </FormField>
          <FormField id="event-endAt" label="Ends" error={errors.endAt?.message}>
            <Input id="event-endAt" type="datetime-local" aria-invalid={invalid('endAt')} {...register('endAt')} />
          </FormField>
          <FormField id="event-ownerId" label="Owner" required error={errors.ownerId?.message}>
            <Select id="event-ownerId" aria-invalid={invalid('ownerId')} {...register('ownerId')}>
              <option value="">Choose the owner…</option>
              {owners.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.fullName}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="event-location" label="Location" error={errors.location?.message}>
            <Input id="event-location" aria-invalid={invalid('location')} {...register('location')} />
          </FormField>
          <FormField id="event-onlineLink" label="Online link" error={errors.onlineLink?.message}>
            <Input id="event-onlineLink" placeholder="https://…" aria-invalid={invalid('onlineLink')} {...register('onlineLink')} />
          </FormField>
          <FormField id="event-registrationLink" label="Registration link" error={errors.registrationLink?.message}>
            <Input id="event-registrationLink" placeholder="https://…" aria-invalid={invalid('registrationLink')} {...register('registrationLink')} />
          </FormField>
          {canSeeCampaigns && (
            <FormField id="event-emailCampaignId" label="Email campaign" hint="Its scheduled send then shows as this event on the calendar" className="sm:col-span-2">
              <Select id="event-emailCampaignId" {...register('emailCampaignId')}>
                <option value="">No email campaign</option>
                {linked && !(campaigns.data?.content ?? []).some((c) => c.id === linked.id) && <option value={linked.id}>{linked.name}</option>}
                {(campaigns.data?.content ?? []).map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} · {c.campaignDate}
                  </option>
                ))}
              </Select>
            </FormField>
          )}
        </div>
        <FormField id="event-description" label="Description" error={errors.description?.message}>
          <Textarea id="event-description" rows={3} {...register('description')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete event'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Add event'}
        </Button>
      </DialogFooter>
    </form>
  );
}
