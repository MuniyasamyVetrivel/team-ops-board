import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useDepartments } from '@/features/departments/api';
import { useSlaPolicies } from '@/features/sla/api';
import { PRIORITY_LABELS } from '@/features/tasks/task-meta';
import { PRIORITIES } from '@/features/tasks/types';
import { applyServerErrors } from '@/lib/api/form-errors';

import { useCreateTicket, useTicketCategories } from './api';
import { formatMinutes } from './ticket-meta';
import type { TicketDetail } from './types';

/** Mirrors TicketRequests.CreateTicket. The team is required only when the category has none. */
const createTicketSchema = z.object({
  subject: z.string().trim().min(1, 'Subject is required').max(250),
  description: z.string().max(20000),
  categoryId: z.string().min(1, 'Category is required'),
  departmentId: z.string(),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'URGENT']),
});

type CreateTicketValues = z.infer<typeof createTicketSchema>;

const SERVER_FIELDS = ['subject', 'description', 'categoryId', 'departmentId'] as const;

interface CreateTicketDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: (ticket: TicketDetail) => void;
}

export function CreateTicketDialog({ open, onOpenChange, onCreated }: CreateTicketDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        {open && (
          <CreateTicketForm
            onCancel={() => onOpenChange(false)}
            onCreated={(ticket) => {
              onOpenChange(false);
              onCreated(ticket);
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function CreateTicketForm({ onCancel, onCreated }: { onCancel: () => void; onCreated: (ticket: TicketDetail) => void }) {
  const categories = useTicketCategories();
  const departments = useDepartments();
  const policies = useSlaPolicies();
  const createTicket = useCreateTicket();
  const [banner, setBanner] = useState<string | null>(null);

  const { register, handleSubmit, control, setError, formState: { errors, isSubmitting } } = useForm<CreateTicketValues>({
    resolver: zodResolver(createTicketSchema),
    defaultValues: { subject: '', description: '', categoryId: '', departmentId: '', priority: 'MEDIUM' },
  });

  const categoryId = Number(useWatch({ control, name: 'categoryId' }));
  const priority = useWatch({ control, name: 'priority' });
  const category = categories.data?.find((c) => c.id === categoryId);
  const needsDepartment = Boolean(category && !category.defaultDepartment);
  const policy = policies.data?.find((p) => p.priority === priority);

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    if (needsDepartment && !values.departmentId) {
      setError('departmentId', { type: 'required', message: 'Choose the team that should handle this' });
      return;
    }
    try {
      const created = await createTicket.mutateAsync({
        subject: values.subject,
        description: values.description.trim() || null,
        categoryId: Number(values.categoryId),
        departmentId: needsDepartment ? Number(values.departmentId) : null,
        priority: values.priority,
      });
      toast.success(`${created.code} raised`);
      onCreated(created);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>Raise a ticket</DialogTitle>
        <DialogDescription>Tell the team what you need. The category decides who handles it.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="subject" label="Subject" required error={errors.subject?.message}>
          <Input id="subject" autoFocus aria-invalid={errors.subject ? true : undefined} {...register('subject')} />
        </FormField>
        <FormField id="categoryId" label="Category" required error={errors.categoryId?.message}
          hint={category ? (category.defaultDepartment ? `Handled by ${category.defaultDepartment.name}` : category.description ?? undefined) : undefined}>
          <Select id="categoryId" aria-invalid={errors.categoryId ? true : undefined} {...register('categoryId')}>
            <option value="">Choose a category…</option>
            {categories.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </Select>
        </FormField>
        {needsDepartment && (
          <FormField id="departmentId" label="Team" required error={errors.departmentId?.message}>
            <Select id="departmentId" aria-invalid={errors.departmentId ? true : undefined} {...register('departmentId')}>
              <option value="">Choose a team…</option>
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
        <FormField id="priority" label="Priority"
          hint={policy ? `First response within ${formatMinutes(policy.firstResponseMinutes)}, resolution within ${formatMinutes(policy.resolutionMinutes)}` : undefined}>
          <Select id="priority" {...register('priority')}>
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {PRIORITY_LABELS[p]}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField id="description" label="Description" hint="What happened, what you expected, and any error messages" error={errors.description?.message}>
          <Textarea id="description" rows={5} {...register('description')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          Raise ticket
        </Button>
      </DialogFooter>
    </form>
  );
}
