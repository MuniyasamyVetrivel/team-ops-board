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
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
import { useAuth } from '@/features/auth/use-auth';
import { applyServerErrors } from '@/lib/api/form-errors';
import { errorMessage } from '@/lib/api/errors';

import { useMarketingContext } from '../api';
import { AD_PLATFORMS, CAMPAIGN_OBJECTIVES, PAID_STATUSES, useCreatePaidCampaign, useDeletePaidCampaign, useUpdatePaidCampaign, type PaidCampaign } from './api';
import { moneyField, OBJECTIVE_LABELS, PAID_STATUS_LABELS, parseNumber, PLATFORM_LABELS } from './paid-meta';

const SERVER_FIELDS = ['name', 'platform', 'objective', 'startDate', 'endDate', 'budget', 'ownerId', 'status', 'notes'] as const;

/** Mirrors PaidCampaignDtos.SaveCampaign. */
const schema = z
  .object({
    name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
    platform: z.enum(['LINKEDIN', 'GOOGLE_ADS', 'META', 'OTHER']),
    objective: z.enum(['BRAND_AWARENESS', 'WEBSITE_VISITS', 'ENGAGEMENT', 'VIDEO_VIEWS', 'LEAD_GENERATION', 'WEBSITE_CONVERSIONS', 'JOB_APPLICANTS']),
    status: z.enum(['DRAFT', 'ACTIVE', 'PAUSED', 'COMPLETED']),
    startDate: z.string().min(1, 'Start date is required'),
    endDate: z.string(),
    budget: moneyField('Budget is required').refine((v) => v.trim() === '' || parseNumber(v) > 0, 'Must be more than 0'),
    ownerId: z.string(),
    notes: z.string().max(2000, 'At most 2000 characters'),
  })
  .refine((v) => v.endDate === '' || v.endDate >= v.startDate, { path: ['endDate'], message: 'The end date cannot be before the start date' });

type Values = z.infer<typeof schema>;

interface PaidCampaignFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this campaign; create a new one when absent. */
  campaign?: PaidCampaign;
  /** Whether results are recorded: the campaign then cannot go back to draft or be deleted. */
  hasResults?: boolean;
  onCreated?: (id: number) => void;
  onDeleted?: () => void;
}

export function PaidCampaignFormDialog({ open, onOpenChange, campaign, hasResults = false, onCreated, onDeleted }: PaidCampaignFormDialogProps) {
  // "Today" (default start date) and the owner list come from the marketing context.
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
        {open && context.data && (
          <CampaignForm
            campaign={campaign}
            hasResults={hasResults}
            today={context.data.today}
            onDone={() => onOpenChange(false)}
            onCreated={onCreated}
            onDeleted={onDeleted}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

interface CampaignFormProps {
  campaign?: PaidCampaign;
  hasResults: boolean;
  today: string;
  onDone: () => void;
  onCreated?: (id: number) => void;
  onDeleted?: () => void;
}

function CampaignForm({ campaign, hasResults, today, onDone, onCreated, onDeleted }: CampaignFormProps) {
  const { user } = useAuth();
  const context = useMarketingContext();
  const create = useCreatePaidCampaign();
  const update = useUpdatePaidCampaign(campaign?.id ?? 0);
  const remove = useDeletePaidCampaign();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const editing = campaign !== undefined;

  const owners = context.data?.owners ?? [];
  const ownerOptions = campaign?.owner && !owners.some((o) => o.id === campaign.owner?.id) ? [...owners, campaign.owner] : owners;
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: campaign?.name ?? '',
      platform: campaign?.platform ?? 'LINKEDIN',
      objective: campaign?.objective ?? 'LEAD_GENERATION',
      status: campaign?.status ?? 'ACTIVE',
      startDate: campaign?.startDate ?? today,
      endDate: campaign?.endDate ?? '',
      budget: campaign ? String(campaign.budget) : '',
      ownerId: String(campaign ? (campaign.owner?.id ?? '') : owners.some((o) => o.id === user?.id) ? user?.id : ''),
      notes: campaign?.notes ?? '',
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const body = {
      name: values.name,
      platform: values.platform,
      objective: values.objective,
      status: values.status,
      startDate: values.startDate,
      endDate: values.endDate || null,
      budget: parseNumber(values.budget),
      ownerId: values.ownerId ? Number(values.ownerId) : null,
      notes: values.notes.trim() || null,
    };
    try {
      if (editing) {
        await update.mutateAsync({ ...body, version: campaign.version });
        toast.success('Campaign updated');
        onDone();
      } else {
        const created = await create.mutateAsync(body);
        toast.success(`${values.name} added`);
        onDone();
        if (typeof created !== 'number') onCreated?.(created.campaign.id);
      }
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  async function onDelete() {
    if (!campaign) return;
    try {
      await remove.mutateAsync(campaign.id);
      toast.success('Campaign deleted');
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
        <DialogTitle>{editing ? 'Edit campaign' : 'New paid campaign'}</DialogTitle>
        <DialogDescription>The plan: dates and budget. Monthly results (spend, clicks, leads) are recorded on the campaign.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <FormBanner message={banner} />
        <FormField id="paid-name" label="Campaign name" required error={errors.name?.message}>
          <Input id="paid-name" autoFocus aria-invalid={errors.name ? true : undefined} aria-describedby="paid-name-message" {...register('name')} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField id="paid-platform" label="Platform" required>
            <Select id="paid-platform" {...register('platform')}>
              {AD_PLATFORMS.map((p) => (
                <option key={p} value={p}>
                  {PLATFORM_LABELS[p]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="paid-objective" label="Objective" required>
            <Select id="paid-objective" {...register('objective')}>
              {CAMPAIGN_OBJECTIVES.map((o) => (
                <option key={o} value={o}>
                  {OBJECTIVE_LABELS[o]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="paid-status" label="Status" required error={errors.status?.message}>
            <Select id="paid-status" {...register('status')}>
              {PAID_STATUSES.map((s) => (
                // A campaign with results cannot go back to draft.
                <option key={s} value={s} disabled={s === 'DRAFT' && hasResults}>
                  {PAID_STATUS_LABELS[s]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="paid-start" label="Start date" required error={errors.startDate?.message}>
            <Input id="paid-start" type="date" aria-invalid={errors.startDate ? true : undefined} aria-describedby="paid-start-message" {...register('startDate')} />
          </FormField>
          <FormField id="paid-end" label="End date" error={errors.endDate?.message}>
            <Input id="paid-end" type="date" aria-invalid={errors.endDate ? true : undefined} aria-describedby="paid-end-message" {...register('endDate')} />
          </FormField>
          <FormField id="paid-budget" label="Budget (₹)" required error={errors.budget?.message}>
            <Input id="paid-budget" inputMode="decimal" placeholder="50,000" aria-invalid={errors.budget ? true : undefined} aria-describedby="paid-budget-message" {...register('budget')} />
          </FormField>
          <FormField id="paid-owner" label="Owner">
            <Select id="paid-owner" {...register('ownerId')}>
              <option value="">No owner</option>
              {ownerOptions.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.fullName}
                </option>
              ))}
            </Select>
          </FormField>
        </div>
        {hasResults && <p className="rounded-lg border bg-muted/40 px-3 py-2.5 text-sm text-muted-foreground">Results are recorded for this campaign: its dates must keep covering those months.</p>}
        <FormField id="paid-notes" label="Notes" error={errors.notes?.message}>
          <Textarea id="paid-notes" rows={2} {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && !hasResults && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete campaign'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Add campaign'}
        </Button>
      </DialogFooter>
    </form>
  );
}
