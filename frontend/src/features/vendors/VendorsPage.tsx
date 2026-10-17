import { Plus, Store } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ExportCsvButton } from '@/features/celebrations/CsvButtons';
import { useTool } from '@/features/modules/tool-context';
import { ToolSection } from '@/features/modules/ToolSection';
import { formatLocalDate } from '@/lib/format';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useInitialSearch } from '@/lib/use-initial-search';
import { cn } from '@/lib/utils';

import { exportVendors, useVendors, VENDOR_CATEGORIES, VENDOR_IMPORT, VENDOR_STATUSES, type VendorCategory, type VendorQuery, type VendorStatus } from './api';
import { Rating, VendorErrorState, VendorStatusBadge } from './VendorBadges';
import { VendorFormDialog } from './VendorFormDialog';
import { VendorImportButton } from './VendorImportButton';
import { CATEGORY_LABELS, VENDOR_STATUS_META } from './vendor-meta';
import { vendorPath } from './vendor-paths';

const SORTS = [
  { value: 'name,asc', label: 'Name' },
  { value: 'rating,desc', label: 'Highest rated' },
  { value: 'category,asc', label: 'Category' },
  { value: 'country,asc', label: 'Country' },
  { value: 'updated,desc', label: 'Recently updated' },
];

/** Vendors: the companies the business buys from. VENDOR_EDIT holders add, import and edit; everyone here can export. */
export default function VendorsPage() {
  const { canEdit } = useTool();
  const navigate = useNavigate();
  const [adding, setAdding] = useState(false);
  const [search, setSearch] = useState(useInitialSearch());
  const [category, setCategory] = useState<VendorCategory | ''>('');
  const [status, setStatus] = useState<VendorStatus | ''>('');
  const [country, setCountry] = useState('');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebouncedValue(search.trim());
  const debouncedCountry = useDebouncedValue(country.trim());

  const filters: Omit<VendorQuery, 'page' | 'size' | 'sort'> = { search: debouncedSearch, category: category || undefined, status: status || undefined, country: debouncedCountry };
  const vendors = useVendors({ ...filters, sort, page, size: 25 });
  const filtered = Boolean(debouncedSearch || category || status || debouncedCountry);

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <div className="space-y-4">
      <ToolSection
        title="Vendors"
        description="Who you buy from, their contacts and how many contracts each has."
        actions={
          <>
            {canEdit && <VendorImportButton type={VENDOR_IMPORT} />}
            <ExportCsvButton onExport={() => exportVendors(filters)} disabled={!vendors.data?.totalElements} />
            {canEdit && (
              <Button onClick={() => setAdding(true)}>
                <Plus aria-hidden />
                New vendor
              </Button>
            )}
          </>
        }
      />
      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-40 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
          <SearchInput placeholder="Search name, tax ID, country or contact" aria-label="Search vendors" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
          <Select aria-label="Category" value={category} onChange={(e) => filter(setCategory)(e.target.value as VendorCategory | '')}>
            <option value="">All categories</option>
            {VENDOR_CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {CATEGORY_LABELS[c]}
              </option>
            ))}
          </Select>
          <Select aria-label="Status" value={status} onChange={(e) => filter(setStatus)(e.target.value as VendorStatus | '')}>
            <option value="">All statuses</option>
            {VENDOR_STATUSES.map((s) => (
              <option key={s} value={s}>
                {VENDOR_STATUS_META[s].label}
              </option>
            ))}
          </Select>
          <Input placeholder="Country" aria-label="Country" value={country} onChange={(e) => filter(setCountry)(e.target.value)} />
          <Select aria-label="Sort vendors" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>

        {vendors.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading vendors">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : vendors.isError ? (
          <VendorErrorState error={vendors.error} title="Couldn't load vendors" onRetry={() => void vendors.refetch()} />
        ) : vendors.data.content.length === 0 ? (
          <EmptyState
            icon={Store}
            title={filtered ? 'No vendors match these filters' : 'No vendors yet'}
            description={!filtered && canEdit ? 'Add vendors one by one, or import a CSV from your finance system.' : undefined}
          />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Vendor</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Country</TableHead>
                  <TableHead>Rating</TableHead>
                  <TableHead className="text-right">Contracts</TableHead>
                  <TableHead>Next renewal</TableHead>
                  <TableHead>Main contact</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={cn(vendors.isPlaceholderData && 'opacity-60')}>
                {vendors.data.content.map((v) => {
                  const main = v.contacts[0];
                  return (
                    <TableRow key={v.id}>
                      <TableCell className="max-w-xs">
                        <Link to={vendorPath(v.id)} className="block truncate font-medium hover:underline">
                          {v.name}
                        </Link>
                        {v.taxId && <span className="block truncate text-xs text-muted-foreground">{v.taxId}</span>}
                      </TableCell>
                      <TableCell className="text-sm">{CATEGORY_LABELS[v.category]}</TableCell>
                      <TableCell>
                        <VendorStatusBadge status={v.status} />
                      </TableCell>
                      <TableCell className="text-sm">{v.country ?? <span className="text-muted-foreground">—</span>}</TableCell>
                      <TableCell>
                        <Rating rating={v.rating} />
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{v.contractCount.toLocaleString()}</TableCell>
                      <TableCell className="text-sm whitespace-nowrap">{v.nextRenewal ? formatLocalDate(v.nextRenewal) : <span className="text-muted-foreground">—</span>}</TableCell>
                      <TableCell className="max-w-48 text-sm">
                        {main ? (
                          <>
                            <span className="block truncate">{main.name}</span>
                            {main.email && <span className="block truncate text-xs text-muted-foreground">{main.email}</span>}
                          </>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
            <Pagination {...vendors.data} onPageChange={setPage} />
          </>
        )}
      </Card>
      {canEdit && <VendorFormDialog open={adding} onOpenChange={setAdding} onSaved={(id) => void navigate(vendorPath(id))} />}
    </div>
  );
}
