import { Download, FileUp, MousePointerClick, Plus } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { parseLocalDate } from '@/features/tasks/task-meta';
import { errorMessage } from '@/lib/api/errors';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { cn } from '@/lib/utils';

import { useImportDefinitions } from '../api';
import { CsvImportDialog } from '../components/CsvImportDialog';
import { MarketingFilterBar } from '../components/MarketingFilterBar';
import type { MarketingFilters } from '../filter-memory';
import { formatCount, formatInr, periodLabel } from '../marketing-format';
import { useMarketingFilters } from '../use-marketing-filters';
import { AD_PLATFORMS, exportPaidCampaigns, PAID_IMPORT_TYPE, PAID_STATUSES, usePaidCampaigns, type AdPlatform, type PaidCampaignQuery, type PaidCampaignStatus } from './api';
import { PaidCampaignFormDialog } from './PaidCampaignFormDialog';
import { PaidCampaignSheet } from './PaidCampaignSheet';
import { BudgetBar, PaidStatusBadge } from './PaidBadges';
import { OBJECTIVE_LABELS, PAID_STATUS_LABELS, PLATFORM_LABELS } from './paid-meta';
import { PaidSummary } from './PaidSummary';
import { PaidTrendPanel } from './PaidTrendPanel';

const SORTS = [
  { value: 'start,desc', label: 'Newest first' },
  { value: 'budget,desc', label: 'Largest budget' },
  { value: 'name,asc', label: 'Name' },
  { value: 'updated,desc', label: 'Recently updated' },
];

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

/** Paid Campaigns (brief sections 40–42): budget and monthly results, spend vs leads, and the campaign table. */
export default function PaidCampaignsPage() {
  const { user } = useAuth();
  const { filters } = useMarketingFilters();
  const [adding, setAdding] = useState(false);
  const [importing, setImporting] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);
  const canEdit = hasPermission(user, 'CAMPAIGN_EDIT');
  // Offered by the server only to users who may run it (CAMPAIGN_EDIT).
  const paidImport = useImportDefinitions().data?.find((d) => d.type === PAID_IMPORT_TYPE);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Paid Campaigns"
        description={filters ? `LinkedIn spend, leads and cost per lead · ${periodLabel(filters.month, filters.year)}` : 'LinkedIn spend, leads and cost per lead'}
        actions={
          canEdit && (
            <div className="flex flex-wrap gap-2">
              {paidImport && (
                <Button variant="outline" onClick={() => setImporting(true)}>
                  <FileUp aria-hidden />
                  Import results
                </Button>
              )}
              <Button onClick={() => setAdding(true)}>
                <Plus aria-hidden />
                New campaign
              </Button>
            </div>
          )
        }
      />
      <MarketingFilterBar />
      {filters ? (
        <>
          <PaidSummary filters={filters} />
          <PaidTrendPanel filters={filters} />
          <CampaignList filters={filters} canEdit={canEdit} onOpen={setOpenId} />
          <PaidCampaignSheet campaignId={openId} onOpenChange={(open) => !open && setOpenId(null)} month={filters.month} year={filters.year} />
        </>
      ) : (
        <Skeleton className="h-96 rounded-xl" role="status" aria-label="Loading paid campaigns" />
      )}
      <PaidCampaignFormDialog open={adding} onOpenChange={setAdding} onCreated={setOpenId} />
      {paidImport && <CsvImportDialog definition={paidImport} open={importing} onOpenChange={setImporting} />}
    </div>
  );
}

function CampaignList({ filters, canEdit, onOpen }: { filters: MarketingFilters; canEdit: boolean; onOpen: (id: number) => void }) {
  const [search, setSearch] = useState('');
  const [platform, setPlatform] = useState('');
  const [status, setStatus] = useState('');
  const [allMonths, setAllMonths] = useState(false);
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState(false);
  const debounced = useDebouncedValue(search.trim());

  const query: Omit<PaidCampaignQuery, 'page' | 'size' | 'sort'> = {
    search: debounced,
    platform: platform ? [platform as AdPlatform] : undefined,
    status: status ? [status as PaidCampaignStatus] : undefined,
    ownerId: filters.ownerId ?? undefined,
    month: allMonths ? undefined : filters.month,
    year: allMonths ? undefined : filters.year,
  };
  const campaigns = usePaidCampaigns({ ...query, sort, page, size: 25 });
  const month = periodLabel(filters.month, filters.year);

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  async function onExport() {
    setExporting(true);
    try {
      await exportPaidCampaigns(query);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  }

  const filtered = Boolean(search || platform || status);

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-3.5">
        <div>
          <h2 className="font-semibold">Campaigns</h2>
          <p className="text-sm text-muted-foreground">{allMonths ? 'All campaigns, lifetime figures' : `Running in ${month}, with that month’s figures`}</p>
        </div>
        <Button variant="outline" size="sm" disabled={exporting || !campaigns.data?.totalElements} onClick={() => void onExport()}>
          <Download aria-hidden />
          Export CSV
        </Button>
      </div>
      <div className="grid gap-3 border-b p-4 sm:grid-cols-2 lg:grid-cols-[1fr_repeat(3,minmax(0,11rem))_auto]">
        <SearchInput placeholder="Search campaigns" aria-label="Search campaigns" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
        <Select aria-label="Platform" value={platform} onChange={(e) => filter(setPlatform)(e.target.value)}>
          <option value="">All platforms</option>
          {AD_PLATFORMS.map((p) => (
            <option key={p} value={p}>
              {PLATFORM_LABELS[p]}
            </option>
          ))}
        </Select>
        <Select aria-label="Campaign status" value={status} onChange={(e) => filter(setStatus)(e.target.value)}>
          <option value="">All statuses</option>
          {PAID_STATUSES.map((s) => (
            <option key={s} value={s}>
              {PAID_STATUS_LABELS[s]}
            </option>
          ))}
        </Select>
        <Select aria-label="Sort campaigns" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
          {SORTS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </Select>
        <label className="flex items-center gap-2 text-sm whitespace-nowrap">
          <Checkbox checked={allMonths} onChange={(e) => filter(setAllMonths)(e.target.checked)} />
          All months
        </label>
      </div>
      {campaigns.isPending ? (
        <div className="space-y-3 p-4" role="status" aria-label="Loading campaigns">
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} className="h-12" />
          ))}
        </div>
      ) : campaigns.isError ? (
        <ErrorState error={campaigns.error} title="Couldn't load campaigns" onRetry={() => void campaigns.refetch()} />
      ) : campaigns.data.content.length === 0 ? (
        <EmptyState
          icon={MousePointerClick}
          title={filtered ? 'No campaigns match these filters' : allMonths ? 'No paid campaigns yet' : `No campaigns running in ${month}`}
          description={canEdit ? 'Add a campaign with its dates and budget, then record its results each month.' : undefined}
        />
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Campaign</TableHead>
                <TableHead>Dates</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="min-w-44">Budget used</TableHead>
                <TableHead className="text-right">{allMonths ? 'Spend' : 'Month spend'}</TableHead>
                <TableHead className="text-right">Leads</TableHead>
                <TableHead className="text-right">Cost per lead</TableHead>
                <TableHead>Owner</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody className={cn(campaigns.isPlaceholderData && 'opacity-60')}>
              {campaigns.data.content.map((c) => {
                const figures = c.month ?? c.lifetime;
                return (
                  <TableRow key={c.id}>
                    <TableCell className="max-w-sm">
                      <button type="button" className="block max-w-full truncate text-left font-medium hover:underline" onClick={() => onOpen(c.id)}>
                        {c.name}
                      </button>
                      <p className="truncate text-xs text-muted-foreground">
                        {PLATFORM_LABELS[c.platform]} · {OBJECTIVE_LABELS[c.objective]}
                      </p>
                    </TableCell>
                    <TableCell className="text-sm whitespace-nowrap">
                      {dayFormat.format(parseLocalDate(c.startDate))}
                      <p className="text-xs text-muted-foreground">{c.endDate ? `to ${dayFormat.format(parseLocalDate(c.endDate))}` : 'No end date'}</p>
                    </TableCell>
                    <TableCell>
                      <PaidStatusBadge status={c.status} />
                    </TableCell>
                    <TableCell>
                      <p className="mb-1 text-xs tabular-nums">
                        {formatInr(c.budgetProgress.spent)} <span className="text-muted-foreground">of {formatInr(c.budget)}</span>
                      </p>
                      <BudgetBar label={`${c.name} budget used`} progress={c.budgetProgress} />
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{formatInr(figures.results.spend)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatCount(figures.results.leads)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatInr(figures.rates.costPerLead)}</TableCell>
                    <TableCell className="max-w-44">{c.owner ? <UserCell name={c.owner.fullName} /> : <span className="text-sm text-muted-foreground">No owner</span>}</TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
          <Pagination {...campaigns.data} onPageChange={setPage} />
        </>
      )}
    </Card>
  );
}
