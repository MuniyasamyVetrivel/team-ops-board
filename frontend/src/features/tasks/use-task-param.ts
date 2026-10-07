import { useCallback } from 'react';
import { useSearchParams } from 'react-router';

/** The open task lives in the URL (?task=42), so drawers survive reloads and can be shared as links. */
export function useTaskParam(): [number | null, (id: number | null) => void] {
  const [params, setParams] = useSearchParams();
  const raw = params.get('task');
  const id = raw && /^\d+$/.test(raw) ? Number(raw) : null;
  const setId = useCallback(
    (next: number | null) => {
      setParams(
        (current) => {
          const updated = new URLSearchParams(current);
          if (next === null) updated.delete('task');
          else updated.set('task', String(next));
          return updated;
        },
        { replace: next === null },
      );
    },
    [setParams],
  );
  return [id, setId];
}
