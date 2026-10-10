import { CircleCheck, Megaphone, Percent, Target, UserPlus } from 'lucide-react';
import { useState } from 'react';

import { Delta } from '@/components/common/Delta';
import { ErrorState } from '@/components/common/ErrorState';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { cn } from '@/lib/utils';

import { MarketingKpiCard } from '../components/MarketingKpiCard';
import { TargetBar, TargetProgress, TargetStatusBadge } from '../components/TargetProgress';
import type { MarketingFilters } from '../filter-memory';
import { formatCount, formatPercent, MONTH_NAMES } from '../marketing-format';
import type { LeadSource } from '../targets/api';
import { LEAD_SOURCE_LABELS } from '../targets/target-meta';
import { useLeadSummary } from './api';
import { LeadStatusBadge } from './LeadBadges';
import { LINK_KIND_LABELS } from './lead-meta';

type Compare = 'previous' | 'lastYear';

interface LeadSummaryProps {
  filters: MarketingFilters;
  /** Narrows the lead table to one source. */
  onPickSource: (source: LeadSource) => void;
}

/**
 * Brief sections 43–44: the month's leads against the Website Leads target, by source (each against its own target),
 * by status, and the campaigns and content that brought the most in. Targets show only for TARGET_VIEW holders.
 */
export function LeadSummary({ filters, onPickSource }: LeadSummaryProps) {
  const [compare, setCompare] = useState<Compare>('previous');
  const summary = useLeadSummary({
    month: filters.month,
    year: filters.year,
    ownerId: filters.ownerId ?? undefined,
    ...(compare === 'lastYear' ? { compareMonth: filters.month, compareYear: filters.year - 1 } : {}),
  });

  if (summary.isError) {
    return (
      <Card>
        <ErrorState error={summary.error} title="Couldn't load the lead summary" onRetry={() => void summary.refetch()} />
      </Card>
    );
  }
  if (!summary.data) {
    return (
      <div className="space-y-4" role="status" aria-label="Loading the lead summary">
        <div className="grid gap-4 sm:gap-6 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }

  const data = summary.data;
  const short = MONTH_NAMES[data.comparisonPeriod.month - 1] ?? data.comparisonPeriod.label;
  const compareLabel = data.comparisonPeriod.year === data.period.year ? short : data.comparisonPeriod.label;
  const loading = summary.isPlaceholderData;
  const converted = data.byStatus.find((s) => s.status === 'CONVERTED')?.leads ?? 0;
  const target = data.totalTarget;
  const maxStatus = Math.max(...data.byStatus.map((s) => s.leads), 1);

  return (
    <div className="space-y-6">
      <section aria-label={`Leads for ${data.period.label}`} className="grid gap-4 sm:gap-6 sm:grid-cols-2 xl:grid-cols-4">
        <MarketingKpiCard label="Leads generated" icon={UserPlus} value={data.total} previous={data.comparisonTotal} previousLabel={compareLabel} loading={loading} hint="Every lead dated in the month, any status" />
        {data.targetsVisible &&
          (target ? (
            <TargetProgress
              label="Website Leads target"
              target={target.targetValue}
              actual={target.actual}
              achievementPct={target.achievementPct}
              remaining={target.remaining}
              status={target.status}
            />
          ) : (
            <MarketingKpiCard label="Website Leads target" icon={Target} value={null} hint={`No target set for ${data.period.label}`} />
          ))}
        <MarketingKpiCard label="Converted" icon={CircleCheck} value={converted} loading={loading} hint={`${formatCount(data.total)} leads this month`} />
        <MarketingKpiCard label="Conversion share" icon={Percent} value={data.convertedPct} format="percent" loading={loading} hint="Converted ÷ all leads" />
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,22rem)]">
        <Card className={cn(loading && 'opacity-60')}>
          <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
            <div>
              <h2 className="text-card-title font-semibold">Leads by source</h2>
              <p className="mt-0.5 text-label text-muted-foreground">{data.targetsVisible ? 'Each source against its monthly target.' : 'Choose a source to list its leads.'}</p>
            </div>
            <Select aria-label="Compare with" className="w-56" value={compare} onChange={(e) => setCompare(e.target.value as Compare)}>
              <option value="previous">Compare with the previous month</option>
              <option value="lastYear">Compare with the same month last year</option>
            </Select>
          </div>
          <Table aria-label={`Leads by source, ${data.period.label}`}>
            <TableHeader>
              <TableRow>
                <TableHead>Source</TableHead>
                <TableHead numeric>{data.period.label}</TableHead>
                <TableHead numeric>{data.comparisonPeriod.label}</TableHead>
                <TableHead numeric>Change</TableHead>
                {data.targetsVisible && <TableHead className="min-w-52">Target</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.bySource.map((row) => (
                <TableRow key={row.source}>
                  <TableCell>
                    <button type="button" className="font-medium whitespace-nowrap hover:underline" onClick={() => onPickSource(row.source)}>
                      {LEAD_SOURCE_LABELS[row.source]}
                    </button>
                  </TableCell>
                  <TableCell numeric className="font-semibold">{formatCount(row.leads)}</TableCell>
                  <TableCell numeric className="text-muted-foreground">{formatCount(row.comparison)}</TableCell>
                  <TableCell numeric>
                    <CountChange now={row.leads} before={row.comparison} />
                  </TableCell>
                  {data.targetsVisible && (
                    <TableCell>
                      {row.target ? (
                        <div className="space-y-1">
                          <div className="flex items-center justify-between gap-2 text-xs tabular-nums">
                            <span>
                              {formatCount(row.target.actual)} of {formatCount(row.target.targetValue)} · {formatPercent(row.target.achievementPct)}
                            </span>
                            <TargetStatusBadge status={row.target.status} />
                          </div>
                          <TargetBar label={`${LEAD_SOURCE_LABELS[row.source]} target achievement`} achievementPct={row.target.achievementPct} status={row.target.status} />
                        </div>
                      ) : (
                        <span className="text-xs text-muted-foreground">No target</span>
                      )}
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>

        <div className="space-y-6">
          <Card className={cn(loading && 'opacity-60')}>
            <div className="border-b px-6 py-4">
              <h2 className="text-card-title font-semibold">By status</h2>
              <p className="mt-0.5 text-label text-muted-foreground">Leads dated in {data.period.label}, by their current status</p>
            </div>
            <ul className="space-y-3 px-6 py-4" aria-label="Leads by status">
              {data.byStatus.map((s) => (
                <li key={s.status} className="space-y-1">
                  <div className="flex items-center justify-between gap-2 text-sm">
                    <LeadStatusBadge status={s.status} />
                    <span className="font-medium tabular-nums">{formatCount(s.leads)}</span>
                  </div>
                  <div className="h-1.5 overflow-hidden rounded-full bg-muted" aria-hidden>
                    <div className="h-full rounded-full bg-primary/70" style={{ width: `${(s.leads / maxStatus) * 100}%` }} />
                  </div>
                </li>
              ))}
            </ul>
          </Card>

          <Card className={cn(loading && 'opacity-60')}>
            <div className="border-b px-6 py-4">
              <h2 className="text-card-title font-semibold">Top campaigns & content</h2>
              <p className="mt-0.5 text-label text-muted-foreground">Most leads in {data.period.label}</p>
            </div>
            {data.topLinks.length === 0 ? (
              <p className="flex items-center gap-2 px-6 py-4 text-sm text-muted-foreground">
                <Megaphone className="size-4" aria-hidden />
                No leads linked to a campaign or content this month.
              </p>
            ) : (
              <ol className="divide-y" aria-label="Top campaigns and content">
                {data.topLinks.map((l) => (
                  <li key={`${l.kind}-${l.id}`} className="flex items-center justify-between gap-3 px-6 py-2.5 text-sm">
                    <div className="min-w-0">
                      <p className="truncate font-medium" title={l.name}>
                        {l.name}
                      </p>
                      <p className="text-xs text-muted-foreground">{LINK_KIND_LABELS[l.kind]}</p>
                    </div>
                    <span className="shrink-0 tabular-nums">
                      {formatCount(l.leads)} {l.leads === 1 ? 'lead' : 'leads'}
                    </span>
                  </li>
                ))}
              </ol>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}

/** More leads is good news; the change is in words and an arrow as well as colour. */
function CountChange({ now, before }: { now: number; before: number }) {
  const diff = now - before;
  return <Delta value={diff} amount={formatCount(Math.abs(diff))} />;
}
