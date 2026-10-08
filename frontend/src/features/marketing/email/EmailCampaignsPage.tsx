import { Download, FileUp, Mail, Pencil, Plus } from 'lucide-react';
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
import { formatCount, formatPercent, periodLabel } from '../marketing-format';
import { useMarketingFilters } from '../use-marketing-filters';
import { CAMPAIGN_STATUSES, CAMPAIGN_TYPES, EMAIL_IMPORT_TYPE, exportCampaigns, useEmailCampaigns, type CampaignQuery, type EmailCampaign, type EmailCampaignStatus, type EmailCampaignType } from './api';
import { CampaignFormDialog } from './CampaignFormDialog';
import { CampaignStatusBadge } from './CampaignStatusBadge';
import { CAMPAIGN_STATUS_LABELS, CAMPAIGN_TYPE_LABELS } from './email-meta';
import { EmailSummary } from './EmailSummary';
import { EmailTrendPanel } from './EmailTrendPanel';

const SORTS = [
  { value: 'date,desc', label: 'Newest first' },
  { value: 'leads,desc', label: 'Most leads' },
  { value: 'sent,desc', label: 'Most emails sent' },
  { value: 'name,asc', label: 'Name' },
];

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

/** Email Campaigns (brief sections 37–39): monthly results, trend, and the campaign table. */
export default function EmailCampaignsPage() {
  const { user } = useAuth();
  const { filters } = useMarketingFilters();
  const [adding, setAdding] = useState(false);
  const [importing, setImporting] = useState(false);
  const canEdit = hasPermission(user, 'CAMPAIGN_EDIT');
  // Offered by the server only to users who may run it (CAMPAIGN_EDIT).
  const emailImport = useImportDefinitions().data?.find((d) => d.type === EMAIL_IMPORT_TYPE);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Email Campaigns"
        description={filters ? `Zoho email campaigns · ${periodLabel(filters.month, filters.year)}` : 'Zoho email campaigns'}
        actions={
          canEdit && (
            <div className="flex flex-wrap gap-2">
              {emailImport && (
                <Button variant="outline" onClick={() => setImporting(true)}>
                  <FileUp aria-hidden />
                  Import CSV
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
          <EmailSummary filters={filters} />
          <EmailTrendPanel filters={filters} />
          <CampaignList filters={filters} canEdit={canEdit} />
        </>
      ) : (
        <Skeleton className="h-96 rounded-xl" role="status" aria-label="Loading email campaigns" />
      )}
      <CampaignFormDialog open={adding} onOpenChange={setAdding} />
      {emailImport && <CsvImportDialog definition={emailImport} open={importing} onOpenChange={setImporting} />}
    </div>
  );
}

function CampaignList({ filters, canEdit }: { filters: MarketingFilters; canEdit: boolean }) {
  const [search, setSearch] = useState('');
  const [type, setType] = useState('');
  const [status, setStatus] = useState('');
  const [allMonths, setAllMonths] = useState(false);
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<EmailCampaign | null>(null);
  const [exporting, setExporting] = useState(false);
  const debounced = useDebouncedValue(search.trim());

  const query: Omit<CampaignQuery, 'page' | 'size' | 'sort'> = {
    search: debounced,
    type: type ? [type as EmailCampaignType] : undefined,
    status: status ? [status as EmailCampaignStatus] : undefined,
    ownerId: filters.ownerId ?? undefined,
    month: allMonths ? undefined : filters.month,
    year: allMonths ? undefined : filters.year,
  };
  const campaigns = useEmailCampaigns({ ...query, sort, page, size: 25 });

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  async function onExport() {
    setExporting(true);
    try {
      await exportCampaigns(query);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  }

  const filtered = Boolean(search || type || status);

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-3.5">
        <div>
          <h2 className="font-semibold">Campaigns</h2>
          <p className="text-sm text-muted-foreground">{allMonths ? 'All months' : periodLabel(filters.month, filters.year)}</p>
        </div>
        <Button variant="outline" size="sm" disabled={exporting || !campaigns.data?.totalElements} onClick={() => void onExport()}>
          <Download aria-hidden />
          Export CSV
        </Button>
      </div>
      <div className="grid gap-3 border-b p-4 sm:grid-cols-2 lg:grid-cols-[1fr_repeat(3,minmax(0,11rem))_auto]">
        <SearchInput placeholder="Search name or audience" aria-label="Search campaigns" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
        <Select aria-label="Campaign type" value={type} onChange={(e) => filter(setType)(e.target.value)}>
          <option value="">All types</option>
          {CAMPAIGN_TYPES.map((t) => (
            <option key={t} value={t}>
              {CAMPAIGN_TYPE_LABELS[t]}
            </option>
          ))}
        </Select>
        <Select aria-label="Campaign status" value={status} onChange={(e) => filter(setStatus)(e.target.value)}>
          <option value="">All statuses</option>
          {CAMPAIGN_STATUSES.map((s) => (
            <option key={s} value={s}>
              {CAMPAIGN_STATUS_LABELS[s]}
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
          icon={Mail}
          title={filtered ? 'No campaigns match these filters' : allMonths ? 'No email campaigns yet' : `No campaigns in ${periodLabel(filters.month, filters.year)}`}
          description={canEdit ? 'Add a campaign after each send, or import a Zoho export.' : undefined}
        />
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Campaign</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Sent</TableHead>
                <TableHead className="text-right">Open rate</TableHead>
                <TableHead className="text-right">Click rate</TableHead>
                <TableHead className="text-right">Leads</TableHead>
                <TableHead>Owner</TableHead>
                {canEdit && (
                  <TableHead>
                    <span className="sr-only">Actions</span>
                  </TableHead>
                )}
              </TableRow>
            </TableHeader>
            <TableBody className={cn(campaigns.isPlaceholderData && 'opacity-60')}>
              {campaigns.data.content.map((c) => {
                const sent = c.status === 'SENT';
                return (
                  <TableRow key={c.id}>
                    <TableCell className="max-w-sm">
                      <p className="truncate font-medium">{c.name}</p>
                      <p className="truncate text-xs text-muted-foreground">
                        {CAMPAIGN_TYPE_LABELS[c.campaignType]}
                        {c.audience ? ` · ${c.audience}` : ''}
                      </p>
                    </TableCell>
                    <TableCell className="text-sm whitespace-nowrap">{dayFormat.format(parseLocalDate(c.campaignDate))}</TableCell>
                    <TableCell>
                      <CampaignStatusBadge status={c.status} />
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{sent ? formatCount(c.counts.emailsSent) : '—'}</TableCell>
                    <TableCell className="text-right tabular-nums">{sent ? formatPercent(c.rates.openRate) : '—'}</TableCell>
                    <TableCell className="text-right tabular-nums">{sent ? formatPercent(c.rates.clickRate) : '—'}</TableCell>
                    <TableCell className="text-right tabular-nums">{sent ? formatCount(c.counts.leads) : '—'}</TableCell>
                    <TableCell className="max-w-44">{c.owner ? <UserCell name={c.owner.fullName} /> : <span className="text-sm text-muted-foreground">No owner</span>}</TableCell>
                    {canEdit && (
                      <TableCell>
                        <Button variant="ghost" size="icon" aria-label={`Edit ${c.name}`} onClick={() => setEditing(c)}>
                          <Pencil aria-hidden />
                        </Button>
                      </TableCell>
                    )}
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
          <Pagination {...campaigns.data} onPageChange={setPage} />
        </>
      )}
      <CampaignFormDialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)} campaign={editing ?? undefined} />
    </Card>
  );
}
