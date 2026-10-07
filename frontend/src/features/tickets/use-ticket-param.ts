import { useIdParam } from '@/lib/use-id-param';

/** The open ticket lives in the URL (?ticket=42). */
export function useTicketParam(): [number | null, (id: number | null) => void] {
  return useIdParam('ticket');
}
