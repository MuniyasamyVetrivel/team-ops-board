import { zodResolver } from '@hookform/resolvers/zod';
import { History, LoaderCircle, Lock, Monitor, Pencil, Plus, Smartphone } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { FormBanner, FormField } from '@/components/common/FormField';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Textarea } from '@/components/ui/textarea';
import { applyServerErrors } from '@/lib/api/form-errors';

import { useMarketingContext } from '../api';
import { RankingBadge, RankingChangeIndicator } from '../components/RankingBadges';
import { formatCount, MONTH_NAMES } from '../marketing-format';
import { useCorrectRanking, useKeywordHistory, useRecordRanking, type KeywordHistory, type RankingEntry } from './api';
import { RankingHistoryChart } from './RankingHistoryChart';
import { DEVICE_LABELS, RANKING_SOURCE_LABELS, SEARCH_ENGINE_LABELS } from './seo-meta';

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

interface KeywordHistorySheetProps {
  keywordId: number | null;
  onOpenChange: (open: boolean) => void;
  /** The month the page is showing: the default for a new entry. */
  month: number;
  year: number;
  canEdit: boolean;
}

/** A keyword's ranking history: line chart, every recorded month, recording a month and corrections. */
export function KeywordHistorySheet({ keywordId, onOpenChange, month, year, canEdit }: KeywordHistorySheetProps) {
  return (
    <Dialog open={keywordId !== null} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-2xl">{keywordId !== null && <HistoryBody keywordId={keywordId} month={month} year={year} canEdit={canEdit} />}</SheetContent>
    </Dialog>
  );
}

type Mode = { kind: 'view' } | { kind: 'record' } | { kind: 'correct'; entry: RankingEntry };

function HistoryBody({ keywordId, month, year, canEdit }: { keywordId: number; month: number; year: number; canEdit: boolean }) {
  const history = useKeywordHistory(keywordId);
  const [mode, setMode] = useState<Mode>({ kind: 'view' });

  if (history.isPending) {
    return (
      <div className="space-y-4 p-6" role="status" aria-label="Loading ranking history">
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-64" />
        <Skeleton className="h-40" />
      </div>
    );
  }
  if (history.isError) {
    return <ErrorState error={history.error} title="Couldn't load the ranking history" onRetry={() => void history.refetch()} />;
  }

  const h = history.data;
  const DeviceIcon = h.device === 'MOBILE' ? Smartphone : Monitor;
  const recordedHere = h.entries.some((e) => e.month === month && e.year === year);
  const chronological = [...h.entries].reverse();

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <DialogTitle>{h.keyword}</DialogTitle>
        <DialogDescription className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span>{h.page.title}</span>
          <span aria-hidden>·</span>
          <span className="inline-flex items-center gap-1">
            <DeviceIcon className="size-3.5" aria-hidden />
            {SEARCH_ENGINE_LABELS[h.searchEngine]} · {h.location} · {DEVICE_LABELS[h.device]}
          </span>
          {h.targetPosition !== null && (
            <>
              <span aria-hidden>·</span>
              <span>Target #{h.targetPosition}</span>
            </>
          )}
        </DialogDescription>
      </DialogHeader>
      <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 pb-6">
        {chronological.length > 0 && (
          <RankingHistoryChart
            label={`Monthly positions for ${h.keyword}`}
            periods={chronological.map((e) => ({ month: e.month, year: e.year, label: e.label }))}
            series={[{ key: `k${h.keywordId}`, name: h.keyword, points: chronological.map((e) => ({ recorded: true, position: e.position })) }]}
            target={h.targetPosition}
          />
        )}

        {canEdit && mode.kind === 'record' && <RankingForm history={h} defaultMonth={month} defaultYear={year} onDone={() => setMode({ kind: 'view' })} />}
        {canEdit && mode.kind === 'correct' && <RankingForm history={h} entry={mode.entry} onDone={() => setMode({ kind: 'view' })} />}

        <section aria-labelledby="ranking-months" className="space-y-2">
          <div className="flex items-center justify-between gap-3">
            <h3 id="ranking-months" className="text-sm font-semibold">
              Monthly rankings
            </h3>
            {canEdit && h.status !== 'ARCHIVED' && mode.kind === 'view' && (
              <Button size="sm" onClick={() => setMode({ kind: 'record' })}>
                <Plus aria-hidden />
                {recordedHere ? 'Record another month' : `Record ${MONTH_NAMES[month - 1]}`}
              </Button>
            )}
          </div>
          {h.entries.length === 0 ? (
            <EmptyState icon={History} title="No rankings recorded yet" description="Each month adds a new record; earlier months are never overwritten." />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Month</TableHead>
                  <TableHead>Position</TableHead>
                  <TableHead>Change</TableHead>
                  <TableHead className="text-right">Volume</TableHead>
                  <TableHead>Recorded</TableHead>
                  {canEdit && (
                    <TableHead>
                      <span className="sr-only">Actions</span>
                    </TableHead>
                  )}
                </TableRow>
              </TableHeader>
              <TableBody>
                {h.entries.map((entry) => (
                  <TableRow key={entry.id}>
                    <TableCell className="text-sm font-medium whitespace-nowrap">
                      {entry.label}
                      {entry.notes && <p className="max-w-56 truncate text-xs font-normal text-muted-foreground" title={entry.notes}>{entry.notes}</p>}
                    </TableCell>
                    <TableCell>
                      <RankingBadge position={entry.position} status={entry.status} />
                    </TableCell>
                    <TableCell>
                      <RankingChangeIndicator change={entry.change} />
                    </TableCell>
                    <TableCell className="text-right text-sm tabular-nums">{formatCount(entry.searchVolume)}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      <Badge tone="neutral">{RANKING_SOURCE_LABELS[entry.source]}</Badge>
                      <p className="mt-0.5">
                        {entry.recordedBy?.fullName ?? 'Unknown'} · {dayFormat.format(new Date(entry.updatedAt))}
                      </p>
                    </TableCell>
                    {canEdit && (
                      <TableCell>
                        {entry.correctable ? (
                          <Button variant="ghost" size="icon" aria-label={`Correct ${entry.label}`} disabled={mode.kind !== 'view'} onClick={() => setMode({ kind: 'correct', entry })}>
                            <Pencil aria-hidden />
                          </Button>
                        ) : (
                          <span className="inline-flex text-muted-foreground" title="Closed month: only the current and previous month can be corrected">
                            <Lock className="size-4" aria-hidden />
                            <span className="sr-only">{entry.label} is closed</span>
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
    </div>
  );
}

/** Mirrors RankingDtos.RecordRanking / CorrectRanking: a position 1–100, or explicitly Not Ranked. */
const rankingSchema = z
  .object({
    month: z.string(),
    year: z.string(),
    // A disabled input (while Not ranked is ticked) is reported as undefined.
    position: z.string().trim().optional(),
    notRanked: z.boolean(),
    searchVolume: z.string().trim().refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) <= 1_000_000_000), 'Enter a whole number of searches'),
    notes: z.string().max(1000, 'At most 1000 characters'),
  })
  .refine((v) => v.notRanked || (/^\d+$/.test(v.position ?? '') && Number(v.position) >= 1 && Number(v.position) <= 100), {
    path: ['position'],
    message: 'Enter a position from 1 to 100, or tick Not ranked',
  });

type RankingValues = z.infer<typeof rankingSchema>;

const SERVER_FIELDS = ['position', 'searchVolume', 'notes'] as const;

function RankingForm({ history, entry, defaultMonth, defaultYear, onDone }: { history: KeywordHistory; entry?: RankingEntry; defaultMonth?: number; defaultYear?: number; onDone: () => void }) {
  const context = useMarketingContext();
  const record = useRecordRanking(history.keywordId);
  const correct = useCorrectRanking();
  const [banner, setBanner] = useState<string | null>(null);
  const correcting = entry !== undefined;

  const { register, handleSubmit, control, setError, formState: { errors, isSubmitting } } = useForm<RankingValues>({
    resolver: zodResolver(rankingSchema),
    defaultValues: {
      month: String(entry?.month ?? defaultMonth ?? ''),
      year: String(entry?.year ?? defaultYear ?? ''),
      position: entry?.position?.toString() ?? '',
      notRanked: entry ? entry.position === null : false,
      searchVolume: entry?.searchVolume?.toString() ?? '',
      notes: entry?.notes ?? '',
    },
  });
  const notRanked = useWatch({ control, name: 'notRanked' });
  const years = context.data?.years ?? [Number(defaultYear)];

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const body = {
      position: values.notRanked ? null : Number(values.position),
      searchVolume: values.searchVolume === '' ? null : Number(values.searchVolume),
      notes: values.notes.trim() || null,
    };
    try {
      if (correcting) {
        await correct.mutateAsync({ id: entry.id, input: { ...body, version: entry.version } });
        toast.success(`${entry.label} corrected`);
      } else {
        await record.mutateAsync({ ...body, month: Number(values.month), year: Number(values.year) });
        toast.success(`Ranking recorded for ${MONTH_NAMES[Number(values.month) - 1]} ${values.year}`);
      }
      onDone();
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4 rounded-xl border bg-muted/30 p-4" aria-label={correcting ? `Correct ${entry.label}` : 'Record a month'}>
      <div>
        <h3 className="text-sm font-semibold">{correcting ? `Correct ${entry.label}` : 'Record a month'}</h3>
        <p className="text-xs text-muted-foreground">
          {correcting ? 'The correction is audited with the old and new values.' : 'Adds a new record. A month that already has a ranking must be corrected instead.'}
        </p>
      </div>
      <FormBanner message={banner} />
      <div className="grid gap-4 sm:grid-cols-4">
        {!correcting && (
          <>
            <FormField id="ranking-month" label="Month" required>
              <Select id="ranking-month" {...register('month')}>
                {MONTH_NAMES.map((name, index) => (
                  <option key={name} value={index + 1}>
                    {name}
                  </option>
                ))}
              </Select>
            </FormField>
            <FormField id="ranking-year" label="Year" required>
              <Select id="ranking-year" {...register('year')}>
                {years.map((y) => (
                  <option key={y} value={y}>
                    {y}
                  </option>
                ))}
              </Select>
            </FormField>
          </>
        )}
        <FormField id="ranking-position" label="Position" required={!notRanked} error={errors.position?.message}>
          <Input id="ranking-position" inputMode="numeric" disabled={notRanked} autoFocus aria-invalid={errors.position ? true : undefined} aria-describedby="ranking-position-message" {...register('position')} />
        </FormField>
        <div className="flex items-end pb-2.5">
          <label className="flex items-center gap-2 text-sm">
            <Checkbox {...register('notRanked')} />
            Not ranked
          </label>
        </div>
        <FormField id="ranking-volume" label="Search volume" error={errors.searchVolume?.message} className={correcting ? '' : 'sm:col-span-2'}>
          <Input id="ranking-volume" inputMode="numeric" aria-invalid={errors.searchVolume ? true : undefined} aria-describedby="ranking-volume-message" {...register('searchVolume')} />
        </FormField>
        <FormField id="ranking-notes" label="Notes" error={errors.notes?.message} className={correcting ? 'sm:col-span-3' : 'sm:col-span-2'}>
          <Textarea id="ranking-notes" rows={1} aria-invalid={errors.notes ? true : undefined} aria-describedby="ranking-notes-message" {...register('notes')} />
        </FormField>
      </div>
      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {correcting ? 'Save correction' : 'Record ranking'}
        </Button>
      </div>
    </form>
  );
}
