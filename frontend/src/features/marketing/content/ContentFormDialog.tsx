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
import { useSeoKeywords, useSeoPageOptions } from '../seo/api';
import { CONTENT_STATUSES, CONTENT_TYPES, useCreateContent, useDeleteContent, useUpdateContent, type ContentItem, type ContentStatus } from './api';
import { CONTENT_STATUS_LABELS, CONTENT_TYPE_LABELS, contentDateProblem, isLive } from './content-meta';

const SERVER_FIELDS = ['title', 'url', 'contentType', 'status', 'authorId', 'ownerId', 'plannedDate', 'publicationDate', 'refreshedDate', 'targetKeywordText', 'organicTraffic', 'ctaClicks', 'notes'] as const;

const MAX_COUNT = 1_000_000_000;
const strip = (v: string) => v.trim().replace(/,/g, '');
const countField = z
  .string()
  .refine((v) => strip(v) === '' || /^\d+$/.test(strip(v)), 'Enter a whole number')
  .refine((v) => strip(v) === '' || Number(strip(v)) <= MAX_COUNT, 'Too large');
const toCount = (v: string) => (strip(v) === '' ? null : Number(strip(v)));

/** A full URL or a site path, as the server accepts. */
function isUrl(value: string): boolean {
  if (value.startsWith('/') && !value.startsWith('//') && !value.includes(' ')) return true;
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:';
  } catch {
    return false;
  }
}

/** Mirrors ContentDtos.SaveContent and ContentRules (`today` is the business date). */
function contentSchema(today: string) {
  return z
    .object({
      title: z.string().trim().min(1, 'Title is required').max(300, 'At most 300 characters'),
      url: z
        .string()
        .trim()
        .max(1000, 'At most 1000 characters')
        .refine((v) => v === '' || isUrl(v), 'Enter a full URL (https://…) or a path starting with /'),
      contentType: z.enum(['BLOG', 'CASE_STUDY', 'WHITEPAPER', 'LANDING_PAGE', 'OTHER']),
      status: z.enum(['IDEA', 'PLANNED', 'IN_PROGRESS', 'DRAFT', 'PUBLISHED', 'UPDATED']),
      authorId: z.string(),
      ownerId: z.string(),
      plannedDate: z.string(),
      publicationDate: z.string(),
      refreshedDate: z.string(),
      targetPageId: z.string(),
      targetKeywordId: z.string(),
      targetKeywordText: z.string().max(255, 'At most 255 characters'),
      organicTraffic: countField,
      ctaClicks: countField,
      notes: z.string().max(2000, 'At most 2000 characters'),
    })
    .superRefine((v, ctx) => {
      const problem = contentDateProblem(v, today);
      if (problem) ctx.addIssue({ code: 'custom', path: [problem.field], message: problem.message });
    });
}

type Values = z.infer<ReturnType<typeof contentSchema>>;

interface ContentFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this item; create a new one when absent. */
  item?: ContentItem;
  onCreated?: (id: number) => void;
  onDeleted?: () => void;
}

export function ContentFormDialog({ open, onOpenChange, item, onCreated, onDeleted }: ContentFormDialogProps) {
  // "Today" (latest publication date) and the people lists come from the marketing context.
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
        {open && context.data && <ContentForm item={item} today={context.data.today} onDone={() => onOpenChange(false)} onCreated={onCreated} onDeleted={onDeleted} />}
      </DialogContent>
    </Dialog>
  );
}

interface ContentFormProps {
  item?: ContentItem;
  today: string;
  onDone: () => void;
  onCreated?: (id: number) => void;
  onDeleted?: () => void;
}

function ContentForm({ item, today, onDone, onCreated, onDeleted }: ContentFormProps) {
  const { user } = useAuth();
  const context = useMarketingContext();
  const canSeo = hasPermission(user, 'SEO_VIEW');
  const pages = useSeoPageOptions(canSeo);
  const create = useCreateContent();
  const update = useUpdateContent(item?.id ?? 0);
  const remove = useDeleteContent();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const editing = item !== undefined;
  const locked = item?.publicationLocked ?? false;
  const hasLeads = (item?.leads ?? 0) > 0;

  const people = context.data?.owners ?? [];
  const withCurrent = (u: ContentItem['owner']) => (u && !people.some((p) => p.id === u.id) ? [u] : []);
  const ownerOptions = [...people, ...withCurrent(item?.owner ?? null)];
  const authorOptions = [...people, ...withCurrent(item?.author ?? null)];
  const meInList = people.some((p) => p.id === user?.id);

  const { register, control, handleSubmit, setError, setValue, getValues, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(contentSchema(today)),
    defaultValues: {
      title: item?.title ?? '',
      url: item?.url ?? '',
      contentType: item?.contentType ?? 'BLOG',
      status: item?.status ?? 'IDEA',
      authorId: String(item ? (item.author?.id ?? '') : meInList ? user?.id : ''),
      ownerId: String(item ? (item.owner?.id ?? '') : meInList ? user?.id : ''),
      plannedDate: item?.plannedDate ?? '',
      publicationDate: item?.publicationDate ?? '',
      refreshedDate: item?.refreshedDate ?? '',
      targetPageId: item?.targetPage ? String(item.targetPage.id) : '',
      targetKeywordId: item?.targetKeyword ? String(item.targetKeyword.id) : '',
      targetKeywordText: item?.targetKeywordText ?? '',
      organicTraffic: item?.organicTraffic == null ? '' : String(item.organicTraffic),
      ctaClicks: item?.ctaClicks == null ? '' : String(item.ctaClicks),
      notes: item?.notes ?? '',
    },
  });
  const status = useWatch({ control, name: 'status' });
  const pageId = useWatch({ control, name: 'targetPageId' });
  const keywords = useSeoKeywords({ pageId: pageId ? Number(pageId) : undefined, size: 100 }, canSeo && pageId !== '');
  const live = isLive(status);

  /** Like a status move on the server: publishing dates it today, earlier stages clear the dates. */
  function onStatusChange(next: ContentStatus) {
    if (!isLive(next)) {
      setValue('publicationDate', '');
      setValue('refreshedDate', '');
      return;
    }
    if (!getValues('publicationDate')) setValue('publicationDate', today);
    if (next === 'UPDATED' && !getValues('refreshedDate')) setValue('refreshedDate', today);
    if (next === 'PUBLISHED') setValue('refreshedDate', '');
  }

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const body = {
      title: values.title.trim(),
      url: values.url.trim() || null,
      contentType: values.contentType,
      status: values.status,
      authorId: values.authorId ? Number(values.authorId) : null,
      ownerId: values.ownerId ? Number(values.ownerId) : null,
      plannedDate: values.plannedDate || null,
      publicationDate: values.publicationDate || null,
      refreshedDate: values.refreshedDate || null,
      targetPageId: values.targetPageId ? Number(values.targetPageId) : null,
      targetKeywordId: values.targetKeywordId ? Number(values.targetKeywordId) : null,
      targetKeywordText: values.targetKeywordText.trim() || null,
      organicTraffic: toCount(values.organicTraffic),
      ctaClicks: toCount(values.ctaClicks),
      notes: values.notes.trim() || null,
    };
    try {
      if (editing) {
        await update.mutateAsync({ ...body, version: item.version });
        toast.success('Content updated');
        onDone();
      } else {
        const created = await create.mutateAsync(body);
        toast.success(`${body.title} added`);
        onDone();
        if (typeof created !== 'number') onCreated?.(created.id);
      }
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  async function onDelete() {
    if (!item) return;
    try {
      await remove.mutateAsync(item.id);
      toast.success('Content deleted');
      onDone();
      onDeleted?.();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  const field = (name: keyof Values) => ({ 'aria-invalid': errors[name] ? true : undefined, 'aria-describedby': `content-${name}-message` });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? 'Edit content' : 'New content'}</DialogTitle>
        <DialogDescription>Blog posts count towards the blog target in the month they are published.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <FormBanner message={banner} />
        <FormField id="content-title" label="Title" required error={errors.title?.message}>
          <Input id="content-title" autoFocus {...field('title')} {...register('title')} />
        </FormField>
        {locked && (
          <p className="flex items-start gap-2 rounded-lg border bg-muted/40 px-3 py-2.5 text-sm text-muted-foreground">
            <Lock className="mt-0.5 size-4 shrink-0" aria-hidden />
            Published in a closed month, so its status, type and publication date stay as counted. Everything else can still change.
          </p>
        )}
        {hasLeads && !locked && <p className="rounded-lg border bg-muted/40 px-3 py-2.5 text-sm text-muted-foreground">Leads name this content, so it stays published and cannot be deleted.</p>}
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField id="content-contentType" label="Type" required>
            <Select id="content-contentType" disabled={locked} {...register('contentType')}>
              {CONTENT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {CONTENT_TYPE_LABELS[t]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="content-status" label="Status" required error={errors.status?.message}>
            <Select id="content-status" disabled={locked} {...register('status', { onChange: (e: { target: { value: ContentStatus } }) => onStatusChange(e.target.value) })}>
              {CONTENT_STATUSES.map((s) => (
                <option key={s} value={s} disabled={hasLeads && !isLive(s)}>
                  {CONTENT_STATUS_LABELS[s]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="content-plannedDate" label="Planned for" error={errors.plannedDate?.message} hint="The month it is planned for">
            <Input id="content-plannedDate" type="date" {...field('plannedDate')} {...register('plannedDate')} />
          </FormField>
          <FormField id="content-publicationDate" label="Published on" required={live} error={errors.publicationDate?.message}>
            <Input id="content-publicationDate" type="date" max={today} disabled={!live || locked} {...field('publicationDate')} {...register('publicationDate')} />
          </FormField>
          <FormField id="content-refreshedDate" label="Refreshed on" required={status === 'UPDATED'} error={errors.refreshedDate?.message}>
            <Input id="content-refreshedDate" type="date" max={today} disabled={status !== 'UPDATED'} {...field('refreshedDate')} {...register('refreshedDate')} />
          </FormField>
          <FormField id="content-url" label="URL" required={live} error={errors.url?.message} className="sm:col-span-3">
            <Input id="content-url" placeholder="https://www.example.com/blog/…" {...field('url')} {...register('url')} />
          </FormField>
          <FormField id="content-authorId" label="Author">
            <Select id="content-authorId" {...register('authorId')}>
              <option value="">No author</option>
              {authorOptions.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.fullName}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="content-ownerId" label="Owner">
            <Select id="content-ownerId" {...register('ownerId')}>
              <option value="">No owner</option>
              {ownerOptions.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.fullName}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="content-targetKeywordText" label="Target keyword" error={errors.targetKeywordText?.message}>
            <Input id="content-targetKeywordText" {...field('targetKeywordText')} {...register('targetKeywordText')} />
          </FormField>
          {canSeo && (
            <>
              <FormField id="content-targetPageId" label="Target page" className="sm:col-span-2">
                <Select id="content-targetPageId" disabled={pages.isPending} {...register('targetPageId', { onChange: () => setValue('targetKeywordId', '') })}>
                  <option value="">No SEO page</option>
                  {item?.targetPage && !(pages.data ?? []).some((p) => p.id === item.targetPage?.id) && <option value={item.targetPage.id}>{item.targetPage.title}</option>}
                  {(pages.data ?? []).map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.title}
                    </option>
                  ))}
                </Select>
              </FormField>
              <FormField id="content-targetKeywordId" label="SEO keyword" hint={pageId ? undefined : 'Choose the target page first'}>
                <Select id="content-targetKeywordId" disabled={!pageId || keywords.isPending} {...register('targetKeywordId')}>
                  <option value="">No tracked keyword</option>
                  {item?.targetKeyword && !(keywords.data?.content ?? []).some((k) => k.id === item.targetKeyword?.id) && <option value={item.targetKeyword.id}>{item.targetKeyword.keyword}</option>}
                  {(keywords.data?.content ?? []).map((k) => (
                    <option key={k.id} value={k.id}>
                      {k.keyword}
                    </option>
                  ))}
                </Select>
              </FormField>
            </>
          )}
          <FormField id="content-organicTraffic" label="Organic traffic" error={errors.organicTraffic?.message} hint="Entered by hand">
            <Input id="content-organicTraffic" inputMode="numeric" {...field('organicTraffic')} {...register('organicTraffic')} />
          </FormField>
          <FormField id="content-ctaClicks" label="CTA clicks" error={errors.ctaClicks?.message}>
            <Input id="content-ctaClicks" inputMode="numeric" {...field('ctaClicks')} {...register('ctaClicks')} />
          </FormField>
        </div>
        <FormField id="content-notes" label="Notes" error={errors.notes?.message}>
          <Textarea id="content-notes" rows={2} {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && !locked && !hasLeads && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete content'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Add content'}
        </Button>
      </DialogFooter>
    </form>
  );
}
