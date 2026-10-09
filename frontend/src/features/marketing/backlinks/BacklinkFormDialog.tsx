import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle, Lock, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { applyServerErrors } from '@/lib/api/form-errors';
import { errorMessage } from '@/lib/api/errors';

import { useMarketingContext } from '../api';
import { useSeoPageOptions } from '../seo/api';
import { BACKLINK_STATUSES, BACKLINK_TYPES, STAGES, useCreateBacklink, useDeleteBacklink, useUpdateBacklink, type Backlink, type BacklinkStatus, type DateField } from './api';
import { BACKLINK_STATUS_LABELS, BACKLINK_TYPE_LABELS, moveTo, STAGE_FIELDS, STAGE_LABELS, stageAllowed, stageProblem, stageRequired, type StageDates } from './backlink-meta';

const SERVER_FIELDS = ['targetUrl', 'referringDomain', 'linkUrl', 'anchorText', 'linkType', 'status', 'submittedDate', 'approvedDate', 'liveDate', 'rejectedDate', 'lostDate', 'ownerId', 'domainAuthority', 'notes'] as const;

/** A full http(s) URL, or a site path when `allowPath`, as the server accepts. */
function isUrl(value: string, allowPath: boolean): boolean {
  if (allowPath && value.startsWith('/') && !value.startsWith('//') && !value.includes(' ')) return true;
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:';
  } catch {
    return false;
  }
}

/** Mirrors BacklinkDtos.SaveBacklink and BacklinkRules (`today` is the business date). */
function backlinkSchema(today: string) {
  return z
    .object({
      targetPageId: z.string(),
      targetUrl: z
        .string()
        .trim()
        .max(500, 'At most 500 characters')
        .refine((v) => v === '' || isUrl(v, true), 'Enter a URL (https://…) or a path starting with /'),
      linkUrl: z
        .string()
        .trim()
        .max(700, 'At most 700 characters')
        .refine((v) => v === '' || isUrl(v, false), 'Enter the full URL (https://…) of the page that links to us'),
      referringDomain: z.string().trim().max(255, 'At most 255 characters'),
      anchorText: z.string().max(255, 'At most 255 characters'),
      linkType: z.enum(['GUEST_POST', 'DIRECTORY', 'BUSINESS_LISTING', 'PROFILE', 'FORUM', 'SOCIAL_BOOKMARK', 'PRESS_RELEASE', 'RESOURCE_PAGE', 'BLOG_COMMENT', 'OTHER']),
      status: z.enum(['PROSPECTED', 'SUBMITTED', 'APPROVED', 'LIVE', 'REJECTED', 'LOST']),
      submittedDate: z.string(),
      approvedDate: z.string(),
      liveDate: z.string(),
      rejectedDate: z.string(),
      lostDate: z.string(),
      ownerId: z.string(),
      domainAuthority: z
        .string()
        .trim()
        .refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) <= 100), 'A whole number from 0 to 100'),
      notes: z.string().max(2000, 'At most 2000 characters'),
    })
    .superRefine((v, ctx) => {
      if (!v.targetPageId && !v.targetUrl) ctx.addIssue({ code: 'custom', path: ['targetUrl'], message: 'Choose our page, or enter the URL the link points to' });
      if (!v.referringDomain && !v.linkUrl) ctx.addIssue({ code: 'custom', path: ['referringDomain'], message: 'Enter the referring domain or the link URL' });
      const problem = stageProblem(v.status, datesOf(v), v.linkUrl, today);
      if (problem) ctx.addIssue({ code: 'custom', path: [problem.field], message: problem.message });
    });
}

type Values = z.infer<ReturnType<typeof backlinkSchema>>;

const datesOf = (v: Pick<Values, DateField>): StageDates => ({
  submittedDate: v.submittedDate || null,
  approvedDate: v.approvedDate || null,
  liveDate: v.liveDate || null,
  rejectedDate: v.rejectedDate || null,
  lostDate: v.lostDate || null,
});

interface BacklinkFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this backlink; create a new one when absent. */
  backlink?: Backlink;
  onCreated?: (id: number) => void;
  onDeleted?: () => void;
}

export function BacklinkFormDialog({ open, onOpenChange, backlink, onCreated, onDeleted }: BacklinkFormDialogProps) {
  // "Today" (latest stage date) and the owner list come from the marketing context.
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
        {open && context.data && <BacklinkForm backlink={backlink} today={context.data.today} onDone={() => onOpenChange(false)} onCreated={onCreated} onDeleted={onDeleted} />}
      </DialogContent>
    </Dialog>
  );
}

interface BacklinkFormProps {
  backlink?: Backlink;
  today: string;
  onDone: () => void;
  onCreated?: (id: number) => void;
  onDeleted?: () => void;
}

function BacklinkForm({ backlink, today, onDone, onCreated, onDeleted }: BacklinkFormProps) {
  const { user } = useAuth();
  const context = useMarketingContext();
  const canSeo = hasPermission(user, 'SEO_VIEW');
  const pages = useSeoPageOptions(canSeo);
  const create = useCreateBacklink();
  const update = useUpdateBacklink(backlink?.id ?? 0);
  const remove = useDeleteBacklink();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const editing = backlink !== undefined;
  const locked = new Set(backlink?.lockedDates ?? []);

  const owners = context.data?.owners ?? [];
  const ownerOptions = backlink?.owner && !owners.some((o) => o.id === backlink.owner?.id) ? [...owners, backlink.owner] : owners;
  // A page picked from the list fills the URL on the server; keep the field for other targets.
  const pageUrl = backlink?.targetPage?.url;

  const { register, control, handleSubmit, setError, setValue, getValues, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(backlinkSchema(today)),
    defaultValues: {
      targetPageId: backlink?.targetPage && canSeo ? String(backlink.targetPage.id) : '',
      targetUrl: backlink && !(canSeo && backlink.targetPage && backlink.targetUrl === pageUrl) ? backlink.targetUrl : '',
      linkUrl: backlink?.linkUrl ?? '',
      referringDomain: backlink?.referringDomain ?? '',
      anchorText: backlink?.anchorText ?? '',
      linkType: backlink?.linkType ?? 'GUEST_POST',
      status: backlink?.status ?? 'PROSPECTED',
      submittedDate: backlink?.submittedDate ?? '',
      approvedDate: backlink?.approvedDate ?? '',
      liveDate: backlink?.liveDate ?? '',
      rejectedDate: backlink?.rejectedDate ?? '',
      lostDate: backlink?.lostDate ?? '',
      ownerId: String(backlink ? (backlink.owner?.id ?? '') : owners.some((o) => o.id === user?.id) ? user?.id : ''),
      domainAuthority: backlink?.domainAuthority == null ? '' : String(backlink.domainAuthority),
      notes: backlink?.notes ?? '',
    },
  });
  const status = useWatch({ control, name: 'status' });

  /** Like a status move on the server: the stages the status needs are dated today, the others cleared. */
  function onStatusChange(next: BacklinkStatus) {
    const moved = moveTo(next, datesOf(getValues()), today);
    for (const stage of STAGES) {
      const field = STAGE_FIELDS[stage];
      if (!locked.has(field)) setValue(field, moved[field] ?? '');
    }
  }

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const dates = datesOf(values);
    const body = {
      targetPageId: values.targetPageId ? Number(values.targetPageId) : null,
      targetUrl: values.targetUrl.trim() || null,
      referringDomain: values.referringDomain.trim() || null,
      linkUrl: values.linkUrl.trim() || null,
      anchorText: values.anchorText.trim() || null,
      linkType: values.linkType,
      status: values.status,
      ...dates,
      ownerId: values.ownerId ? Number(values.ownerId) : null,
      domainAuthority: values.domainAuthority.trim() === '' ? null : Number(values.domainAuthority),
      notes: values.notes.trim() || null,
    };
    try {
      if (editing) {
        await update.mutateAsync({ ...body, version: backlink.version });
        toast.success('Backlink updated');
        onDone();
      } else {
        const created = await create.mutateAsync(body);
        toast.success('Backlink added');
        onDone();
        if (typeof created !== 'number') onCreated?.(created.id);
      }
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  async function onDelete() {
    if (!backlink) return;
    try {
      await remove.mutateAsync(backlink.id);
      toast.success(`${backlink.code} deleted`);
      onDone();
      onDeleted?.();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  const field = (name: keyof Values) => ({ 'aria-invalid': errors[name] ? true : undefined, 'aria-describedby': `backlink-${name}-message` });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? `Edit ${backlink.code}` : 'New backlink'}</DialogTitle>
        <DialogDescription>Each stage counts in the month of its date; links that go live count towards the Backlinks target.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <FormBanner message={banner} />
        <div className="grid gap-4 sm:grid-cols-2">
          {canSeo && (
            <FormField id="backlink-targetPageId" label="Our page">
              <Select id="backlink-targetPageId" disabled={pages.isPending} {...register('targetPageId')}>
                <option value="">Another URL</option>
                {backlink?.targetPage && !(pages.data ?? []).some((p) => p.id === backlink.targetPage?.id) && <option value={backlink.targetPage.id}>{backlink.targetPage.title}</option>}
                {(pages.data ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.title}
                  </option>
                ))}
              </Select>
            </FormField>
          )}
          <FormField id="backlink-targetUrl" label="Target URL" error={errors.targetUrl?.message} hint={canSeo ? 'Leave empty to use the page’s URL' : 'Our page the link points to'} className={canSeo ? undefined : 'sm:col-span-2'}>
            <Input id="backlink-targetUrl" placeholder="/services/sap-testing" {...field('targetUrl')} {...register('targetUrl')} />
          </FormField>
          <FormField id="backlink-linkUrl" label="Link URL" required={status === 'LIVE' || status === 'LOST'} error={errors.linkUrl?.message} hint="The page on the referring site" className="sm:col-span-2">
            <Input id="backlink-linkUrl" placeholder="https://dzone.com/articles/…" {...field('linkUrl')} {...register('linkUrl')} />
          </FormField>
          <FormField id="backlink-referringDomain" label="Referring domain" error={errors.referringDomain?.message} hint="Defaults to the link URL’s domain">
            <Input id="backlink-referringDomain" placeholder="dzone.com" {...field('referringDomain')} {...register('referringDomain')} />
          </FormField>
          <FormField id="backlink-anchorText" label="Anchor text" error={errors.anchorText?.message}>
            <Input id="backlink-anchorText" {...field('anchorText')} {...register('anchorText')} />
          </FormField>
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <FormField id="backlink-linkType" label="Type" required>
            <Select id="backlink-linkType" {...register('linkType')}>
              {BACKLINK_TYPES.map((t) => (
                <option key={t} value={t}>
                  {BACKLINK_TYPE_LABELS[t]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="backlink-status" label="Status" required error={errors.status?.message}>
            <Select id="backlink-status" {...register('status', { onChange: (e: { target: { value: BacklinkStatus } }) => onStatusChange(e.target.value) })}>
              {BACKLINK_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {BACKLINK_STATUS_LABELS[s]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="backlink-domainAuthority" label="Domain authority" error={errors.domainAuthority?.message} hint="0–100, where available">
            <Input id="backlink-domainAuthority" inputMode="numeric" {...field('domainAuthority')} {...register('domainAuthority')} />
          </FormField>
        </div>

        <fieldset className="space-y-2 rounded-lg border p-3">
          <legend className="px-1 text-sm font-medium">Stage dates</legend>
          {locked.size > 0 && (
            <p className="flex items-start gap-2 text-xs text-muted-foreground">
              <Lock className="mt-0.5 size-3.5 shrink-0" aria-hidden />
              Dates in closed months stay as counted. Later stages can still be added.
            </p>
          )}
          <div className="grid gap-4 sm:grid-cols-5">
            {STAGES.map((stage) => {
              const name = STAGE_FIELDS[stage];
              return (
                <FormField key={stage} id={`backlink-${name}`} label={STAGE_LABELS[stage]} required={stageRequired(status, stage)} error={errors[name]?.message}>
                  <Input id={`backlink-${name}`} type="date" max={today} disabled={!stageAllowed(status, stage) || locked.has(name)} {...field(name)} {...register(name)} />
                </FormField>
              );
            })}
          </div>
        </fieldset>

        <FormField id="backlink-ownerId" label="Owner">
          <Select id="backlink-ownerId" {...register('ownerId')}>
            <option value="">No owner</option>
            {ownerOptions.map((o) => (
              <option key={o.id} value={o.id}>
                {o.fullName}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField id="backlink-notes" label="Notes" error={errors.notes?.message}>
          <Textarea id="backlink-notes" rows={2} {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && locked.size === 0 && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete backlink'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Add backlink'}
        </Button>
      </DialogFooter>
    </form>
  );
}
