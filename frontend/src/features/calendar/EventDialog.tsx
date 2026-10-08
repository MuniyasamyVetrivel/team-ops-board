import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { ErrorState } from '@/components/common/ErrorState';
import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
import { isSuperAdmin } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { useTeamDirectory } from '@/features/team/api';
import { errorMessage } from '@/lib/api/errors';
import { applyServerErrors } from '@/lib/api/form-errors';

import { useCalendarEvent, useDeleteEvent, useSaveEvent, type CalendarEventDetail } from './api';
import { EVENT_TYPE_LABELS } from './calendar-meta';

/** Mirrors CalendarDtos.SaveEvent. Timed events are entered in local time and sent as instants. */
const eventSchema = z
  .object({
    title: z.string().trim().min(1, 'Title is required').max(200),
    description: z.string().max(5000),
    eventType: z.enum(['TEAM_EVENT', 'MEETING', 'IMPORTANT_DATE', 'LEAVE']),
    allDay: z.boolean(),
    startDate: z.string(),
    endDate: z.string(),
    startAt: z.string(),
    endAt: z.string(),
    departmentId: z.string(),
    userId: z.string(),
  })
  .superRefine((v, ctx) => {
    if (v.allDay) {
      if (!v.startDate) ctx.addIssue({ code: 'custom', path: ['startDate'], message: 'Start date is required' });
      if (!v.endDate) ctx.addIssue({ code: 'custom', path: ['endDate'], message: 'End date is required' });
      if (v.startDate && v.endDate && v.endDate < v.startDate) ctx.addIssue({ code: 'custom', path: ['endDate'], message: 'The end cannot be before the start' });
    } else {
      if (!v.startAt) ctx.addIssue({ code: 'custom', path: ['startAt'], message: 'Start time is required' });
      if (!v.endAt) ctx.addIssue({ code: 'custom', path: ['endAt'], message: 'End time is required' });
      if (v.startAt && v.endAt && v.endAt <= v.startAt) ctx.addIssue({ code: 'custom', path: ['endAt'], message: 'The end must be after the start' });
    }
    if (v.eventType === 'LEAVE' && !v.userId) ctx.addIssue({ code: 'custom', path: ['userId'], message: 'Choose who is on leave' });
  });

type EventValues = z.infer<typeof eventSchema>;

function toLocalInput(iso: string): string {
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

interface EventDialogProps {
  /** null = closed; { day } = create on that day; { eventId } = open an existing event. */
  target: { eventId: number } | { day: string } | null;
  onClose: () => void;
}

export function EventDialog({ target, onClose }: EventDialogProps) {
  return (
    <Dialog open={target !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-xl">
        {target && ('eventId' in target ? <ExistingEvent id={target.eventId} onDone={onClose} /> : <EventForm event={null} day={target.day} onDone={onClose} />)}
      </DialogContent>
    </Dialog>
  );
}

function ExistingEvent({ id, onDone }: { id: number; onDone: () => void }) {
  const query = useCalendarEvent(id);
  if (query.isPending) {
    return (
      <div className="space-y-3 p-6" role="status" aria-label="Loading event">
        <DialogTitle className="sr-only">Loading event</DialogTitle>
        <Skeleton className="h-6 w-1/2" />
        <Skeleton className="h-32" />
      </div>
    );
  }
  if (query.isError) {
    return (
      <div className="p-6">
        <DialogTitle className="sr-only">Event unavailable</DialogTitle>
        <ErrorState error={query.error} title="Couldn't open this event" />
      </div>
    );
  }
  return <EventForm event={query.data} day={query.data.startDate} onDone={onDone} />;
}

function EventForm({ event, day, onDone }: { event: CalendarEventDetail | null; day: string; onDone: () => void }) {
  const { user } = useAuth();
  const departments = useDepartments();
  const people = useTeamDirectory({ status: 'ACTIVE', size: 100, sort: 'name,asc' });
  const save = useSaveEvent(event?.id ?? null);
  const remove = useDeleteEvent();
  const [banner, setBanner] = useState<string | null>(null);
  const readOnly = event !== null && !event.canEdit;
  const companyWide = isSuperAdmin(user);

  const { register, handleSubmit, control, setError, formState: { errors, isSubmitting } } = useForm<EventValues>({
    resolver: zodResolver(eventSchema),
    defaultValues: {
      title: event?.title ?? '',
      description: event?.description ?? '',
      eventType: event?.eventType ?? 'MEETING',
      allDay: event?.allDay ?? true,
      startDate: event?.startDate ?? day,
      endDate: event?.endDate ?? day,
      startAt: event && !event.allDay ? toLocalInput(event.startAt) : `${day}T10:00`,
      endAt: event && !event.allDay ? toLocalInput(event.endAt) : `${day}T11:00`,
      departmentId: event?.department ? String(event.department.id) : event || companyWide ? '' : String(user?.department.id ?? ''),
      userId: event?.user ? String(event.user.id) : '',
    },
  });
  const allDay = useWatch({ control, name: 'allDay' });
  const eventType = useWatch({ control, name: 'eventType' });

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      await save.mutateAsync({
        version: event?.version,
        title: values.title,
        description: values.description.trim() || null,
        eventType: values.eventType,
        allDay: values.allDay,
        startDate: values.allDay ? values.startDate : null,
        endDate: values.allDay ? values.endDate : null,
        startAt: values.allDay ? null : new Date(values.startAt).toISOString(),
        endAt: values.allDay ? null : new Date(values.endAt).toISOString(),
        departmentId: values.departmentId ? Number(values.departmentId) : null,
        userId: values.eventType === 'LEAVE' && values.userId ? Number(values.userId) : null,
      });
      toast.success(event ? 'Event updated' : 'Event added');
      onDone();
    } catch (error) {
      setBanner(applyServerErrors(error, setError, ['title', 'description'] as const));
    }
  });

  async function onDelete() {
    if (!event) return;
    try {
      await remove.mutateAsync(event.id);
      toast.success('Event deleted');
      onDone();
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{event ? (readOnly ? event.title : 'Edit event') : 'New event'}</DialogTitle>
        <DialogDescription>
          {readOnly ? `${EVENT_TYPE_LABELS[event.eventType]}${event.createdBy ? ` · added by ${event.createdBy.fullName}` : ''}` : 'Leave needs the person who is away; company-wide events are for Super Admins.'}
        </DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <fieldset disabled={readOnly} className="space-y-4">
          <FormField id="event-title" label="Title" required error={errors.title?.message}>
            <Input id="event-title" autoFocus aria-invalid={errors.title ? true : undefined} {...register('title')} />
          </FormField>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="event-type" label="Type">
              <Select id="event-type" {...register('eventType')}>
                {(Object.keys(EVENT_TYPE_LABELS) as (keyof typeof EVENT_TYPE_LABELS)[]).map((t) => (
                  <option key={t} value={t}>
                    {EVENT_TYPE_LABELS[t]}
                  </option>
                ))}
              </Select>
            </FormField>
            {eventType === 'LEAVE' ? (
              <FormField id="event-user" label="Who is on leave" required error={errors.userId?.message}>
                <Select id="event-user" aria-invalid={errors.userId ? true : undefined} {...register('userId')}>
                  <option value="">Choose a person…</option>
                  {people.data?.content.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.fullName} — {p.department.name}
                    </option>
                  ))}
                </Select>
              </FormField>
            ) : (
              <FormField id="event-department" label="Audience">
                <Select id="event-department" {...register('departmentId')}>
                  {(companyWide || (event && !event.department)) && <option value="">Company-wide</option>}
                  {departments.data
                    ?.filter((d) => d.status === 'ACTIVE')
                    .map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.name}
                      </option>
                    ))}
                </Select>
              </FormField>
            )}
          </div>
          <div className="flex items-center gap-2">
            <Checkbox id="event-all-day" {...register('allDay')} />
            <Label htmlFor="event-all-day" className="font-normal">
              All day
            </Label>
          </div>
          {allDay ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField id="event-start-date" label="From" error={errors.startDate?.message}>
                <Input id="event-start-date" type="date" {...register('startDate')} />
              </FormField>
              <FormField id="event-end-date" label="To" error={errors.endDate?.message}>
                <Input id="event-end-date" type="date" {...register('endDate')} />
              </FormField>
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField id="event-start-at" label="Starts" error={errors.startAt?.message}>
                <Input id="event-start-at" type="datetime-local" {...register('startAt')} />
              </FormField>
              <FormField id="event-end-at" label="Ends" error={errors.endAt?.message}>
                <Input id="event-end-at" type="datetime-local" {...register('endAt')} />
              </FormField>
            </div>
          )}
          <FormField id="event-description" label="Details">
            <Textarea id="event-description" rows={3} {...register('description')} />
          </FormField>
        </fieldset>
      </DialogBody>
      <DialogFooter>
        {event?.canEdit && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => void onDelete()}>
            <Trash2 aria-hidden />
            Delete
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          {readOnly ? 'Done' : 'Cancel'}
        </Button>
        {!readOnly && (
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
            {event ? 'Save changes' : 'Add event'}
          </Button>
        )}
      </DialogFooter>
    </form>
  );
}
