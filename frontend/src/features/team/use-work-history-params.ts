import { useCallback } from 'react';
import { useSearchParams } from 'react-router';

import type { WorkDateField, WorkDatePreset, WorkKind, WorkQuery } from './work-history-api';

/** "ALL", a status of the kind, or "OVERDUE". */
export type WorkStatusTab = string;

/** Any time, a preset, or a custom from/to. */
export type WorkRange = '' | WorkDatePreset | 'CUSTOM';

export interface WorkHistoryParams {
  kind: WorkKind;
  status: WorkStatusTab;
  dateField: WorkDateField;
  range: WorkRange;
  from: string;
  to: string;
  /** Project (tasks) or category (tickets) id. */
  group: string;
  priority: string;
  search: string;
  sort: string;
  page: number;
}

const DATE_FIELDS: WorkDateField[] = ['COMPLETED', 'DUE', 'START', 'CREATED'];
const PRESETS: WorkDatePreset[] = ['THIS_WEEK', 'LAST_WEEK', 'THIS_MONTH', 'LAST_MONTH', 'LAST_3_MONTHS', 'LAST_30_DAYS'];
export const DEFAULT_SORT = 'updated,desc';

/** URL keys: the profile page also uses ?task= and ?ticket= for its drawers. */
const KEYS: Record<Exclude<keyof WorkHistoryParams, 'page'>, string> = {
  kind: 'work',
  status: 'status',
  dateField: 'dateField',
  range: 'range',
  from: 'from',
  to: 'to',
  group: 'group',
  priority: 'priority',
  search: 'q',
  sort: 'sort',
};

export function readWorkParams(search: URLSearchParams): WorkHistoryParams {
  const dateField = search.get(KEYS.dateField) as WorkDateField | null;
  const range = search.get(KEYS.range) ?? '';
  const page = Number(search.get('page'));
  return {
    kind: search.get(KEYS.kind) === 'tickets' ? 'tickets' : 'tasks',
    status: (search.get(KEYS.status) ?? 'ALL').toUpperCase(),
    dateField: dateField && DATE_FIELDS.includes(dateField) ? dateField : 'COMPLETED',
    range: range === 'CUSTOM' || PRESETS.includes(range as WorkDatePreset) ? (range as WorkRange) : '',
    from: search.get(KEYS.from) ?? '',
    to: search.get(KEYS.to) ?? '',
    group: search.get(KEYS.group) ?? '',
    priority: search.get(KEYS.priority) ?? '',
    search: search.get(KEYS.search) ?? '',
    sort: search.get(KEYS.sort) ?? DEFAULT_SORT,
    page: Number.isInteger(page) && page > 0 ? page : 0,
  };
}

/** The API filters, without the status (for the tab counts and summary) or with it (for the list). */
export function toWorkQuery(p: WorkHistoryParams, withStatus: boolean): WorkQuery {
  const custom = p.range === 'CUSTOM';
  return {
    status: withStatus && p.status !== 'ALL' ? [p.status] : undefined,
    dateField: p.range ? p.dateField : undefined,
    preset: p.range && p.range !== 'CUSTOM' ? p.range : undefined,
    from: custom && p.from ? p.from : undefined,
    to: custom && p.to ? p.to : undefined,
    groupId: p.group ? Number(p.group) : undefined,
    priority: p.priority ? [p.priority] : undefined,
    search: p.search.trim() || undefined,
  };
}

/**
 * Work history filters kept in the URL, so a filtered view can be linked to (e.g. from the Workload page) and
 * survives a reload. Changing a filter goes back to the first page; switching between tasks and tickets clears the
 * filters that differ between them.
 */
export function useWorkHistoryParams(): [WorkHistoryParams, (patch: Partial<WorkHistoryParams>) => void] {
  const [search, setSearch] = useSearchParams();
  const params = readWorkParams(search);
  const update = useCallback(
    (patch: Partial<WorkHistoryParams>) => {
      setSearch(
        (current) => {
          const next = new URLSearchParams(current);
          const switching = patch.kind !== undefined && patch.kind !== readWorkParams(current).kind;
          if (switching) {
            ['status', 'group', 'sort'].forEach((key) => next.delete(KEYS[key as keyof typeof KEYS]));
          }
          for (const [key, value] of Object.entries(patch)) {
            if (key === 'page') continue;
            const name = KEYS[key as keyof typeof KEYS];
            const empty = value === '' || value === undefined || (key === 'status' && value === 'ALL') || (key === 'kind' && value === 'tasks') || (key === 'sort' && value === DEFAULT_SORT);
            if (empty) next.delete(name);
            else next.set(name, String(value));
          }
          if (patch.page) next.set('page', String(patch.page));
          else next.delete('page');
          return next;
        },
        { replace: true },
      );
    },
    [setSearch],
  );
  return [params, update];
}
