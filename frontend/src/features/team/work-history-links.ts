import type { WorkHistoryParams } from './use-work-history-params';

/** A link to a person's work history, already filtered (e.g. from the Workload page). */
export function historyLink(userId: number, filters: Partial<Pick<WorkHistoryParams, 'status' | 'dateField' | 'range' | 'from' | 'to'>>): string {
  const search = new URLSearchParams();
  if (filters.status && filters.status !== 'ALL') search.set('status', filters.status);
  if (filters.range) {
    search.set('range', filters.range);
    if (filters.dateField) search.set('dateField', filters.dateField);
    if (filters.range === 'CUSTOM') {
      if (filters.from) search.set('from', filters.from);
      if (filters.to) search.set('to', filters.to);
    }
  }
  const query = search.toString();
  return `/team/${userId}${query ? `?${query}` : ''}#work-history`;
}
