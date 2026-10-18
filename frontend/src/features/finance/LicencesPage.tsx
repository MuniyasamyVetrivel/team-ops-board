import { AppWindow, Plus } from 'lucide-react';
import { useState } from 'react';
import { useSearchParams } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ExportCsvButton } from '@/features/celebrations/CsvButtons';
import { useTool } from '@/features/modules/tool-context';
import { ToolSection } from '@/features/modules/ToolSection';
import { formatLocalDate } from '@/lib/format';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useIdParam } from '@/lib/use-id-param';
import { useInitialSearch } from '@/lib/use-initial-search';
import { cn } from '@/lib/utils';

import { BILLING_CYCLES, exportLicences, LICENCE_IMPORT, LICENCE_STATUSES, useLicences, type BillingCycle, type Licence, type LicenceQuery, type LicenceStatus } from './api';
import { FinanceErrorState, LicenceRenewalBadge, LicenceStatusBadge, SeatUsage } from './FinanceBadges';
import { FinanceImportButton } from './FinanceImportButton';
import { BILLING_LABELS, costLabel, formatAud, LICENCE_STATUS_META } from './finance-meta';
import { useDepartmentChoices } from './finance-options';
import { LicenceFormDialog } from './LicenceFormDialog';
import { LicenceSheet } from './LicenceSheet';

const SORTS = [
  { value: 'name,asc', label: 'Name' },
  { value: 'renewal,asc', label: 'Renewing soonest' },
  { value: 'cost,desc', label: 'Highest cost' },
  { value: 'seats,desc', label: 'Most seats' },
  { value: 'vendor,asc', label: 'Vendor' },
  { value: 'updated,desc', label: 'Recently updated' },
];

const RENEWAL_WINDOWS = [
  { value: '7', label: 'Renewing in 7 days' },
  { value: '30', label: 'Renewing in 30 days' },
  { value: '90', label: 'Renewing in 90 days' },
];

/** Every tool and licence with its seats, cost and renewal. Opens one from ?licence=12 (reminders, search, vendor page). */
export default function LicencesPage() {
  const { canEdit } = useTool();
  const [params] = useSearchParams();
  const [openId, setOpenId] = useIdParam('licence');
  const [adding, setAdding] = useState(false);
  const [search, setSearch] = useState(useInitialSearch());
  const [status, setStatus] = useState<LicenceStatus | ''>(() => (LICENCE_STATUSES as string[]).includes(params.get('status') ?? '') ? (params.get('status') as LicenceStatus) : '');
  const [billingCycle, setBillingCycle] = useState<BillingCycle | ''>('');
  const [departmentId, setDepartmentId] = useState('');
  const [renewalWithinDays, setRenewalWithinDays] = useState(params.get('renewalWithinDays') ?? '');
  const [sort, setSort] = useState(params.get('sort') ?? SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(search.trim());
  const { departments } = useDepartmentChoices(null);

  const filters: Omit<LicenceQuery, 'page' | 'size' | 'sort'> = {
    search: debounced,
    status: status || undefined,
    billingCycle: billingCycle || undefined,
    departmentId: departmentId ? Number(departmentId) : undefined,
    renewalWithinDays: renewalWithinDays ? Number(renewalWithinDays) : undefined,
  };
  const licences = useLicences({ ...filters, sort, page, size: 25 });
  const filtered = Boolean(debounced || status || billingCycle || departmentId || renewalWithinDays);

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <div className="space-y-4">
      <ToolSection
        title="Tools and licences"
        description="Software and subscriptions with their seats, cost and renewal. Under-used means fewer than 70% of the paid seats are assigned."
        actions={
          <>
            {canEdit && <FinanceImportButton type={LICENCE_IMPORT} />}
            <ExportCsvButton onExport={() => exportLicences(filters)} disabled={!licences.data?.totalElements} />
            {canEdit && (
              <Button onClick={() => setAdding(true)}>
                <Plus aria-hidden />
                New licence
              </Button>
            )}
          </>
        }
      />
      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-40 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
          <SearchInput placeholder="Search name, vendor or payment method" aria-label="Search licences" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
          <Select aria-label="Status" value={status} onChange={(e) => filter(setStatus)(e.target.value as LicenceStatus | '')}>
            <option value="">All statuses</option>
            {LICENCE_STATUSES.map((s) => (
              <option key={s} value={s}>
                {LICENCE_STATUS_META[s].label}
              </option>
            ))}
          </Select>
          <Select aria-label="Billing" value={billingCycle} onChange={(e) => filter(setBillingCycle)(e.target.value as BillingCycle | '')}>
            <option value="">Any billing</option>
            {BILLING_CYCLES.map((b) => (
              <option key={b} value={b}>
                {BILLING_LABELS[b]}
              </option>
            ))}
          </Select>
          <Select aria-label="Department" value={departmentId} onChange={(e) => filter(setDepartmentId)(e.target.value)}>
            <option value="">All departments</option>
            {departments.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </Select>
          <Select aria-label="Renewal" value={renewalWithinDays} onChange={(e) => filter(setRenewalWithinDays)(e.target.value)}>
            <option value="">Any renewal date</option>
            {RENEWAL_WINDOWS.map((w) => (
              <option key={w.value} value={w.value}>
                {w.label}
              </option>
            ))}
          </Select>
          <Select aria-label="Sort licences" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>

        {licences.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading licences">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : licences.isError ? (
          <FinanceErrorState error={licences.error} title="Couldn't load licences" onRetry={() => void licences.refetch()} />
        ) : licences.data.content.length === 0 ? (
          <EmptyState
            icon={AppWindow}
            title={filtered ? 'No licences match these filters' : 'No tools or licences yet'}
            description={!filtered && canEdit ? 'Add the software you pay for, or import a CSV, to track seats, costs and renewals.' : undefined}
          />
        ) : (
          <>
            <LicencesTable licences={licences.data.content} onOpen={setOpenId} dimmed={licences.isPlaceholderData} />
            <Pagination {...licences.data} onPageChange={setPage} />
          </>
        )}
      </Card>
      <LicenceSheet licenceId={openId} onClose={() => setOpenId(null)} />
      {canEdit && <LicenceFormDialog open={adding} onOpenChange={setAdding} onSaved={setOpenId} />}
    </div>
  );
}

function LicencesTable({ licences, onOpen, dimmed }: { licences: Licence[]; onOpen: (id: number) => void; dimmed?: boolean }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Tool or licence</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Seats</TableHead>
          <TableHead className="text-right">Cost</TableHead>
          <TableHead className="text-right">Per year (AUD)</TableHead>
          <TableHead>Renewal</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody className={cn(dimmed && 'opacity-60')}>
        {licences.map((l) => (
          <TableRow key={l.id}>
            <TableCell className="max-w-xs">
              <button type="button" className="block max-w-full text-left" onClick={() => onOpen(l.id)}>
                <span className="block truncate font-medium hover:underline">{l.name}</span>
              </button>
              <span className="block truncate text-xs text-muted-foreground">{[l.vendor?.name, l.department?.name].filter(Boolean).join(' · ') || 'No vendor or department'}</span>
            </TableCell>
            <TableCell>
              <LicenceStatusBadge status={l.status} />
            </TableCell>
            <TableCell>
              <SeatUsage licence={l} />
            </TableCell>
            <TableCell className="text-right text-sm whitespace-nowrap tabular-nums">{costLabel(l)}</TableCell>
            <TableCell className="text-right text-sm whitespace-nowrap tabular-nums">
              {l.billingCycle === 'ONE_OFF' ? (
                <span className="text-muted-foreground">One-off</span>
              ) : l.annualCostAud !== null ? (
                formatAud(l.annualCostAud)
              ) : l.annualCost !== null ? (
                <span className="text-status-warning" title={`No exchange rate for ${l.currency} yet`}>
                  No {l.currency} rate
                </span>
              ) : (
                <span className="text-muted-foreground">—</span>
              )}
            </TableCell>
            <TableCell className="text-sm whitespace-nowrap">
              {l.renewalDate ? formatLocalDate(l.renewalDate) : <span className="text-muted-foreground">—</span>}
              <span className="mt-1 flex">
                <LicenceRenewalBadge licence={l} />
              </span>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
