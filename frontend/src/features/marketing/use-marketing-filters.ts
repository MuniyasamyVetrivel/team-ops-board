import { useCallback, useContext, useMemo } from 'react';
import { useSearchParams } from 'react-router';

import { useMarketingContext } from './api';
import { FilterMemoryContext, type MarketingFilters } from './filter-memory';

function parseNumber(raw: string | null, min: number, max: number): number | undefined {
  if (raw === null || !/^\d+$/.test(raw)) return undefined;
  const value = Number(raw);
  return value >= min && value <= max ? value : undefined;
}

/**
 * Filters from the URL (?month=10&year=2026&owner=7), falling back to the remembered ones and then to the current
 * business month from the server. {@code filters} is null until the server context has loaded.
 */
export function useMarketingFilters() {
  const context = useMarketingContext();
  const memory = useContext(FilterMemoryContext);
  const [params, setParams] = useSearchParams();
  const current = context.data?.currentPeriod;
  const remembered = memory?.remembered;

  const month = parseNumber(params.get('month'), 1, 12);
  const year = parseNumber(params.get('year'), 2000, 2100);
  const owner = params.get('owner');
  const filters = useMemo<MarketingFilters | null>(() => {
    if (!current) return null;
    return {
      month: month ?? remembered?.month ?? current.month,
      year: year ?? remembered?.year ?? current.year,
      ownerId: owner !== null ? (parseNumber(owner, 1, Number.MAX_SAFE_INTEGER) ?? null) : (remembered?.ownerId ?? null),
    };
  }, [current, month, year, owner, remembered]);

  const remember = memory?.remember;
  const setFilters = useCallback(
    (next: MarketingFilters) => {
      remember?.(next);
      setParams(
        (existing) => {
          const updated = new URLSearchParams(existing);
          updated.set('month', String(next.month));
          updated.set('year', String(next.year));
          if (next.ownerId === null) updated.delete('owner');
          else updated.set('owner', String(next.ownerId));
          return updated;
        },
        { replace: true },
      );
    },
    [remember, setParams],
  );

  const isCurrentPeriod = filters !== null && current !== undefined && filters.month === current.month && filters.year === current.year;
  return { context, filters, setFilters, isCurrentPeriod };
}
