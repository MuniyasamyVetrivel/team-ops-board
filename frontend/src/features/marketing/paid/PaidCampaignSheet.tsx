import { zodResolver } from '@hookform/resolvers/zod';
import { CalendarRange, History, IndianRupee, LoaderCircle, Lock, Pencil, PiggyBank, Plus, ReceiptIndianRupee, UserPlus, Wallet } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { FormBanner, FormField } from '@/components/common/FormField';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Textarea } from '@/components/ui/textarea';
import { parseLocalDate } from '@/features/tasks/task-meta';
import { applyServerErrors } from '@/lib/api/form-errors';

import { useMarketingContext } from '../api';
import { MarketingKpiCard } from '../components/MarketingKpiCard';
import { formatCount, formatInr, formatPercent, periodLabel } from '../marketing-format';
import { usePaidCampaign, useSavePaidMonth, type PaidCampaignDetail, type PaidMonth } from './api';
import { PaidCampaignFormDialog } from './PaidCampaignFormDialog';
import { BudgetBar, PaidStatusBadge } from './PaidBadges';
import { moneyField, OBJECTIVE_LABELS, PAID_RATE_HINTS, paidCountField, parseNumber, PLATFORM_LABELS, recordableMonths, SOURCE_LABELS, type Period } from './paid-meta';

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

interface PaidCampaignSheetProps {
  campaignId: number | null;
  onOpenChange: (open: boolean) => void;
  /** The month the page is showing: the default month to record. */
  month: number;
  year: number;
}

/** A campaign: budget KPIs, every recorded month, recording a month and corrections. */
export function PaidCampaignSheet({ campaignId, onOpenChange, month, year }: PaidCampaignSheetProps) {
  return (
    <Dialog open={campaignId !== null} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-3xl">{campaignId !== null && <CampaignBody campaignId={campaignId} month={month} year={year} onClose={() => onOpenChange(false)} />}</SheetContent>
    </Dialog>
  );
}

type Mode = { kind: 'view' } | { kind: 'record' } | { kind: 'correct'; row: PaidMonth };

function CampaignBody({ campaignId, month, year, onClose }: { campaignId: number; month: number; year: number; onClose: () => void }) {
  const detail = usePaidCampaign(campaignId);
  const context = useMarketingContext();
  const [mode, setMode] = useState<Mode>({ kind: 'view' });
  const [editing, setEditing] = useState(false);

  if (detail.isPending) {
    return (
      <div className="space-y-4 p-6" role="status" aria-label="Loading the campaign">
        <Skeleton className="h-8 w-2/3" />
        <div className="grid gap-3 sm:grid-cols-3">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
        <Skeleton className="h-40" />
      </div>
    );
  }
  if (detail.isError) {
    return <ErrorState error={detail.error} title="Couldn't load the campaign" onRetry={() => void detail.refetch()} />;
  }

  const { campaign, months, permissions } = detail.data;
  const canEdit = permissions.canEdit;
  const open = context.data ? recordableMonths(campaign, months, context.data.today) : [];
  const newestFirst = [...months].reverse();
  const progress = campaign.budgetProgress;
  const results = campaign.lifetime.results;

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <div className="flex flex-wrap items-center gap-2">
          <DialogTitle>{campaign.name}</DialogTitle>
          <PaidStatusBadge status={campaign.status} />
        </div>
        <DialogDescription className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span>
            {PLATFORM_LABELS[campaign.platform]} · {OBJECTIVE_LABELS[campaign.objective]}
          </span>
          <span aria-hidden>·</span>
          <span className="inline-flex items-center gap-1">
            <CalendarRange className="size-3.5" aria-hidden />
            {dayFormat.format(parseLocalDate(campaign.startDate))} – {campaign.endDate ? dayFormat.format(parseLocalDate(campaign.endDate)) : 'no end date'}
          </span>
          {campaign.owner && (
            <>
              <span aria-hidden>·</span>
              <span>{campaign.owner.fullName}</span>
            </>
          )}
        </DialogDescription>
        {canEdit && (
          <div>
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit campaign
            </Button>
          </div>
        )}
      </DialogHeader>
      <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 pb-6">
        <section aria-label="Campaign totals" className="grid gap-4 sm:gap-6 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-5">
          <MarketingKpiCard label="Budget" icon={Wallet} value={progress.budget} format="currency" />
          <MarketingKpiCard label="Spent" icon={ReceiptIndianRupee} value={progress.spent} format="currency">
            <BudgetBar label={`${campaign.name} budget used`} progress={progress} className="mt-2" compact />
          </MarketingKpiCard>
          <MarketingKpiCard label="Remaining" icon={PiggyBank} value={progress.remaining} format="currency" hint="Budget − spent">
            {progress.overBudget && <p className="mt-1 text-xs font-medium text-status-danger">Over budget</p>}
          </MarketingKpiCard>
          <MarketingKpiCard label="Leads" icon={UserPlus} value={results.leads} hint={`${formatCount(results.clicks)} clicks · CTR ${formatPercent(campaign.lifetime.rates.ctr)}`} />
          <MarketingKpiCard label="Cost per lead" icon={IndianRupee} value={campaign.lifetime.rates.costPerLead} format="currency" hint={PAID_RATE_HINTS.costPerLead} />
        </section>
        {campaign.notes && <p className="rounded-lg border bg-muted/30 px-3 py-2 text-sm whitespace-pre-line">{campaign.notes}</p>}

        {canEdit && mode.kind === 'record' && <MonthForm detail={detail.data} periods={open} defaultMonth={month} defaultYear={year} onDone={() => setMode({ kind: 'view' })} />}
        {canEdit && mode.kind === 'correct' && <MonthForm detail={detail.data} row={mode.row} onDone={() => setMode({ kind: 'view' })} />}

        <section aria-labelledby="paid-months" className="space-y-2">
          <div className="flex items-center justify-between gap-3">
            <h3 id="paid-months" className="text-sm font-semibold">
              Monthly results
            </h3>
            {canEdit && mode.kind === 'view' && campaign.status !== 'DRAFT' && open.length > 0 && (
              <Button size="sm" onClick={() => setMode({ kind: 'record' })}>
                <Plus aria-hidden />
                Record a month
              </Button>
            )}
          </div>
          {canEdit && campaign.status === 'DRAFT' && <p className="text-sm text-muted-foreground">A draft has no results yet. Set the campaign to active to record its months.</p>}
          {months.length === 0 ? (
            <EmptyState icon={History} title="No results recorded yet" description="Record each month's spend, impressions, clicks, leads and conversions." />
          ) : (
            <Table aria-label="Monthly results">
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  <TableHead numeric>Spend</TableHead>
                  <TableHead numeric>Impressions</TableHead>
                  <TableHead numeric>Clicks</TableHead>
                  <TableHead numeric>CTR</TableHead>
                  <TableHead numeric>Leads</TableHead>
                  <TableHead numeric>Cost per lead</TableHead>
                  <TableHead>Recorded</TableHead>
                  {canEdit && (
                    <TableHead>
                      <span className="sr-only">Actions</span>
                    </TableHead>
                  )}
                </TableRow>
              </TableHeader>
              <TableBody>
                {newestFirst.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell className="text-sm font-medium whitespace-nowrap">
                      {row.period.label}
                      {row.notes && <p className="max-w-44 truncate text-xs font-normal text-muted-foreground" title={row.notes}>{row.notes}</p>}
                    </TableCell>
                    <TableCell numeric>{formatInr(row.figures.results.spend)}</TableCell>
                    <TableCell numeric>{formatCount(row.figures.results.impressions)}</TableCell>
                    <TableCell numeric>{formatCount(row.figures.results.clicks)}</TableCell>
                    <TableCell numeric>{formatPercent(row.figures.rates.ctr)}</TableCell>
                    <TableCell numeric>{formatCount(row.figures.results.leads)}</TableCell>
                    <TableCell numeric>{formatInr(row.figures.rates.costPerLead)}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      <Badge tone="neutral">{SOURCE_LABELS[row.source]}</Badge>
                      <p className="mt-0.5">
                        {row.recordedBy?.fullName ?? 'Unknown'} · {dayFormat.format(new Date(row.updatedAt))}
                      </p>
                    </TableCell>
                    {canEdit && (
                      <TableCell>
                        {row.correctable ? (
                          <Button variant="ghost" size="icon" aria-label={`Correct ${row.period.label}`} disabled={mode.kind !== 'view'} onClick={() => setMode({ kind: 'correct', row })}>
                            <Pencil aria-hidden />
                          </Button>
                        ) : (
                          <span className="inline-flex text-muted-foreground" title="Closed month: only the current and previous month can be corrected">
                            <Lock className="size-4" aria-hidden />
                            <span className="sr-only">{row.period.label} is closed</span>
                          </span>
                        )}
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </section>
      </div>
      <PaidCampaignFormDialog open={editing} onOpenChange={setEditing} campaign={campaign} hasResults={months.length > 0} onDeleted={onClose} />
    </div>
  );
}

/** Mirrors PaidCampaignDtos.SaveMonth and PaidResults.problems(). */
const monthSchema = z
  .object({
    period: z.string(),
    amountSpent: moneyField('Amount spent is required'),
    impressions: paidCountField,
    clicks: paidCountField,
    leads: paidCountField,
    conversions: paidCountField,
    notes: z.string().max(1000, 'At most 1000 characters'),
  })
  .superRefine((v, ctx) => {
    if (parseNumber(v.clicks) > parseNumber(v.impressions)) ctx.addIssue({ code: 'custom', path: ['clicks'], message: 'Cannot be more than the impressions' });
    if (parseNumber(v.conversions) > parseNumber(v.leads)) ctx.addIssue({ code: 'custom', path: ['conversions'], message: 'Cannot be more than the leads' });
  });

type MonthValues = z.infer<typeof monthSchema>;

const MONTH_FIELDS = ['amountSpent', 'impressions', 'clicks', 'leads', 'conversions', 'notes'] as const;
const COUNT_INPUTS = [
  { name: 'impressions', label: 'Impressions' },
  { name: 'clicks', label: 'Clicks' },
  { name: 'leads', label: 'Leads' },
  { name: 'conversions', label: 'Conversions' },
] as const;

interface MonthFormProps {
  detail: PaidCampaignDetail;
  /** Correct this month; record a new one when absent. */
  row?: PaidMonth;
  /** Months that can still be recorded, newest first. */
  periods?: Period[];
  defaultMonth?: number;
  defaultYear?: number;
  onDone: () => void;
}

function MonthForm({ detail, row, periods = [], defaultMonth, defaultYear, onDone }: MonthFormProps) {
  const save = useSavePaidMonth(detail.campaign.id);
  const [banner, setBanner] = useState<string | null>(null);
  const correcting = row !== undefined;
  const key = (p: Period) => `${p.year}-${p.month}`;
  const preferred = periods.find((p) => p.month === defaultMonth && p.year === defaultYear) ?? periods[0];
  const r = row?.figures.results;

  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<MonthValues>({
    resolver: zodResolver(monthSchema),
    defaultValues: {
      period: row ? key(row.period) : preferred ? key(preferred) : '',
      amountSpent: r ? String(r.spend) : '',
      impressions: r ? String(r.impressions) : '',
      clicks: r ? String(r.clicks) : '',
      leads: r ? String(r.leads) : '',
      conversions: r ? String(r.conversions) : '',
      notes: row?.notes ?? '',
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const [y, m] = values.period.split('-').map(Number);
    const input = {
      amountSpent: parseNumber(values.amountSpent),
      impressions: parseNumber(values.impressions),
      clicks: parseNumber(values.clicks),
      leads: parseNumber(values.leads),
      conversions: parseNumber(values.conversions),
      notes: values.notes.trim() || null,
      ...(correcting ? { version: row.version } : {}),
    };
    try {
      await save.mutateAsync({ month: m!, year: y!, input });
      toast.success(correcting ? `${row.period.label} corrected` : `Results recorded for ${periodLabel(m!, y!)}`);
      onDone();
    } catch (error) {
      setBanner(applyServerErrors(error, setError, MONTH_FIELDS));
    }
  });

  const title = correcting ? `Correct ${row.period.label}` : 'Record a month';
  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4 rounded-xl border bg-muted/30 p-4" aria-label={title}>
      <div>
        <h3 className="text-sm font-semibold">{title}</h3>
        <p className="text-xs text-muted-foreground">
          {correcting ? 'The correction is audited with the old and new figures.' : 'Each month is recorded once; rates and cost per lead are calculated for you.'}
        </p>
      </div>
      <FormBanner message={banner} />
      <div className="grid gap-4 sm:grid-cols-3">
        {!correcting && (
          <FormField id="paid-month" label="Month" required>
            <Select id="paid-month" {...register('period')}>
              {periods.map((p) => (
                <option key={key(p)} value={key(p)}>
                  {periodLabel(p.month, p.year)}
                </option>
              ))}
            </Select>
          </FormField>
        )}
        <FormField id="paid-spent" label="Amount spent (₹)" required error={errors.amountSpent?.message}>
          <Input id="paid-spent" inputMode="decimal" autoFocus aria-invalid={errors.amountSpent ? true : undefined} aria-describedby="paid-spent-message" {...register('amountSpent')} />
        </FormField>
        {COUNT_INPUTS.map((field) => {
          const error = errors[field.name]?.message;
          return (
            <FormField key={field.name} id={`paid-${field.name}`} label={field.label} error={error}>
              <Input id={`paid-${field.name}`} inputMode="numeric" aria-invalid={error ? true : undefined} aria-describedby={`paid-${field.name}-message`} {...register(field.name)} />
            </FormField>
          );
        })}
        <FormField id="paid-month-notes" label="Notes" error={errors.notes?.message} className={correcting ? 'sm:col-span-3' : 'sm:col-span-2'}>
          <Textarea id="paid-month-notes" rows={1} {...register('notes')} />
        </FormField>
      </div>
      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {correcting ? 'Save correction' : 'Record results'}
        </Button>
      </div>
    </form>
  );
}
