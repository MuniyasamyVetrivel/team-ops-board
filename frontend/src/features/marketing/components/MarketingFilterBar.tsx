import { RotateCcw } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';

import { MONTH_NAMES } from '../marketing-format';
import { useMarketingFilters } from '../use-marketing-filters';

/**
 * Month, year and owner for every marketing page. The default is the current business month from the server; the
 * selection is kept in the URL and carried across marketing pages.
 */
export function MarketingFilterBar({ showOwner = true }: { showOwner?: boolean }) {
  const { context, filters, setFilters, isCurrentPeriod } = useMarketingFilters();

  if (context.isError) return null;
  if (!filters || !context.data) {
    return <Skeleton className="h-[62px] w-full max-w-2xl rounded-xl" aria-label="Loading filters" />;
  }

  const { currentPeriod, owners } = context.data;
  const years = context.data.years.includes(filters.year) ? context.data.years : [...context.data.years, filters.year].sort((a, b) => b - a);
  const filtered = !isCurrentPeriod || filters.ownerId !== null;

  return (
    <div role="group" aria-label="Marketing filters" className="flex flex-wrap items-end gap-3 rounded-xl border bg-card p-3 shadow-xs">
      <div className="grid gap-1">
        <label htmlFor="marketing-month" className="text-xs font-medium text-muted-foreground">
          Month
        </label>
        <Select id="marketing-month" className="w-36" value={filters.month} onChange={(event) => setFilters({ ...filters, month: Number(event.target.value) })}>
          {MONTH_NAMES.map((name, index) => (
            <option key={name} value={index + 1}>
              {name}
            </option>
          ))}
        </Select>
      </div>
      <div className="grid gap-1">
        <label htmlFor="marketing-year" className="text-xs font-medium text-muted-foreground">
          Year
        </label>
        <Select id="marketing-year" className="w-28" value={filters.year} onChange={(event) => setFilters({ ...filters, year: Number(event.target.value) })}>
          {years.map((year) => (
            <option key={year} value={year}>
              {year}
            </option>
          ))}
        </Select>
      </div>
      {showOwner && (
        <div className="grid gap-1">
          <label htmlFor="marketing-owner" className="text-xs font-medium text-muted-foreground">
            Owner
          </label>
          <Select
            id="marketing-owner"
            className="w-52"
            value={filters.ownerId ?? ''}
            onChange={(event) => setFilters({ ...filters, ownerId: event.target.value ? Number(event.target.value) : null })}
          >
            <option value="">All owners</option>
            {owners.map((owner) => (
              <option key={owner.id} value={owner.id}>
                {owner.fullName}
              </option>
            ))}
          </Select>
        </div>
      )}
      {filtered && (
        <Button variant="ghost" size="sm" className="mb-1" onClick={() => setFilters({ month: currentPeriod.month, year: currentPeriod.year, ownerId: null })}>
          <RotateCcw aria-hidden />
          Reset to {currentPeriod.label}
        </Button>
      )}
    </div>
  );
}
