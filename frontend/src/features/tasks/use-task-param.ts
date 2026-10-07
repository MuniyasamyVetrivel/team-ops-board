import { useIdParam } from '@/lib/use-id-param';

/** The open task lives in the URL (?task=42). */
export function useTaskParam(): [number | null, (id: number | null) => void] {
  return useIdParam('task');
}
