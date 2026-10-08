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
import { applyServerErrors } from '@/lib/api/form-errors';
import { errorMessage } from '@/lib/api/errors';

import { useMarketingContext } from '../api';
import { DEVICES, KEYWORD_STATUSES, SEARCH_ENGINES, useCreateKeyword, useDeleteKeyword, useSeoPageOptions, useUpdateKeyword, type KeywordItem } from './api';
import { DEVICE_LABELS, KEYWORD_STATUS_LABELS, SEARCH_ENGINE_LABELS } from './seo-meta';

/** An optional whole number within [min, max]; empty means "not set". */
const optionalInt = (min: number, max: number, message: string) =>
  z.string().trim().refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) >= min && Number(v) <= max), message);

/** Mirrors SeoDtos.CreateKeyword / UpdateKeyword. */
const keywordSchema = z.object({
  pageId: z.string().min(1, 'Page is required'),
  keyword: z.string().trim().min(1, 'Keyword is required').max(200, 'At most 200 characters'),
  searchEngine: z.enum(['GOOGLE', 'BING']),
  location: z.string().trim().max(100, 'At most 100 characters'),
  device: z.enum(['DESKTOP', 'MOBILE']),
  targetPosition: optionalInt(1, 100, 'Enter a position from 1 to 100'),
  searchVolume: optionalInt(0, 1_000_000_000, 'Enter a whole number of searches'),
  keywordDifficulty: optionalInt(0, 100, 'Enter a score from 0 to 100'),
  ownerId: z.string(),
  status: z.enum(['ACTIVE', 'PAUSED', 'ARCHIVED']),
});

type KeywordValues = z.infer<typeof keywordSchema>;

const SERVER_FIELDS = ['pageId', 'keyword', 'location', 'targetPosition', 'searchVolume', 'keywordDifficulty', 'ownerId', 'status'] as const;

const toNumber = (value: string) => (value === '' ? null : Number(value));

interface KeywordFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this keyword; create a new one when absent. */
  keyword?: KeywordItem;
  /** Preselects the page (and its owner) for a new keyword. */
  page?: { id: number; ownerId: number | null };
}

export function KeywordFormDialog({ open, onOpenChange, keyword, page }: KeywordFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">{open && <KeywordForm keyword={keyword} page={page} onDone={() => onOpenChange(false)} />}</DialogContent>
    </Dialog>
  );
}

function KeywordForm({ keyword, page, onDone }: { keyword?: KeywordItem; page?: { id: number; ownerId: number | null }; onDone: () => void }) {
  const { user } = useAuth();
  const context = useMarketingContext();
  const pages = useSeoPageOptions();
  const create = useCreateKeyword();
  const update = useUpdateKeyword(keyword?.id ?? 0);
  const remove = useDeleteKeyword();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const editing = keyword !== undefined;

  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<KeywordValues>({
    resolver: zodResolver(keywordSchema),
    defaultValues: {
      pageId: String(keyword?.page.id ?? page?.id ?? ''),
      keyword: keyword?.keyword ?? '',
      searchEngine: keyword?.searchEngine ?? 'GOOGLE',
      location: keyword?.location ?? 'India',
      device: keyword?.device ?? 'DESKTOP',
      targetPosition: keyword?.targetPosition?.toString() ?? '',
      searchVolume: keyword?.searchVolume?.toString() ?? '',
      keywordDifficulty: keyword?.keywordDifficulty?.toString() ?? '',
      ownerId: String(keyword ? (keyword.owner?.id ?? '') : (page?.ownerId ?? user?.id ?? '')),
      status: keyword?.status ?? 'ACTIVE',
    },
  });

  const owners = context.data?.owners ?? [];
  const ownerOptions = keyword?.owner && !owners.some((o) => o.id === keyword.owner?.id) ? [...owners, keyword.owner] : owners;
  // An archived page is no longer offered, but a keyword already on it keeps showing it.
  const pageOptions = keyword && !pages.data?.some((p) => p.id === keyword.page.id) ? [...(pages.data ?? []), keyword.page] : (pages.data ?? []);

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const base = {
      pageId: Number(values.pageId),
      keyword: values.keyword,
      searchEngine: values.searchEngine,
      location: values.location || 'India',
      device: values.device,
      targetPosition: toNumber(values.targetPosition),
      searchVolume: toNumber(values.searchVolume),
      keywordDifficulty: toNumber(values.keywordDifficulty),
      ownerId: values.ownerId ? Number(values.ownerId) : null,
    };
    try {
      await (editing ? update.mutateAsync({ ...base, version: keyword.version, status: values.status }) : create.mutateAsync(base));
      toast.success(editing ? 'Keyword updated' : 'Keyword added');
      onDone();
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  async function onDelete() {
    if (!keyword) return;
    try {
      await remove.mutateAsync(keyword.id);
      toast.success('Keyword deleted');
      onDone();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? 'Edit keyword' : 'Add keyword'}</DialogTitle>
        <DialogDescription>Monthly positions are recorded separately and never overwritten.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="keyword" label="Keyword" required error={errors.keyword?.message}>
          <Input id="keyword" autoFocus={!editing} aria-invalid={errors.keyword ? true : undefined} aria-describedby="keyword-message" {...register('keyword')} />
        </FormField>
        <FormField id="pageId" label="Page" required error={errors.pageId?.message}>
          <Select id="pageId" aria-invalid={errors.pageId ? true : undefined} {...register('pageId')}>
            <option value="">Choose a page</option>
            {pageOptions.map((p) => (
              <option key={p.id} value={p.id}>
                {p.title} ({p.url})
              </option>
            ))}
          </Select>
        </FormField>
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField id="searchEngine" label="Search engine">
            <Select id="searchEngine" {...register('searchEngine')}>
              {SEARCH_ENGINES.map((engine) => (
                <option key={engine} value={engine}>
                  {SEARCH_ENGINE_LABELS[engine]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="location" label="Location" error={errors.location?.message}>
            <Input id="location" aria-invalid={errors.location ? true : undefined} aria-describedby="location-message" {...register('location')} />
          </FormField>
          <FormField id="device" label="Device">
            <Select id="device" {...register('device')}>
              {DEVICES.map((device) => (
                <option key={device} value={device}>
                  {DEVICE_LABELS[device]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="targetPosition" label="Target position" error={errors.targetPosition?.message}>
            <Input id="targetPosition" inputMode="numeric" aria-invalid={errors.targetPosition ? true : undefined} aria-describedby="targetPosition-message" {...register('targetPosition')} />
          </FormField>
          <FormField id="searchVolume" label="Monthly searches" error={errors.searchVolume?.message}>
            <Input id="searchVolume" inputMode="numeric" aria-invalid={errors.searchVolume ? true : undefined} aria-describedby="searchVolume-message" {...register('searchVolume')} />
          </FormField>
          <FormField id="keywordDifficulty" label="Difficulty (0–100)" error={errors.keywordDifficulty?.message}>
            <Input id="keywordDifficulty" inputMode="numeric" aria-invalid={errors.keywordDifficulty ? true : undefined} aria-describedby="keywordDifficulty-message" {...register('keywordDifficulty')} />
          </FormField>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
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
            <FormField id="status" label="Status" hint="Paused keywords are not tracked; archived ones leave the page statistics">
              <Select id="status" aria-describedby="status-message" {...register('status')}>
                {KEYWORD_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {KEYWORD_STATUS_LABELS[status]}
                  </option>
                ))}
              </Select>
            </FormField>
          )}
        </div>
      </DialogBody>
      <DialogFooter>
        {editing && !keyword.ranking.recorded && !keyword.lastRankedAt && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete keyword'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Add keyword'}
        </Button>
      </DialogFooter>
    </form>
  );
}
