import { Download, FileUp, Lock, Plus, UserPlus } from 'lucide-react';
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
import { useIdParam } from '@/lib/use-id-param';

import { useImportDefinitions } from '../api';
import { CsvImportDialog } from '../components/CsvImportDialog';
import { MarketingFilterBar } from '../components/MarketingFilterBar';
import type { MarketingFilters } from '../filter-memory';
import { periodLabel } from '../marketing-format';
import { LEAD_SOURCES, type LeadSource } from '../targets/api';
import { LEAD_SOURCE_LABELS } from '../targets/target-meta';
import { useMarketingFilters } from '../use-marketing-filters';
import { exportLeads, LEAD_IMPORT_TYPE, LEAD_STATUSES, useLeads, type LeadQuery, type LeadStatus } from './api';
import { LeadFormDialog } from './LeadFormDialog';
import { LeadSheet } from './LeadSheet';
import { LeadStatusBadge } from './LeadBadges';
import { LEAD_STATUS_LABELS } from './lead-meta';
import { LeadSummary } from './LeadSummary';
import { LeadTrendPanel } from './LeadTrendPanel';

const SORTS = [
  { value: 'date,desc', label: 'Newest first' },
  { value: 'date,asc', label: 'Oldest first' },
  { value: 'name,asc', label: 'Name' },
  { value: 'company,asc', label: 'Company' },
  { value: 'updated,desc', label: 'Recently updated' },
];

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

/** Leads (brief sections 43–44): the month against its targets by source, the trend, and the lead table. */
export default function LeadsPage() {
  const { user } = useAuth();
  const { filters } = useMarketingFilters();
  const [adding, setAdding] = useState(false);
  const [importing, setImporting] = useState(false);
  const [openId, setOpenId] = useIdParam('lead');
  // Shared with the summary: choosing a source there narrows the table.
  const [source, setSource] = useState('');
  const canEdit = hasPermission(user, 'LEAD_EDIT');
  // Offered by the server only to users who may run it (LEAD_EDIT).
  const leadImport = useImportDefinitions().data?.find((d) => d.type === LEAD_IMPORT_TYPE);

  function pickSource(picked: LeadSource) {
    setSource(picked);
    document.getElementById('lead-table')?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Leads"
        description={filters ? `Marketing leads by source and status · ${periodLabel(filters.month, filters.year)}` : 'Marketing leads by source and status'}
        actions={
          canEdit && (
            <div className="flex flex-wrap gap-2">
              {leadImport && (
                <Button variant="outline" onClick={() => setImporting(true)}>
                  <FileUp aria-hidden />
                  Import leads
                </Button>
              )}
              <Button onClick={() => setAdding(true)}>
                <Plus aria-hidden />
                New lead
              </Button>
            </div>
          )
        }
      />
      <MarketingFilterBar />
      {filters ? (
        <>
          <LeadSummary filters={filters} onPickSource={pickSource} />
          <LeadTrendPanel filters={filters} />
          <LeadList filters={filters} canEdit={canEdit} source={source} onSourceChange={setSource} onOpen={setOpenId} />
          <LeadSheet leadId={openId} onOpenChange={(open) => !open && setOpenId(null)} canEdit={canEdit} />
        </>
      ) : (
        <Skeleton className="h-96 rounded-xl" role="status" aria-label="Loading leads" />
      )}
      <LeadFormDialog open={adding} onOpenChange={setAdding} onCreated={setOpenId} />
      {leadImport && <CsvImportDialog definition={leadImport} open={importing} onOpenChange={setImporting} />}
    </div>
  );
}

interface LeadListProps {
  filters: MarketingFilters;
  canEdit: boolean;
  source: string;
  onSourceChange: (source: string) => void;
  onOpen: (id: number) => void;
}

function LeadList({ filters, canEdit, source, onSourceChange, onOpen }: LeadListProps) {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [allMonths, setAllMonths] = useState(false);
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState(false);
  const debounced = useDebouncedValue(search.trim());

  const query: Omit<LeadQuery, 'page' | 'size' | 'sort'> = {
    search: debounced,
    source: source ? [source as LeadSource] : undefined,
    status: status ? [status as LeadStatus] : undefined,
    ownerId: filters.ownerId ?? undefined,
    month: allMonths ? undefined : filters.month,
    year: allMonths ? undefined : filters.year,
  };
  const leads = useLeads({ ...query, sort, page, size: 25 });
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
      await exportLeads(query);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  }

  const filtered = Boolean(search || source || status);

  return (
    <Card id="lead-table" className="scroll-mt-4">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <div>
          <h2 className="text-card-title font-semibold">Leads</h2>
          <p className="mt-0.5 text-label text-muted-foreground">{allMonths ? 'Every lead' : `Dated in ${month}`}</p>
        </div>
        <Button variant="outline" size="sm" disabled={exporting || !leads.data?.totalElements} onClick={() => void onExport()}>
          <Download aria-hidden />
          Export CSV
        </Button>
      </div>
      <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-40 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
        <SearchInput placeholder="Search name, company, email or code" aria-label="Search leads" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
        <Select aria-label="Lead source" value={source} onChange={(e) => filter(onSourceChange)(e.target.value)}>
          <option value="">All sources</option>
          {LEAD_SOURCES.map((s) => (
            <option key={s} value={s}>
              {LEAD_SOURCE_LABELS[s]}
            </option>
          ))}
        </Select>
        <Select aria-label="Lead status" value={status} onChange={(e) => filter(setStatus)(e.target.value)}>
          <option value="">All statuses</option>
          {LEAD_STATUSES.map((s) => (
            <option key={s} value={s}>
              {LEAD_STATUS_LABELS[s]}
            </option>
          ))}
        </Select>
        <Select aria-label="Sort leads" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
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
      {leads.isPending ? (
        <div className="space-y-3 p-4" role="status" aria-label="Loading the lead table">
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} className="h-12" />
          ))}
        </div>
      ) : leads.isError ? (
        <ErrorState error={leads.error} title="Couldn't load leads" onRetry={() => void leads.refetch()} />
      ) : leads.data.content.length === 0 ? (
        <EmptyState
          icon={UserPlus}
          title={filtered ? 'No leads match these filters' : allMonths ? 'No leads yet' : `No leads dated in ${month}`}
          description={canEdit ? 'Add leads by hand or import a website form or CRM export.' : undefined}
        />
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Lead</TableHead>
                <TableHead>Company</TableHead>
                <TableHead>Source</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Owner</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody className={cn(leads.isPlaceholderData && 'opacity-60')}>
              {leads.data.content.map((l) => (
                <TableRow key={l.id}>
                  <TableCell className="max-w-64">
                    <button type="button" className="block max-w-full truncate text-left font-medium hover:underline" onClick={() => onOpen(l.id)}>
                      {l.name}
                    </button>
                    <p className="truncate text-xs text-muted-foreground">
                      <span className="font-mono">{l.code}</span>
                      {l.email && ` · ${l.email}`}
                    </p>
                  </TableCell>
                  <TableCell className="max-w-48 truncate text-sm">{l.company ?? <span className="text-muted-foreground">—</span>}</TableCell>
                  <TableCell className="max-w-56 text-sm">
                    {LEAD_SOURCE_LABELS[l.source]}
                    {l.link && (
                      <p className="truncate text-xs text-muted-foreground" title={l.link.name}>
                        {l.link.name}
                      </p>
                    )}
                  </TableCell>
                  <TableCell className="text-sm whitespace-nowrap">
                    <span className="inline-flex items-center gap-1">
                      {dayFormat.format(parseLocalDate(l.leadDate))}
                      {l.countLocked && (
                        <span className="text-muted-foreground" title="Month closed: date and source stay as counted">
                          <Lock className="size-3.5" aria-hidden />
                          <span className="sr-only">(month closed)</span>
                        </span>
                      )}
                    </span>
                  </TableCell>
                  <TableCell>
                    <LeadStatusBadge status={l.status} />
                  </TableCell>
                  <TableCell className="max-w-44">{l.owner ? <UserCell name={l.owner.fullName} /> : <span className="text-sm text-muted-foreground">No owner</span>}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Pagination {...leads.data} onPageChange={setPage} />
        </>
      )}
    </Card>
  );
}
