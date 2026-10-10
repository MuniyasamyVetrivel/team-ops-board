import { Mail, MailOpen, MousePointerClick, Send, Target, UserPlus } from 'lucide-react';
import { useState } from 'react';

import { Delta } from '@/components/common/Delta';
import { ErrorState } from '@/components/common/ErrorState';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from '@/lib/utils';

import { MarketingKpiCard } from '../components/MarketingKpiCard';
import type { MarketingFilters } from '../filter-memory';
import { formatCount, formatPercent, MONTH_NAMES } from '../marketing-format';
import { useEmailSummary, type EmailCounts, type EmailRates, type MonthTotals } from './api';
import { CAMPAIGN_TYPE_LABELS, RATE_LABELS } from './email-meta';

type Compare = 'previous' | 'lastYear';

interface Row {
  label: string;
  hint?: string;
  value: (m: MonthTotals) => number | null;
  rate?: boolean;
  /** Whether a rise is good news, bad news, or neither. */
  better: 'higher' | 'lower' | null;
}

const count = (key: keyof EmailCounts) => (m: MonthTotals) => m.counts[key];
const rate = (key: keyof EmailRates) => (m: MonthTotals) => m.rates[key];

const ROWS: Row[] = [
  { label: 'Campaigns sent', value: (m) => m.campaigns, better: null },
  { label: 'Emails sent', value: count('emailsSent'), better: null },
  { label: 'Delivered', value: count('delivered'), better: null },
  { label: 'Unique opens', value: count('uniqueOpens'), better: 'higher' },
  { label: 'Unique clicks', value: count('uniqueClicks'), better: 'higher' },
  { label: 'Leads', value: count('leads'), better: 'higher' },
  ...(['deliveryRate', 'openRate', 'clickRate', 'clickToOpenRate', 'leadConversionRate'] as const).map((key) => ({ label: RATE_LABELS[key].label, hint: RATE_LABELS[key].formula, value: rate(key), rate: true, better: 'higher' as const })),
  ...(['bounceRate', 'unsubscribeRate'] as const).map((key) => ({ label: RATE_LABELS[key].label, hint: RATE_LABELS[key].formula, value: rate(key), rate: true, better: 'lower' as const })),
];

/** Brief section 39: the month's totals and rates against another month, as KPI cards and a full comparison table. */
export function EmailSummary({ filters }: { filters: MarketingFilters }) {
  const [compare, setCompare] = useState<Compare>('previous');
  const summary = useEmailSummary({
    month: filters.month,
    year: filters.year,
    ownerId: filters.ownerId ?? undefined,
    ...(compare === 'lastYear' ? { compareMonth: filters.month, compareYear: filters.year - 1 } : {}),
  });

  if (summary.isError) {
    return (
      <Card>
        <ErrorState error={summary.error} title="Couldn't load the monthly summary" onRetry={() => void summary.refetch()} />
      </Card>
    );
  }
  if (!summary.data) {
    return (
      <div className="space-y-4" role="status" aria-label="Loading the monthly summary">
        <div className="grid gap-4 sm:gap-6 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }

  const { current, comparison, byType } = summary.data;
  const short = MONTH_NAMES[comparison.period.month - 1] ?? comparison.period.label;
  const compareLabel = comparison.period.year === current.period.year ? short : comparison.period.label;
  const loading = summary.isPlaceholderData;

  return (
    <div className="space-y-6">
      <section aria-label={`Email results for ${current.period.label}`} className="grid gap-4 sm:gap-6 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6">
        <MarketingKpiCard label="Campaigns sent" icon={Send} value={current.campaigns} previous={comparison.campaigns} previousLabel={compareLabel} loading={loading} />
        <MarketingKpiCard label="Emails sent" icon={Mail} value={current.counts.emailsSent} previous={comparison.counts.emailsSent} previousLabel={compareLabel} loading={loading} />
        <MarketingKpiCard label="Open rate" icon={MailOpen} value={current.rates.openRate} format="percent" previous={comparison.rates.openRate} previousLabel={compareLabel} loading={loading} hint={RATE_LABELS.openRate.formula} />
        <MarketingKpiCard label="Click rate" icon={MousePointerClick} value={current.rates.clickRate} format="percent" previous={comparison.rates.clickRate} previousLabel={compareLabel} loading={loading} hint={RATE_LABELS.clickRate.formula} />
        <MarketingKpiCard label="Leads" icon={UserPlus} value={current.counts.leads} previous={comparison.counts.leads} previousLabel={compareLabel} loading={loading} />
        <MarketingKpiCard label="Lead conversion" icon={Target} value={current.rates.leadConversionRate} format="percent" previous={comparison.rates.leadConversionRate} previousLabel={compareLabel} loading={loading} hint={RATE_LABELS.leadConversionRate.formula} />
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,20rem)]">
        <Card className={cn(loading && 'opacity-60')}>
          <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
            <div>
              <h2 className="text-card-title font-semibold">Monthly summary</h2>
              <p className="mt-0.5 text-label text-muted-foreground">Sent campaigns only; rates come from the month&apos;s totals.</p>
            </div>
            <Select aria-label="Compare with" className="w-56" value={compare} onChange={(e) => setCompare(e.target.value as Compare)}>
              <option value="previous">Compare with the previous month</option>
              <option value="lastYear">Compare with the same month last year</option>
            </Select>
          </div>
          <Table aria-label={`${current.period.label} against ${comparison.period.label}`}>
            <TableHeader>
              <TableRow>
                <TableHead>Measure</TableHead>
                <TableHead numeric>{current.period.label}</TableHead>
                <TableHead numeric>{comparison.period.label}</TableHead>
                <TableHead numeric>Change</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {ROWS.map((row) => {
                const now = row.value(current);
                const before = row.value(comparison);
                return (
                  <TableRow key={row.label}>
                    <TableCell>
                      <p className="font-medium whitespace-nowrap">{row.label}</p>
                      {row.hint && <p className="text-xs text-muted-foreground">{row.hint}</p>}
                    </TableCell>
                    <TableCell numeric className="font-semibold">{row.rate ? formatPercent(now) : formatCount(now)}</TableCell>
                    <TableCell numeric className="text-muted-foreground">{row.rate ? formatPercent(before) : formatCount(before)}</TableCell>
                    <TableCell numeric>
                      <Change now={now} before={before} rate={row.rate ?? false} better={row.better} />
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </Card>

        <Card>
          <div className="border-b px-6 py-4">
            <h2 className="text-card-title font-semibold">By campaign type</h2>
            <p className="mt-0.5 text-label text-muted-foreground">{current.period.label}</p>
          </div>
          {byType.length === 0 ? (
            <p className="px-6 py-4 text-sm text-muted-foreground">No campaigns sent this month.</p>
          ) : (
            <ul className="divide-y" aria-label="Campaign types">
              {byType.map((t) => (
                <li key={t.campaignType} className="flex items-center justify-between gap-3 px-6 py-3 text-sm">
                  <div>
                    <p className="font-medium">{CAMPAIGN_TYPE_LABELS[t.campaignType]}</p>
                    <p className="text-xs text-muted-foreground">
                      {t.campaigns} {t.campaigns === 1 ? 'campaign' : 'campaigns'} · {formatCount(t.counts.emailsSent)} sent
                    </p>
                  </div>
                  <div className="text-right text-xs tabular-nums">
                    <p>{formatPercent(t.rates.openRate)} open</p>
                    <p className="text-muted-foreground">{formatCount(t.counts.leads)} leads</p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  );
}

/**
 * Counts change by their difference; rates by percentage points ("+1.24 pts"). Green or red only when the direction is
 * clearly good or bad; always with an arrow and words.
 */
function Change({ now, before, rate, better }: { now: number | null; before: number | null; rate: boolean; better: Row['better'] }) {
  if (now === null || before === null) return <span className="text-xs text-muted-foreground">—</span>;
  const diff = Math.round((now - before) * 100) / 100;
  const amount = rate ? `${Math.abs(diff).toFixed(2)} pts` : formatCount(Math.abs(diff));
  return <Delta value={diff} amount={amount} better={better} />;
}
