import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { isSuperAdmin } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { applyServerErrors } from '@/lib/api/form-errors';

import { useSaveAnnouncement, type Announcement } from './api';

/** Mirrors AnnouncementDtos.Save. Times are entered in the browser's local time and sent as instants. */
const announcementSchema = z
  .object({
    title: z.string().trim().min(1, 'Title is required').max(200),
    body: z.string().trim().min(1, 'Message is required').max(20000),
    targetDepartmentId: z.string(),
    priority: z.enum(['NORMAL', 'IMPORTANT', 'URGENT']),
    publishAt: z.string(),
    expiresAt: z.string(),
    ackRequired: z.boolean(),
  })
  .refine((v) => !v.expiresAt || !v.publishAt || v.expiresAt > v.publishAt, { path: ['expiresAt'], message: 'The expiry must be after the publish time' });

type AnnouncementValues = z.infer<typeof announcementSchema>;

/** ISO instant → value for a datetime-local input (local time, minutes). */
function toLocalInput(iso: string | null): string {
  if (!iso) return '';
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

const SERVER_FIELDS = ['title', 'body', 'expiresAt'] as const;

export function AnnouncementDialog({ open, announcement, onOpenChange }: { open: boolean; announcement: Announcement | null; onOpenChange: (open: boolean) => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">{open && <AnnouncementForm announcement={announcement} onDone={() => onOpenChange(false)} />}</DialogContent>
    </Dialog>
  );
}

function AnnouncementForm({ announcement, onDone }: { announcement: Announcement | null; onDone: () => void }) {
  const { user } = useAuth();
  const departments = useDepartments();
  const save = useSaveAnnouncement(announcement?.id ?? null);
  const [banner, setBanner] = useState<string | null>(null);
  const everyone = isSuperAdmin(user);
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<AnnouncementValues>({
    resolver: zodResolver(announcementSchema),
    defaultValues: {
      title: announcement?.title ?? '',
      body: announcement?.body ?? '',
      targetDepartmentId: announcement?.targetDepartment ? String(announcement.targetDepartment.id) : everyone ? '' : String(user?.department.id ?? ''),
      priority: announcement?.priority ?? 'NORMAL',
      publishAt: toLocalInput(announcement?.publishAt ?? null),
      expiresAt: toLocalInput(announcement?.expiresAt ?? null),
      ackRequired: announcement?.ackRequired ?? false,
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      await save.mutateAsync({
        version: announcement?.version,
        title: values.title,
        body: values.body,
        targetDepartmentId: values.targetDepartmentId ? Number(values.targetDepartmentId) : null,
        priority: values.priority,
        publishAt: values.publishAt ? new Date(values.publishAt).toISOString() : null,
        expiresAt: values.expiresAt ? new Date(values.expiresAt).toISOString() : null,
        ackRequired: values.ackRequired,
      });
      toast.success(announcement ? 'Announcement updated' : 'Announcement published');
      onDone();
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{announcement ? 'Edit announcement' : 'New announcement'}</DialogTitle>
        <DialogDescription>People in the audience are notified when it is published.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="title" label="Title" required error={errors.title?.message}>
          <Input id="title" autoFocus aria-invalid={errors.title ? true : undefined} {...register('title')} />
        </FormField>
        <FormField id="body" label="Message" required error={errors.body?.message}>
          <Textarea id="body" rows={5} aria-invalid={errors.body ? true : undefined} {...register('body')} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="targetDepartmentId" label="Audience">
            <Select id="targetDepartmentId" {...register('targetDepartmentId')}>
              {everyone && <option value="">Everyone</option>}
              {departments.data
                ?.filter((d) => d.status === 'ACTIVE')
                .map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
            </Select>
          </FormField>
          <FormField id="priority" label="Priority">
            <Select id="priority" {...register('priority')}>
              <option value="NORMAL">Normal</option>
              <option value="IMPORTANT">Important</option>
              <option value="URGENT">Urgent</option>
            </Select>
          </FormField>
          <FormField id="publishAt" label="Publish at" hint="Leave empty to publish now">
            <Input id="publishAt" type="datetime-local" {...register('publishAt')} />
          </FormField>
          <FormField id="expiresAt" label="Expires at" error={errors.expiresAt?.message}>
            <Input id="expiresAt" type="datetime-local" aria-invalid={errors.expiresAt ? true : undefined} {...register('expiresAt')} />
          </FormField>
        </div>
        <div className="flex items-center gap-2">
          <Checkbox id="ackRequired" {...register('ackRequired')} />
          <Label htmlFor="ackRequired" className="font-normal">
            Ask everyone to acknowledge it
          </Label>
        </div>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {announcement ? 'Save changes' : 'Publish'}
        </Button>
      </DialogFooter>
    </form>
  );
}
