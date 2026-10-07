import { useCallback } from 'react';
import { useSearchParams } from 'react-router';

/**
 * A numeric id kept in the URL (e.g. ?task=42), so drawers survive reloads and can be shared as links. Closing
 * (null) replaces the history entry instead of adding one.
 */
export function useIdParam(name: string): [number | null, (id: number | null) => void] {
  const [params, setParams] = useSearchParams();
  const raw = params.get(name);
  const id = raw && /^\d+$/.test(raw) ? Number(raw) : null;
  const setId = useCallback(
    (next: number | null) => {
      setParams(
        (current) => {
          const updated = new URLSearchParams(current);
          if (next === null) updated.delete(name);
          else updated.set(name, String(next));
          return updated;
        },
        { replace: next === null },
      );
    },
    [name, setParams],
  );
  return [id, setId];
}
