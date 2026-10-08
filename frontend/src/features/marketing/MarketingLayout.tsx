import { useMemo, useState } from 'react';
import { Outlet } from 'react-router';

import { FilterMemoryContext, type FilterMemory, type MarketingFilters } from './filter-memory';

/**
 * Wraps every /digital-marketing route. It remembers the last filters, so moving between marketing pages through the
 * sidebar (links without a query string) keeps the selected month, year and owner.
 */
export function MarketingLayout() {
  const [remembered, setRemembered] = useState<Partial<MarketingFilters>>({});
  const memory = useMemo<FilterMemory>(() => ({ remembered, remember: setRemembered }), [remembered]);
  return (
    <FilterMemoryContext.Provider value={memory}>
      <Outlet />
    </FilterMemoryContext.Provider>
  );
}
