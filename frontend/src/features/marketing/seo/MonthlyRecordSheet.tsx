import { zodResolver } from '@hookform/resolvers/zod';
import { CircleCheck, LoaderCircle } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { FormBanner } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { errorMessage } from '@/lib/api/errors';

import { periodLabel } from '../marketing-format';
import { useRankings, useRecordMonthly, type KeywordItem } from './api';
import { PreviousPosition } from './SeoBadges';

/** The most keywords offered at once; the server accepts at most 500 per update. */
const BATCH = 100;

interface MonthlyRecordSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  month: number;
  year: number;
}

/**
 * The monthly SEO update: every tracked keyword without a ranking for the month, in one form. Only filled rows are
 * sent (a position, or Not ranked ticked), and the server saves all of them or none.
 */
export function MonthlyRecordSheet({ open, onOpenChange, month, year }: MonthlyRecordSheetProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-3xl">{open && <SheetBody month={month} year={year} onDone={() => onOpenChange(false)} />}</SheetContent>
    </Dialog>
  );
}

function SheetBody({ month, year, onDone }: { month: number; year: number; onDone: () => void }) {
  const pending = useRankings({ month, year, standing: 'NOT_RECORDED', status: ['ACTIVE'], sort: 'page,asc', size: BATCH });
  const label = periodLabel(month, year);

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <DialogTitle>Record rankings for {label}</DialogTitle>
        <DialogDescription>Keywords being tracked that have no position for {label} yet. Leave a row empty to skip it.</DialogDescription>
      </DialogHeader>
      {pending.isPending ? (
        <div className="space-y-3 px-6" role="status" aria-label="Loading keywords to record">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-11" />
          ))}
        </div>
      ) : pending.isError ? (
        <ErrorState error={pending.error} title="Couldn't load keywords" onRetry={() => void pending.refetch()} />
      ) : pending.data.content.length === 0 ? (
        <EmptyState icon={CircleCheck} title={`Every keyword is recorded for ${label}`} description="Corrections are made from a keyword's ranking history." />
      ) : (
        <EntryForm key={pending.data.content.map((k) => k.id).join(',')} keywords={pending.data.content} total={pending.data.totalElements} month={month} year={year} onDone={onDone} />
      )}
    </div>
  );
}

const entrySchema = z
  .object({
    keywordId: z.number(),
    // A disabled input (while NR is ticked) is reported as undefined.
    position: z.string().trim().optional(),
    notRanked: z.boolean(),
  })
  .refine((v) => v.notRanked || !v.position || (/^\d+$/.test(v.position) && Number(v.position) >= 1 && Number(v.position) <= 100), {
    path: ['position'],
    message: '1–100, or tick NR',
  });

/** Mirrors RankingDtos.RecordMonthly: positions 1–100 or explicitly Not Ranked; at least one row. */
const monthlySchema = z.object({ entries: z.array(entrySchema) }).refine((v) => v.entries.some((e) => e.notRanked || Boolean(e.position)), {
  path: ['root'],
  message: 'Enter at least one position, or tick NR for a keyword that is not ranked',
});

type MonthlyValues = z.infer<typeof monthlySchema>;

function EntryForm({ keywords, total, month, year, onDone }: { keywords: KeywordItem[]; total: number; month: number; year: number; onDone: () => void }) {
  const record = useRecordMonthly();
  const [banner, setBanner] = useState<string | null>(null);
  const { register, handleSubmit, control, formState: { errors, isSubmitting } } = useForm<MonthlyValues>({
    resolver: zodResolver(monthlySchema),
    defaultValues: { entries: keywords.map((k) => ({ keywordId: k.id, position: '', notRanked: false })) },
  });
  const entries = useWatch({ control, name: 'entries' });
  const filled = entries.filter((e) => e.notRanked || Boolean(e.position?.trim())).length;
  const formError = (errors as { root?: { message?: string } }).root?.message;

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      const result = await record.mutateAsync({
        month,
        year,
        entries: values.entries
          .filter((e) => e.notRanked || Boolean(e.position))
          .map((e) => ({ keywordId: e.keywordId, position: e.notRanked ? null : Number(e.position), searchVolume: null, notes: null })),
      });
      toast.success(`${result.recorded} ${result.recorded === 1 ? 'ranking' : 'rankings'} recorded for ${result.period.label}`);
      onDone();
    } catch (error) {
      setBanner(errorMessage(error));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <div className="min-h-0 flex-1 overflow-y-auto px-6">
        <FormBanner message={banner ?? formError ?? null} />
        {total > keywords.length && <p className="mb-3 text-sm text-muted-foreground">Showing the first {keywords.length} of {total}. Save these, then open this again for the rest.</p>}
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Keyword</TableHead>
              <TableHead>Last month</TableHead>
              <TableHead className="w-28">Position</TableHead>
              <TableHead className="w-20">NR</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {keywords.map((keyword, index) => {
              const error = errors.entries?.[index]?.position?.message;
              const notRanked = entries[index]?.notRanked ?? false;
              return (
                <TableRow key={keyword.id}>
                  <TableCell className="max-w-xs">
                    <p className="truncate font-medium">{keyword.keyword}</p>
                    <p className="truncate text-xs text-muted-foreground">
                      {keyword.page.title} · {keyword.device === 'MOBILE' ? 'Mobile' : 'Desktop'}
                    </p>
                  </TableCell>
                  <TableCell className="text-sm">
                    <PreviousPosition standing={keyword.ranking} />
                  </TableCell>
                  <TableCell>
                    <Input
                      inputMode="numeric"
                      aria-label={`Position for ${keyword.keyword} (${keyword.device === 'MOBILE' ? 'mobile' : 'desktop'})`}
                      disabled={notRanked}
                      aria-invalid={error ? true : undefined}
                      className="h-8"
                      {...register(`entries.${index}.position`)}
                    />
                    {error && <p className="mt-1 text-xs text-destructive">{error}</p>}
                  </TableCell>
                  <TableCell>
                    <Checkbox aria-label={`${keyword.keyword} (${keyword.device === 'MOBILE' ? 'mobile' : 'desktop'}) is not ranked`} {...register(`entries.${index}.notRanked`)} />
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
      <DialogFooter>
        <span className="mr-auto text-sm text-muted-foreground" aria-live="polite">
          {filled} of {keywords.length} filled in
        </span>
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          Save {filled > 0 ? filled : ''} {filled === 1 ? 'ranking' : 'rankings'}
        </Button>
      </DialogFooter>
    </form>
  );
}
