import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle, Lock, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { SearchInput } from '@/components/common/SearchInput';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { parseLocalDate } from '@/features/tasks/task-meta';
import { applyServerErrors } from '@/lib/api/form-errors';
import { errorMessage } from '@/lib/api/errors';
import { useDebouncedValue } from '@/lib/use-debounced-value';

import { useMarketingContext } from '../api';
import { LEAD_SOURCES, type LeadSource } from '../targets/api';
import { LEAD_SOURCE_LABELS } from '../targets/target-meta';
import { LEAD_STATUSES, useCreateLead, useDeleteLead, useLeadLinkOptions, useUpdateLead, type Lead, type LinkKind, type LinkOption } from './api';
import { LEAD_STATUS_LABELS, LINK_KIND_HINTS, LINK_KIND_LABELS, linkFields, linkFits, linkKindFor } from './lead-meta';

const SERVER_FIELDS = ['name', 'company', 'email', 'phone', 'source', 'leadDate', 'status', 'ownerId', 'departmentId', 'notes'] as const;

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

/** Mirrors LeadDtos.SaveLead; `today` is the business date (a lead cannot be dated in the future). */
function leadSchema(today: string) {
  return z.object({
    name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
    company: z.string().max(200, 'At most 200 characters'),
    email: z
      .string()
      .trim()
      .max(255, 'At most 255 characters')
      .refine((v) => v === '' || z.email().safeParse(v).success, 'Enter a valid email address'),
    phone: z.string().max(50, 'At most 50 characters'),
    source: z.enum(['ORGANIC', 'EMAIL', 'LINKEDIN', 'PAID_CAMPAIGN', 'BLOG', 'WEBSITE', 'REFERRAL', 'OTHER']),
    linkId: z.string(),
    leadDate: z
      .string()
      .min(1, 'Lead date is required')
      .refine((v) => v === '' || v <= today, 'A lead cannot be dated in the future'),
    status: z.enum(['NEW', 'CONTACTED', 'QUALIFIED', 'CONVERTED', 'LOST']),
    ownerId: z.string(),
    departmentId: z.string(),
    notes: z.string().max(2000, 'At most 2000 characters'),
  });
}

type Values = z.infer<ReturnType<typeof leadSchema>>;

interface LeadFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this lead; create a new one when absent. */
  lead?: Lead;
  onCreated?: (id: number) => void;
  onDeleted?: () => void;
}

export function LeadFormDialog({ open, onOpenChange, lead, onCreated, onDeleted }: LeadFormDialogProps) {
  // "Today" (default and latest lead date) and the owner list come from the marketing context.
  const context = useMarketingContext();
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        {open && !context.data && (
          <div className="space-y-3 p-6" role="status" aria-label="Loading the form">
            <Skeleton className="h-8 w-1/2" />
            <Skeleton className="h-40" />
          </div>
        )}
        {open && context.data && <LeadForm lead={lead} today={context.data.today} onDone={() => onOpenChange(false)} onCreated={onCreated} onDeleted={onDeleted} />}
      </DialogContent>
    </Dialog>
  );
}

interface LeadFormProps {
  lead?: Lead;
  today: string;
  onDone: () => void;
  onCreated?: (id: number) => void;
  onDeleted?: () => void;
}

function LeadForm({ lead, today, onDone, onCreated, onDeleted }: LeadFormProps) {
  const { user } = useAuth();
  const context = useMarketingContext();
  const departments = useDepartments();
  const create = useCreateLead();
  const update = useUpdateLead(lead?.id ?? 0);
  const remove = useDeleteLead();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  // The picked campaign or content, for its date (a lead cannot come in before it).
  const [picked, setPicked] = useState<LinkOption | null>(lead?.link ? { ...lead.link, detail: null } : null);
  const editing = lead !== undefined;
  const locked = lead?.countLocked ?? false;

  const owners = context.data?.owners ?? [];
  const ownerOptions = lead?.owner && !owners.some((o) => o.id === lead.owner?.id) ? [...owners, lead.owner] : owners;
  const activeDepartments = (departments.data ?? []).filter((d) => d.status === 'ACTIVE' || d.id === lead?.department?.id);
  const defaultDepartment = editing ? lead.department?.id : user?.department.id;

  const { register, control, handleSubmit, setError, setValue, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(leadSchema(today)),
    defaultValues: {
      name: lead?.name ?? '',
      company: lead?.company ?? '',
      email: lead?.email ?? '',
      phone: lead?.phone ?? '',
      source: lead?.source ?? 'ORGANIC',
      linkId: lead?.link ? String(lead.link.id) : '',
      leadDate: lead?.leadDate ?? today,
      status: lead?.status ?? 'NEW',
      ownerId: String(lead ? (lead.owner?.id ?? '') : owners.some((o) => o.id === user?.id) ? user?.id : ''),
      departmentId: defaultDepartment ? String(defaultDepartment) : '',
      notes: lead?.notes ?? '',
    },
  });
  const source = useWatch({ control, name: 'source' });
  const linkId = useWatch({ control, name: 'linkId' });
  const kind = linkKindFor(source);

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const linkKind = linkKindFor(values.source);
    const link = linkKind && values.linkId ? Number(values.linkId) : null;
    if (link !== null && picked?.date && values.leadDate < picked.date) {
      setError('leadDate', { type: 'validate', message: `The lead cannot be dated before ${picked.name} (${dayFormat.format(parseLocalDate(picked.date))})` });
      return;
    }
    const body = {
      name: values.name.trim(),
      company: values.company.trim() || null,
      email: values.email.trim() || null,
      phone: values.phone.trim() || null,
      source: values.source,
      ...linkFields(linkKind, link),
      departmentId: values.departmentId ? Number(values.departmentId) : null,
      leadDate: values.leadDate,
      status: values.status,
      ownerId: values.ownerId ? Number(values.ownerId) : null,
      notes: values.notes.trim() || null,
    };
    try {
      if (editing) {
        await update.mutateAsync({ ...body, version: lead.version });
        toast.success('Lead updated');
        onDone();
      } else {
        const created = await create.mutateAsync(body);
        toast.success(`${body.name} added`);
        onDone();
        if (typeof created !== 'number') onCreated?.(created.id);
      }
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  async function onDelete() {
    if (!lead) return;
    try {
      await remove.mutateAsync(lead.id);
      toast.success(`${lead.code} deleted`);
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
        <DialogTitle>{editing ? `Edit ${lead.code}` : 'New lead'}</DialogTitle>
        <DialogDescription>Each lead counts towards the Website Leads target and its source&apos;s target in the month of its lead date.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <FormBanner message={banner} />
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="lead-name" label="Lead name" required error={errors.name?.message}>
            <Input id="lead-name" autoFocus aria-invalid={errors.name ? true : undefined} aria-describedby="lead-name-message" {...register('name')} />
          </FormField>
          <FormField id="lead-company" label="Company" error={errors.company?.message}>
            <Input id="lead-company" aria-invalid={errors.company ? true : undefined} aria-describedby="lead-company-message" {...register('company')} />
          </FormField>
          <FormField id="lead-email" label="Email" error={errors.email?.message}>
            <Input id="lead-email" type="email" aria-invalid={errors.email ? true : undefined} aria-describedby="lead-email-message" {...register('email')} />
          </FormField>
          <FormField id="lead-phone" label="Phone" error={errors.phone?.message}>
            <Input id="lead-phone" type="tel" aria-invalid={errors.phone ? true : undefined} aria-describedby="lead-phone-message" {...register('phone')} />
          </FormField>
        </div>

        {locked && (
          <p className="flex items-start gap-2 rounded-lg border bg-muted/40 px-3 py-2.5 text-sm text-muted-foreground">
            <Lock className="mt-0.5 size-4 shrink-0" aria-hidden />
            The lead&apos;s month is closed, so its date and source stay as counted. Contact details, status, owner, campaign and notes can still change.
          </p>
        )}
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField id="lead-source" label="Source" required error={errors.source?.message}>
            <Select
              id="lead-source"
              disabled={locked}
              {...register('source', {
                // Each source takes its own kind of link, so a change of source starts the link again.
                onChange: () => {
                  setValue('linkId', '');
                  setPicked(null);
                },
              })}
            >
              {LEAD_SOURCES.map((s) => (
                <option key={s} value={s}>
                  {LEAD_SOURCE_LABELS[s]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="lead-date" label="Lead date" required error={errors.leadDate?.message}>
            <Input id="lead-date" type="date" max={today} disabled={locked} aria-invalid={errors.leadDate ? true : undefined} aria-describedby="lead-date-message" {...register('leadDate')} />
          </FormField>
          <FormField id="lead-status" label="Status" required>
            <Select id="lead-status" {...register('status')}>
              {LEAD_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {LEAD_STATUS_LABELS[s]}
                </option>
              ))}
            </Select>
          </FormField>
        </div>

        {kind ? (
          <LinkField
            kind={kind}
            source={source}
            value={linkId}
            current={lead?.link && lead.link.kind === kind ? lead.link : null}
            onChange={(option) => {
              setValue('linkId', option ? String(option.id) : '');
              setPicked(option);
            }}
          />
        ) : (
          <p className="text-sm text-muted-foreground">{LEAD_SOURCE_LABELS[source]} leads are not linked to a campaign or content.</p>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="lead-owner" label="Owner" error={errors.ownerId?.message}>
            <Select id="lead-owner" {...register('ownerId')}>
              <option value="">No owner</option>
              {ownerOptions.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.fullName}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="lead-department" label="Department" error={errors.departmentId?.message}>
            <Select id="lead-department" disabled={departments.isPending} {...register('departmentId')}>
              <option value="">No department</option>
              {activeDepartments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </Select>
          </FormField>
        </div>
        <FormField id="lead-notes" label="Notes" error={errors.notes?.message}>
          <Textarea id="lead-notes" rows={2} {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && !locked && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete lead'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Add lead'}
        </Button>
      </DialogFooter>
    </form>
  );
}

interface LinkFieldProps {
  kind: LinkKind;
  source: LeadSource;
  /** The picked id, '' for none. */
  value: string;
  /** The lead's saved link, kept as an option even when the search no longer finds it. */
  current: { id: number; name: string; date: string | null } | null;
  onChange: (option: LinkOption | null) => void;
}

/** The campaign or content of the lead's source: a server search of the newest matches, narrowed to what fits. */
function LinkField({ kind, source, value, current, onChange }: LinkFieldProps) {
  const [search, setSearch] = useState('');
  const debounced = useDebouncedValue(search.trim());
  const options = useLeadLinkOptions(kind, debounced);
  const fitting = (options.data ?? []).filter((o) => linkFits(source, o));
  const choices: LinkOption[] = current && !fitting.some((o) => o.id === current.id) ? [{ kind, id: current.id, name: current.name, date: current.date, detail: null }, ...fitting] : fitting;
  const label = LINK_KIND_LABELS[kind];

  return (
    <div className="grid gap-3 rounded-lg border p-3 sm:grid-cols-2">
      <SearchInput placeholder={`Search ${LINK_KIND_HINTS[kind].toLowerCase()}`} aria-label={`Search ${label.toLowerCase()}s`} value={search} onChange={(e) => setSearch(e.target.value)} />
      <FormField id="lead-link" label={label} hint={source === 'LINKEDIN' ? 'LinkedIn campaigns only' : LINK_KIND_HINTS[kind]}>
        <Select id="lead-link" value={value} disabled={options.isPending} onChange={(e) => onChange(choices.find((o) => String(o.id) === e.target.value) ?? null)}>
          <option value="">{options.isPending ? 'Loading…' : `No ${label.toLowerCase()}`}</option>
          {choices.map((o) => (
            <option key={o.id} value={o.id}>
              {o.name}
              {o.date ? ` · ${dayFormat.format(parseLocalDate(o.date))}` : ''}
            </option>
          ))}
        </Select>
      </FormField>
    </div>
  );
}
