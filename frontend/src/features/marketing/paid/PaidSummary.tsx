import { IndianRupee, PiggyBank, ReceiptIndianRupee, UserPlus, Wallet } from 'lucide-react';
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
import { formatCount, formatInr, formatPercent, MONTH_NAMES } from '../marketing-format';
import { usePaidSummary, type PaidMonthTotals } from './api';
import { BudgetBar } from './PaidBadges';
import { PAID_RATE_HINTS, PLATFORM_LABELS } from './paid-meta';

type Compare = 'previous' | 'lastYear';
type Kind = 'count' | 'money' | 'percent';

interface Row {
  label: string;
  hint?: string;
  value: (m: PaidMonthTotals) => number | null;
  kind: Kind;
  /** Whether a rise is good news, bad news, or neither. */
  better: 'higher' | 'lower' | null;
}

const ROWS: Row[] = [
  { label: 'Campaigns with results', value: (m) => m.campaigns, kind: 'count', better: null },
  { label: 'Spend', value: (m) => m.figures.results.spend, kind: 'money', better: null },
  { label: 'Impressions', value: (m) => m.figures.results.impressions, kind: 'count', better: 'higher' },
  { label: 'Clicks', value: (m) => m.figures.results.clicks, kind: 'count', better: 'higher' },
  { label: 'Leads', value: (m) => m.figures.results.leads, kind: 'count', better: 'higher' },
  { label: 'Conversions', value: (m) => m.figures.results.conversions, kind: 'count', better: 'higher' },
  { label: 'CTR', hint: PAID_RATE_HINTS.ctr, value: (m) => m.figures.rates.ctr, kind: 'percent', better: 'higher' },
  { label: 'Cost per lead', hint: PAID_RATE_HINTS.costPerLead, value: (m) => m.figures.rates.costPerLead, kind: 'money', better: 'lower' },
  { label: 'Conversion rate', hint: PAID_RATE_HINTS.conversionRate, value: (m) => m.figures.rates.conversionRate, kind: 'percent', better: 'higher' },
  { label: 'Cost per click', hint: PAID_RATE_HINTS.costPerClick, value: (m) => m.figures.rates.costPerClick, kind: 'money', better: 'lower' },
];

const show = (value: number | null, kind: Kind) => (kind === 'money' ? formatInr(value) : kind === 'percent' ? formatPercent(value) : formatCount(value));

/**
 * Brief sections 41–42: the budget of the campaigns running in the month (budget, spent to date, remaining) and the
 * month's leads and cost per lead against another month, as KPI cards and a full comparison table.
 */
export function PaidSummary({ filters }: { filters: MarketingFilters }) {
  const [compare, setCompare] = useState<Compare>('previous');
  const summary = usePaidSummary({
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

  const { current, comparison, byPlatform, budget } = summary.data;
  const short = MONTH_NAMES[comparison.period.month - 1] ?? comparison.period.label;
  const compareLabel = comparison.period.year === current.period.year ? short : comparison.period.label;
  const loading = summary.isPlaceholderData;
  const running = budget.campaigns === 1 ? '1 campaign running' : `${budget.campaigns} campaigns running`;
  const progress = budget.progress;

  return (
    <div className="space-y-6">
      <section aria-label={`Paid results for ${current.period.label}`} className="grid gap-4 sm:gap-6 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6">
        <MarketingKpiCard label="Budget" icon={Wallet} value={progress.budget} format="currency" loading={loading} hint={`${running} in ${MONTH_NAMES[current.period.month - 1]}`} />
        <MarketingKpiCard label="Spent to date" icon={ReceiptIndianRupee} value={progress.spent} format="currency" loading={loading}>
          {budget.campaigns > 0 && <BudgetBar label="Budget used" progress={progress} className="mt-2" compact />}
        </MarketingKpiCard>
        <MarketingKpiCard label="Remaining budget" icon={PiggyBank} value={progress.remaining} format="currency" loading={loading} hint="Budget − spent to date">
          {progress.overBudget && <p className="mt-1 text-xs font-medium text-status-danger">Over budget</p>}
        </MarketingKpiCard>
        <MarketingKpiCard label="Spend this month" icon={IndianRupee} value={current.figures.results.spend} format="currency" previous={comparison.figures.results.spend} previousLabel={compareLabel} better={null} loading={loading} />
        <MarketingKpiCard label="Leads" icon={UserPlus} value={current.figures.results.leads} previous={comparison.figures.results.leads} previousLabel={compareLabel} loading={loading} />
        <MarketingKpiCard label="Cost per lead" icon={IndianRupee} value={current.figures.rates.costPerLead} format="currency" previous={comparison.figures.rates.costPerLead} previousLabel={compareLabel} better="lower" loading={loading} hint={PAID_RATE_HINTS.costPerLead} />
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,20rem)]">
        <Card className={cn(loading && 'opacity-60')}>
          <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
            <div>
              <h2 className="text-card-title font-semibold">Monthly summary</h2>
              <p className="mt-0.5 text-label text-muted-foreground">Rates come from the month&apos;s totals, never averaged.</p>
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
                    <TableCell numeric className="font-semibold">{show(now, row.kind)}</TableCell>
                    <TableCell numeric className="text-muted-foreground">{show(before, row.kind)}</TableCell>
                    <TableCell numeric>
                      <Change now={now} before={before} kind={row.kind} better={row.better} />
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </Card>

        <Card>
          <div className="border-b px-6 py-4">
            <h2 className="text-card-title font-semibold">By platform</h2>
            <p className="mt-0.5 text-label text-muted-foreground">{current.period.label}</p>
          </div>
          {byPlatform.length === 0 ? (
            <p className="px-6 py-4 text-sm text-muted-foreground">No results recorded this month.</p>
          ) : (
            <ul className="divide-y" aria-label="Platforms">
              {byPlatform.map((p) => (
                <li key={p.platform} className="flex items-center justify-between gap-3 px-6 py-3 text-sm">
                  <div>
                    <p className="font-medium">{PLATFORM_LABELS[p.platform]}</p>
                    <p className="text-xs text-muted-foreground">
                      {p.campaigns} {p.campaigns === 1 ? 'campaign' : 'campaigns'} · {formatInr(p.figures.results.spend)} spent
                    </p>
                  </div>
                  <div className="text-right text-xs tabular-nums">
                    <p>{formatCount(p.figures.results.leads)} leads</p>
                    <p className="text-muted-foreground">{formatInr(p.figures.rates.costPerLead)} per lead</p>
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
 * Counts and amounts change by their difference; rates by percentage points. Green or red only when the direction is
 * clearly good or bad; always with an arrow and words.
 */
function Change({ now, before, kind, better }: { now: number | null; before: number | null; kind: Kind; better: Row['better'] }) {
  if (now === null || before === null) return <span className="text-xs text-muted-foreground">—</span>;
  const diff = Math.round((now - before) * 100) / 100;
  const amount = kind === 'percent' ? `${Math.abs(diff).toFixed(2)} pts` : show(Math.abs(diff), kind);
  return <Delta value={diff} amount={amount} better={better} />;
}
