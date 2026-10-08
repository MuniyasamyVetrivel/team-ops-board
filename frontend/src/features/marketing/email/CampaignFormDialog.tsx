import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle, Trash2 } from 'lucide-react';
import { useMemo, useState } from 'react';
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
import { useAuth } from '@/features/auth/use-auth';
import { applyServerErrors } from '@/lib/api/form-errors';
import { errorMessage } from '@/lib/api/errors';

import { useMarketingContext } from '../api';
import { CAMPAIGN_STATUSES, CAMPAIGN_TYPES, useCreateCampaign, useDeleteCampaign, useUpdateCampaign, type EmailCampaign } from './api';
import { CAMPAIGN_STATUS_LABELS, CAMPAIGN_TYPE_LABELS, COUNT_FIELDS, countField, countProblems, parseCount, type CountField } from './email-meta';

/** Server field errors shown on their field; count problems from the server appear in the banner. */
const SERVER_FIELDS = ['name', 'campaignType', 'campaignDate', 'status', 'ownerId', 'audience', 'notes'] as const;

interface CampaignFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this campaign; create a new one when absent. */
  campaign?: EmailCampaign;
}

export function CampaignFormDialog({ open, onOpenChange, campaign }: CampaignFormDialogProps) {
  // "Today" (for sent dates) and the owner list come from the marketing context.
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
        {open && context.data && <CampaignForm campaign={campaign} today={context.data.today} onDone={() => onOpenChange(false)} />}
      </DialogContent>
    </Dialog>
  );
}

function CampaignForm({ campaign, today, onDone }: { campaign?: EmailCampaign; today: string; onDone: () => void }) {
  const { user } = useAuth();
  const context = useMarketingContext();
  const create = useCreateCampaign();
  const update = useUpdateCampaign(campaign?.id ?? 0);
  const remove = useDeleteCampaign();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const editing = campaign !== undefined;

  /** Mirrors EmailCampaignDtos.SaveCampaign and EmailCampaignService.validate. */
  const schema = useMemo(
    () =>
      z
        .object({
          name: z.string().trim().min(1, 'Name is required').max(200, 'At most 200 characters'),
          campaignType: z.enum(['NEWSLETTER', 'LEAD_GENERATION', 'PRODUCT_PROMOTION', 'EVENT', 'RECRUITMENT', 'OTHER']),
          campaignDate: z.string().min(1, 'Campaign date is required'),
          status: z.enum(['DRAFT', 'SCHEDULED', 'SENT', 'CANCELLED']),
          ownerId: z.string(),
          audience: z.string().trim().max(200, 'At most 200 characters'),
          notes: z.string().max(2000, 'At most 2000 characters'),
          counts: z.object(Object.fromEntries(COUNT_FIELDS.map((f) => [f.name, countField])) as Record<CountField, typeof countField>),
        })
        .superRefine((v, ctx) => {
          if (v.status !== 'SENT') return;
          if (v.campaignDate > today) ctx.addIssue({ code: 'custom', path: ['campaignDate'], message: 'A sent campaign cannot be dated in the future' });
          const counts = Object.fromEntries(COUNT_FIELDS.map((f) => [f.name, parseCount(v.counts[f.name])])) as Record<CountField, number>;
          for (const [field, message] of countProblems(counts)) ctx.addIssue({ code: 'custom', path: ['counts', field], message });
        }),
    [today],
  );
  type Values = z.infer<typeof schema>;

  const owners = context.data?.owners ?? [];
  const ownerOptions = campaign?.owner && !owners.some((o) => o.id === campaign.owner?.id) ? [...owners, campaign.owner] : owners;
  const c = campaign?.counts;
  const initialCounts: Record<CountField, string> = {
    emailsSent: String(c?.emailsSent ?? ''),
    delivered: String(c?.delivered ?? ''),
    bounced: String(c?.bounced ?? ''),
    opened: String(c?.opened ?? ''),
    uniqueOpens: String(c?.uniqueOpens ?? ''),
    clicked: String(c?.clicked ?? ''),
    uniqueClicks: String(c?.uniqueClicks ?? ''),
    unsubscribed: String(c?.unsubscribed ?? ''),
    leadsGenerated: String(c?.leads ?? ''),
  };
  const { register, handleSubmit, control, setError, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: campaign?.name ?? '',
      campaignType: campaign?.campaignType ?? 'NEWSLETTER',
      campaignDate: campaign?.campaignDate ?? today,
      status: campaign?.status ?? 'SENT',
      ownerId: String(campaign ? (campaign.owner?.id ?? '') : owners.some((o) => o.id === user?.id) ? user?.id : ''),
      audience: campaign?.audience ?? '',
      notes: campaign?.notes ?? '',
      counts: initialCounts,
    },
  });
  const status = useWatch({ control, name: 'status' });
  const sent = status === 'SENT';
  const clearsCounts = editing && campaign.status === 'SENT' && !sent;

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    // Counts belong to sent campaigns only; any other status saves zeros.
    const count = (field: CountField) => (sent ? parseCount(values.counts[field]) : 0);
    const body = {
      name: values.name,
      campaignType: values.campaignType,
      campaignDate: values.campaignDate,
      status: values.status,
      ownerId: values.ownerId ? Number(values.ownerId) : null,
      audience: values.audience || null,
      notes: values.notes.trim() || null,
      emailsSent: count('emailsSent'),
      delivered: count('delivered'),
      bounced: count('bounced'),
      opened: count('opened'),
      uniqueOpens: count('uniqueOpens'),
      clicked: count('clicked'),
      uniqueClicks: count('uniqueClicks'),
      unsubscribed: count('unsubscribed'),
      leadsGenerated: count('leadsGenerated'),
    };
    try {
      if (editing) await update.mutateAsync({ ...body, version: campaign.version });
      else await create.mutateAsync(body);
      toast.success(editing ? 'Campaign updated' : `${values.name} added`);
      onDone();
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
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? 'Edit campaign' : 'New email campaign'}</DialogTitle>
        <DialogDescription>Enter the raw counts from Zoho; open, click and conversion rates are calculated for you.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-5">
        <FormBanner message={banner} />
        <FormField id="campaign-name" label="Campaign name" required error={errors.name?.message}>
          <Input id="campaign-name" autoFocus aria-invalid={errors.name ? true : undefined} aria-describedby="campaign-name-message" {...register('name')} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField id="campaign-type" label="Type" required>
            <Select id="campaign-type" {...register('campaignType')}>
              {CAMPAIGN_TYPES.map((t) => (
                <option key={t} value={t}>
                  {CAMPAIGN_TYPE_LABELS[t]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="campaign-status" label="Status" required>
            <Select id="campaign-status" {...register('status')}>
              {CAMPAIGN_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {CAMPAIGN_STATUS_LABELS[s]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="campaign-date" label={sent ? 'Sent on' : 'Campaign date'} required error={errors.campaignDate?.message}>
            <Input id="campaign-date" type="date" aria-invalid={errors.campaignDate ? true : undefined} aria-describedby="campaign-date-message" {...register('campaignDate')} />
          </FormField>
          <FormField id="campaign-owner" label="Owner">
            <Select id="campaign-owner" {...register('ownerId')}>
              <option value="">No owner</option>
              {ownerOptions.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.fullName}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="campaign-audience" label="Audience" error={errors.audience?.message} className="sm:col-span-2">
            <Input id="campaign-audience" placeholder="e.g. SAP decision makers" {...register('audience')} />
          </FormField>
        </div>

        {sent ? (
          <fieldset className="space-y-3">
            <legend className="text-sm font-semibold">Results</legend>
            <div className="grid gap-4 sm:grid-cols-3">
              {COUNT_FIELDS.map((field) => {
                const error = errors.counts?.[field.name]?.message;
                return (
                  <FormField key={field.name} id={`count-${field.name}`} label={field.label} error={error}>
                    <Input id={`count-${field.name}`} inputMode="numeric" aria-invalid={error ? true : undefined} aria-describedby={`count-${field.name}-message`} {...register(`counts.${field.name}`)} />
                  </FormField>
                );
              })}
            </div>
          </fieldset>
        ) : (
          <p className="rounded-lg border bg-muted/40 px-3 py-2.5 text-sm text-muted-foreground">
            {clearsCounts ? 'Results are only kept for sent campaigns: saving with this status clears them.' : 'Results are entered once the campaign is sent.'}
          </p>
        )}

        <FormField id="campaign-notes" label="Notes" error={errors.notes?.message}>
          <Textarea id="campaign-notes" rows={2} {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && campaign.status !== 'SENT' && (
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
