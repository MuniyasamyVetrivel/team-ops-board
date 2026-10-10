import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { api } from '@/lib/api/client';

export type ResultType =
  | 'TASK'
  | 'TICKET'
  | 'PROJECT'
  | 'EMPLOYEE'
  | 'ARTICLE'
  | 'MARKETING_PAGE'
  | 'KEYWORD'
  | 'EMAIL_CAMPAIGN'
  | 'PAID_CAMPAIGN'
  | 'LEAD';

export type SearchGroupKey =
  | 'TASKS'
  | 'TICKETS'
  | 'PROJECTS'
  | 'EMPLOYEES'
  | 'KNOWLEDGE_BASE'
  | 'MARKETING_PAGES'
  | 'KEYWORDS'
  | 'CAMPAIGNS'
  | 'LEADS';

/** Mirrors SearchDtos.Hit. {@code ref} is an article's slug; {@code status} is the record's status code. */
export interface SearchHit {
  type: ResultType;
  id: number;
  code: string | null;
  title: string;
  subtitle: string | null;
  status: string | null;
  ref: string | null;
}

export interface SearchGroup {
  group: SearchGroupKey;
  label: string;
  total: number;
  hits: SearchHit[];
}

/** Mirrors SearchDtos.SearchResults: only the groups the viewer may search, and only those with matches. */
export interface SearchResults {
  query: string;
  groups: SearchGroup[];
}

/** The server ignores shorter queries. */
export const MIN_QUERY_LENGTH = 2;

export const searchKeys = {
  all: ['global-search'] as const,
  query: (q: string) => [...searchKeys.all, q] as const,
};

export function useGlobalSearch(query: string) {
  const q = query.trim();
  return useQuery({
    queryKey: searchKeys.query(q),
    queryFn: async ({ signal }) => (await api.get<SearchResults>('/search', { params: { q }, signal })).data,
    enabled: q.length >= MIN_QUERY_LENGTH,
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  });
}

/** Where each result opens: the record's drawer or page. */
export function hitHref(hit: SearchHit): string {
  switch (hit.type) {
    case 'TASK':
      return `/tasks?task=${hit.id}`;
    case 'TICKET':
      return `/tickets?ticket=${hit.id}`;
    case 'PROJECT':
      return `/projects/${hit.id}`;
    case 'EMPLOYEE':
      return `/team/${hit.id}`;
    case 'ARTICLE':
      return `/knowledge-base/${encodeURIComponent(hit.ref ?? String(hit.id))}`;
    case 'MARKETING_PAGE':
      return `/digital-marketing/seo/pages/${hit.id}`;
    case 'KEYWORD':
      return `/digital-marketing/seo?tab=keywords&keyword=${hit.id}`;
    case 'EMAIL_CAMPAIGN':
      return `/digital-marketing/email-campaigns?search=${encodeURIComponent(hit.title)}`;
    case 'PAID_CAMPAIGN':
      return `/digital-marketing/paid-campaigns?campaign=${hit.id}`;
    case 'LEAD':
      return `/digital-marketing/leads?lead=${hit.id}`;
  }
}

/** Where "see all" goes for a group: the module's list page with the same search where it supports one. */
export function groupHref(group: SearchGroupKey, query: string): string {
  const q = encodeURIComponent(query.trim());
  switch (group) {
    case 'TASKS':
      return `/tasks?search=${q}`;
    case 'TICKETS':
      return `/tickets?search=${q}`;
    case 'PROJECTS':
      return `/projects?search=${q}`;
    case 'EMPLOYEES':
      return `/team?search=${q}`;
    case 'KNOWLEDGE_BASE':
      return `/knowledge-base?search=${q}`;
    case 'MARKETING_PAGES':
      return `/digital-marketing/seo?tab=pages`;
    case 'KEYWORDS':
      return `/digital-marketing/seo?tab=keywords`;
    case 'CAMPAIGNS':
      return `/digital-marketing/email-campaigns?search=${q}`;
    case 'LEADS':
      return `/digital-marketing/leads`;
  }
}

/** "IN_PROGRESS" → "In progress". */
export function statusLabel(code: string): string {
  const words = code.replace(/_/g, ' ').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}
