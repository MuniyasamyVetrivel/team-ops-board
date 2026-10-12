import { Paperclip, Plus, Repeat, Sparkles } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useIdParam } from '@/lib/use-id-param';
import { useInitialSearch } from '@/lib/use-initial-search';
import { cn } from '@/lib/utils';

import { DAY_CATEGORIES, exportImportantDays, IMPORTANT_DAY_IMPORT, REGIONS, useImportantDays, type DayRecurrence, type ImportantDayCategory, type ImportantDayQuery, type Region } from './api';
import { LinkedInBadge } from './CelebrationBadges';
import { DAY_CATEGORY_LABELS, formatOccasionDate, RECURRENCE_LABELS, REGION_LABELS } from './celebration-meta';
import { ExportCsvButton, ImportCsvButton } from './CsvButtons';
import { ImportantDayFormDialog } from './ImportantDayFormDialog';
import { ImportantDaySheet } from './ImportantDaySheet';

const SORTS = [
  { value: 'date,asc', label: 'Date' },
  { value: 'name,asc', label: 'Name' },
  { value: 'updated,desc', label: 'Recently updated' },
];

/** Important days for marketing: festivals, national and industry days, company milestones, by region. */
export default function ImportantDaysPage() {
  const { user } = useAuth();
  const canEdit = hasPermission(user, 'CELEBRATIONS_EDIT');
  const [openId, setOpenId] = useIdParam('day');
  const [adding, setAdding] = useState(false);
  const [search, setSearch] = useState(useInitialSearch());
  const [category, setCategory] = useState<ImportantDayCategory | ''>('');
  const [region, setRegion] = useState<Region | ''>('');
  const [recurrence, setRecurrence] = useState<DayRecurrence | ''>('');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(search.trim());

  const filters: Omit<ImportantDayQuery, 'page' | 'size' | 'sort'> = {
    search: debounced,
    category: category ? [category] : undefined,
    region: region ? [region] : undefined,
    recurrence: recurrence || undefined,
  };
  const days = useImportantDays({ ...filters, sort, page, size: 25 });
  const filtered = Boolean(debounced || category || region || recurrence);

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Important days"
        description="Festivals, national and industry days and company milestones for marketing, with their LinkedIn posts."
        actions={
          <>
            {canEdit && <ImportCsvButton type={IMPORTANT_DAY_IMPORT} />}
            <ExportCsvButton onExport={() => exportImportantDays(filters)} disabled={!days.data?.totalElements} />
            {canEdit && (
              <Button onClick={() => setAdding(true)}>
                <Plus aria-hidden />
                New important day
              </Button>
            )}
          </>
        }
      />
      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-40 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
          <SearchInput placeholder="Search important days" aria-label="Search important days" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
          <Select aria-label="Category" value={category} onChange={(e) => filter(setCategory)(e.target.value as ImportantDayCategory | '')}>
            <option value="">All categories</option>
            {DAY_CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {DAY_CATEGORY_LABELS[c]}
              </option>
            ))}
          </Select>
          <Select aria-label="Region" value={region} onChange={(e) => filter(setRegion)(e.target.value as Region | '')}>
            <option value="">All regions</option>
            {REGIONS.map((r) => (
              <option key={r} value={r}>
                {REGION_LABELS[r]}
              </option>
            ))}
          </Select>
          <Select aria-label="Repeat" value={recurrence} onChange={(e) => filter(setRecurrence)(e.target.value as DayRecurrence | '')}>
            <option value="">Yearly and one-off</option>
            <option value="YEARLY">{RECURRENCE_LABELS.YEARLY}</option>
            <option value="ONE_OFF">{RECURRENCE_LABELS.ONE_OFF}</option>
          </Select>
          <Select aria-label="Sort important days" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>

        {days.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading important days">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : days.isError ? (
          <ErrorState error={days.error} title="Couldn't load important days" onRetry={() => void days.refetch()} />
        ) : days.data.content.length === 0 ? (
          <EmptyState
            icon={Sparkles}
            title={filtered ? 'No important days match these filters' : 'No important days yet'}
            description={canEdit ? 'Add festivals and national days, or import next year’s calendar as CSV.' : undefined}
          />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Important day</TableHead>
                  <TableHead>Next on</TableHead>
                  <TableHead>Region</TableHead>
                  <TableHead>LinkedIn post</TableHead>
                  <TableHead numeric>
                    <span className="sr-only">Creatives</span>
                    <Paperclip className="ml-auto size-4" aria-hidden />
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={cn(days.isPlaceholderData && 'opacity-60')}>
                {days.data.content.map((d) => (
                  <TableRow key={d.id}>
                    <TableCell className="max-w-sm">
                      <button type="button" className="block max-w-full truncate text-left font-medium hover:underline" onClick={() => setOpenId(d.id)}>
                        {d.name}
                      </button>
                      <p className="flex items-center gap-1 truncate text-xs text-muted-foreground">
                        {DAY_CATEGORY_LABELS[d.category]} ·
                        {d.recurrence === 'YEARLY' && <Repeat className="size-3" aria-hidden />}
                        {RECURRENCE_LABELS[d.recurrence]}
                      </p>
                    </TableCell>
                    <TableCell className="text-sm whitespace-nowrap">{formatOccasionDate(d.nextOccurrence)}</TableCell>
                    <TableCell className="text-sm">{REGION_LABELS[d.region]}</TableCell>
                    <TableCell>
                      <LinkedInBadge post={d.linkedIn} />
                    </TableCell>
                    <TableCell numeric className="text-sm text-muted-foreground">
                      {d.attachmentCount > 0 ? <span aria-label={`${d.attachmentCount} ${d.attachmentCount === 1 ? 'creative' : 'creatives'}`}>{d.attachmentCount}</span> : <span aria-label="No creatives">—</span>}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination {...days.data} onPageChange={setPage} />
          </>
        )}
      </Card>
      <ImportantDaySheet dayId={openId} onClose={() => setOpenId(null)} />
      <ImportantDayFormDialog open={adding} onOpenChange={setAdding} onSaved={setOpenId} />
    </div>
  );
}
