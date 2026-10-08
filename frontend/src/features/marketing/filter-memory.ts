import { createContext } from 'react';

/** The global Digital Marketing filters (brief section 51). Every marketing page reads them. */
export interface MarketingFilters {
  month: number;
  year: number;
  /** null means every owner. */
  ownerId: number | null;
}

export interface FilterMemory {
  remembered: Partial<MarketingFilters>;
  remember: (filters: MarketingFilters) => void;
}

/** Provided by MarketingLayout; null outside it (e.g. a page rendered on its own in a test). */
export const FilterMemoryContext = createContext<FilterMemory | null>(null);
