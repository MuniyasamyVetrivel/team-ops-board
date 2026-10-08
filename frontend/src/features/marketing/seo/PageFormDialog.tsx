import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { applyServerErrors } from '@/lib/api/form-errors';
import { errorMessage } from '@/lib/api/errors';

import { useMarketingContext } from '../api';
import { PAGE_STATUSES, PAGE_TYPES, useCreateSeoPage, useDeleteSeoPage, useUpdateSeoPage, type SeoPageDetail } from './api';
import { PAGE_STATUS_LABELS, PAGE_TYPE_LABELS, PAGE_URL_PATTERN } from './seo-meta';

/** Mirrors SeoDtos.CreatePage / UpdatePage. */
const pageSchema = z.object({
  url: z.string().trim().min(1, 'URL is required').max(500, 'At most 500 characters').regex(PAGE_URL_PATTERN, 'Enter a path starting with / or a full http(s) URL, without spaces'),
  title: z.string().trim().min(1, 'Title is required').max(200, 'At most 200 characters'),
  pageType: z.enum(['SERVICE', 'INDUSTRY', 'LOCATION', 'BLOG', 'LANDING_PAGE', 'PRODUCT', 'OTHER']),
  primaryKeyword: z.string().trim().max(200, 'At most 200 characters'),
  departmentId: z.string().min(1, 'Department is required'),
  ownerId: z.string(),
  status: z.enum(['ACTIVE', 'INACTIVE', 'ARCHIVED']),
});

type PageValues = z.infer<typeof pageSchema>;

const SERVER_FIELDS = ['url', 'title', 'pageType', 'primaryKeyword', 'departmentId', 'ownerId', 'status'] as const;

interface PageFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this page; create a new one when absent. */
  page?: SeoPageDetail;
  onSaved?: (page: SeoPageDetail) => void;
  onDeleted?: () => void;
}

export function PageFormDialog({ open, onOpenChange, page, onSaved, onDeleted }: PageFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        {open && (
          <PageForm
            page={page}
            onCancel={() => onOpenChange(false)}
            onSaved={(saved) => {
              onOpenChange(false);
              onSaved?.(saved);
            }}
            onDeleted={() => {
              onOpenChange(false);
              onDeleted?.();
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function PageForm({ page, onCancel, onSaved, onDeleted }: { page?: SeoPageDetail; onCancel: () => void; onSaved: (page: SeoPageDetail) => void; onDeleted: () => void }) {
  const { user } = useAuth();
  const departments = useDepartments();
  const context = useMarketingContext();
  const create = useCreateSeoPage();
  const update = useUpdateSeoPage(page?.id ?? 0);
  const remove = useDeleteSeoPage();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const editing = page !== undefined;

  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<PageValues>({
    resolver: zodResolver(pageSchema),
    defaultValues: {
      url: page?.url ?? '',
      title: page?.title ?? '',
      pageType: page?.pageType ?? 'SERVICE',
      primaryKeyword: page?.primaryKeyword ?? '',
      departmentId: String(page?.department.id ?? user?.department.id ?? ''),
      ownerId: String(page ? (page.owner?.id ?? '') : (user?.id ?? '')),
      status: page?.status ?? 'ACTIVE',
    },
  });

  // Owners must be Digital Marketing users; keep a current owner listed even if they have since lost access.
  const owners = context.data?.owners ?? [];
  const ownerOptions = page?.owner && !owners.some((o) => o.id === page.owner?.id) ? [...owners, page.owner] : owners;

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const base = {
      url: values.url,
      title: values.title,
      pageType: values.pageType,
      primaryKeyword: values.primaryKeyword || null,
      departmentId: Number(values.departmentId),
      ownerId: values.ownerId ? Number(values.ownerId) : null,
    };
    try {
      const saved = editing ? await update.mutateAsync({ ...base, version: page.version, status: values.status }) : await create.mutateAsync(base);
      toast.success(editing ? 'Page updated' : 'Page added');
      onSaved(saved);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  async function onDelete() {
    if (!page) return;
    try {
      await remove.mutateAsync(page.id);
      toast.success('Page deleted');
      onDeleted();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? 'Edit page' : 'Add website page'}</DialogTitle>
        <DialogDescription>{editing ? 'Archive a page to stop tracking it; its ranking history is kept.' : 'Track a website page and the keywords it should rank for.'}</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="url" label="Page URL" required hint="A path such as /services/sap-testing, or a full URL" error={errors.url?.message}>
          <Input id="url" autoFocus={!editing} className="font-mono" placeholder="/services/sap-testing" aria-invalid={errors.url ? true : undefined} aria-describedby="url-message" {...register('url')} />
        </FormField>
        <FormField id="title" label="Page title" required error={errors.title?.message}>
          <Input id="title" aria-invalid={errors.title ? true : undefined} aria-describedby="title-message" {...register('title')} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="pageType" label="Page type" required>
            <Select id="pageType" {...register('pageType')}>
              {PAGE_TYPES.map((type) => (
                <option key={type} value={type}>
                  {PAGE_TYPE_LABELS[type]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="primaryKeyword" label="Primary keyword" error={errors.primaryKeyword?.message}>
            <Input id="primaryKeyword" aria-invalid={errors.primaryKeyword ? true : undefined} aria-describedby="primaryKeyword-message" {...register('primaryKeyword')} />
          </FormField>
          <FormField id="departmentId" label="Department" required error={errors.departmentId?.message}>
            <Select id="departmentId" aria-invalid={errors.departmentId ? true : undefined} {...register('departmentId')}>
              {departments.data
                ?.filter((d) => d.status === 'ACTIVE' || d.id === page?.department.id)
                .map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
            </Select>
          </FormField>
          <FormField id="ownerId" label="Owner" error={errors.ownerId?.message}>
            <Select id="ownerId" {...register('ownerId')}>
              <option value="">No owner</option>
              {ownerOptions.map((owner) => (
                <option key={owner.id} value={owner.id}>
                  {owner.fullName}
                </option>
              ))}
            </Select>
          </FormField>
          {editing && (
            <FormField id="status" label="Status">
              <Select id="status" {...register('status')}>
                {PAGE_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {PAGE_STATUS_LABELS[status]}
                  </option>
                ))}
              </Select>
            </FormField>
          )}
        </div>
      </DialogBody>
      <DialogFooter>
        {editing && page.keywordCount === 0 && (
          <Button
            type="button"
            variant="ghost"
            className="mr-auto text-destructive"
            disabled={remove.isPending}
            onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}
          >
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete page'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Add page'}
        </Button>
      </DialogFooter>
    </form>
  );
}
