import type { Device, KeywordStatus, PageStatus, PageType, SearchEngine } from './api';

export const PAGE_TYPE_LABELS: Record<PageType, string> = {
  SERVICE: 'Service',
  INDUSTRY: 'Industry',
  LOCATION: 'Location',
  BLOG: 'Blog',
  LANDING_PAGE: 'Landing page',
  PRODUCT: 'Product',
  OTHER: 'Other',
};

export const PAGE_STATUS_LABELS: Record<PageStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  ARCHIVED: 'Archived',
};

export const KEYWORD_STATUS_LABELS: Record<KeywordStatus, string> = {
  ACTIVE: 'Tracking',
  PAUSED: 'Paused',
  ARCHIVED: 'Archived',
};

export const SEARCH_ENGINE_LABELS: Record<SearchEngine, string> = {
  GOOGLE: 'Google',
  BING: 'Bing',
};

export const DEVICE_LABELS: Record<Device, string> = {
  DESKTOP: 'Desktop',
  MOBILE: 'Mobile',
};

/** Mirrors SeoDtos.URL_PATTERN: a site path or a full http(s) URL, without spaces. */
export const PAGE_URL_PATTERN = /^(\/|https?:\/\/)\S*$/;
