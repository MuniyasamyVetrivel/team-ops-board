import { useSearchParams } from 'react-router';

/** The `?search=` a page was opened with (e.g. "see all" from global search), as its search box's starting value. */
export function useInitialSearch(): string {
  const [params] = useSearchParams();
  return params.get('search') ?? '';
}
