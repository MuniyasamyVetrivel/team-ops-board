import { FileSignature, Plus } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { canSeeFinance } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { ExportCsvButton } from '@/features/celebrations/CsvButtons';
import { useTool } from '@/features/modules/tool-context';
import { ToolSection } from '@/features/modules/ToolSection';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useIdParam } from '@/lib/use-id-param';
import { useInitialSearch } from '@/lib/use-initial-search';

import { CONTRACT_IMPORT, CONTRACT_STATUSES, exportContracts, useContracts, useVendors, type ContractQuery, type ContractStatus } from './api';
import { ContractFormDialog } from './ContractFormDialog';
import { ContractSheet } from './ContractSheet';
import { ContractsTable } from './ContractsTable';
import { VendorErrorState } from './VendorBadges';
import { VendorImportButton } from './VendorImportButton';
import { CONTRACT_STATUS_META } from './vendor-meta';

const SORTS = [
  { value: 'start,desc', label: 'Newest start' },
  { value: 'end,asc', label: 'Ending soonest' },
  { value: 'title,asc', label: 'Title' },
  { value: 'vendor,asc', label: 'Vendor' },
  { value: 'updated,desc', label: 'Recently updated' },
];

/** Sorting by value would give the values away, so the server refuses it without FINANCE_VIEW. */
const VALUE_SORT = { value: 'value,desc', label: 'Highest value' };

/** Every vendor contract. Opens one from ?contract=12 (renewal reminders, search). */
export default function ContractsPage() {
  const { canEdit } = useTool();
  const { user } = useAuth();
  const sorts = canSeeFinance(user) ? [...SORTS, VALUE_SORT] : SORTS;
  const [openId, setOpenId] = useIdParam('contract');
  const [adding, setAdding] = useState(false);
  const [search, setSearch] = useState(useInitialSearch());
  const [vendorId, setVendorId] = useState('');
  const [status, setStatus] = useState<ContractStatus | ''>('');
  const [autoRenew, setAutoRenew] = useState<'' | 'true' | 'false'>('');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(search.trim());
  const vendors = useVendors({ size: 100, sort: 'name,asc' });

  const filters: Omit<ContractQuery, 'page' | 'size' | 'sort'> = {
    search: debounced,
    vendorId: vendorId ? Number(vendorId) : undefined,
    status: status || undefined,
    autoRenew: autoRenew === '' ? undefined : autoRenew === 'true',
  };
  const contracts = useContracts({ ...filters, sort, page, size: 25 });
  const filtered = Boolean(debounced || vendorId || status || autoRenew);

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <div className="space-y-4">
      <ToolSection
        title="Contracts"
        description="Every vendor contract with its dates, renewal terms and documents."
        actions={
          <>
            {canEdit && <VendorImportButton type={CONTRACT_IMPORT} />}
            <ExportCsvButton onExport={() => exportContracts(filters)} disabled={!contracts.data?.totalElements} />
            {canEdit && (
              <Button onClick={() => setAdding(true)}>
                <Plus aria-hidden />
                New contract
              </Button>
            )}
          </>
        }
      />
      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-40 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
          <SearchInput placeholder="Search title or vendor" aria-label="Search contracts" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
          <Select aria-label="Vendor" value={vendorId} onChange={(e) => filter(setVendorId)(e.target.value)}>
            <option value="">All vendors</option>
            {vendors.data?.content.map((v) => (
              <option key={v.id} value={v.id}>
                {v.name}
              </option>
            ))}
          </Select>
          <Select aria-label="Status" value={status} onChange={(e) => filter(setStatus)(e.target.value as ContractStatus | '')}>
            <option value="">All statuses</option>
            {CONTRACT_STATUSES.map((s) => (
              <option key={s} value={s}>
                {CONTRACT_STATUS_META[s].label}
              </option>
            ))}
          </Select>
          <Select aria-label="Auto-renew" value={autoRenew} onChange={(e) => filter(setAutoRenew)(e.target.value as '' | 'true' | 'false')}>
            <option value="">Auto-renewing or not</option>
            <option value="true">Renews automatically</option>
            <option value="false">Needs a decision</option>
          </Select>
          <Select aria-label="Sort contracts" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
            {sorts.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>

        {contracts.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading contracts">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : contracts.isError ? (
          <VendorErrorState error={contracts.error} title="Couldn't load contracts" onRetry={() => void contracts.refetch()} />
        ) : contracts.data.content.length === 0 ? (
          <EmptyState
            icon={FileSignature}
            title={filtered ? 'No contracts match these filters' : 'No contracts yet'}
            description={!filtered && canEdit ? 'Add a vendor first, then record its contracts and upload the documents.' : undefined}
          />
        ) : (
          <>
            <ContractsTable contracts={contracts.data.content} onOpen={setOpenId} dimmed={contracts.isPlaceholderData} />
            <Pagination {...contracts.data} onPageChange={setPage} />
          </>
        )}
      </Card>
      <ContractSheet contractId={openId} onClose={() => setOpenId(null)} />
      {canEdit && <ContractFormDialog open={adding} onOpenChange={setAdding} vendorId={vendorId ? Number(vendorId) : undefined} onSaved={setOpenId} />}
    </div>
  );
}
