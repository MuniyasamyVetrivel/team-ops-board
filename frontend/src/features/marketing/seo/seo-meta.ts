import type { RankingMovement } from '../api';
import type { Device, KeywordStatus, PageStatus, PageType, RankingSource, SearchEngine, StandingFilter } from './api';

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

export const RANKING_SOURCE_LABELS: Record<RankingSource, string> = {
  MANUAL: 'Manual',
  CSV: 'CSV import',
  SEMRUSH: 'Semrush',
  GSC: 'Search Console',
};

export const STANDING_FILTER_LABELS: Record<StandingFilter, string> = {
  TOP_10: 'Top 10',
  RANKING: 'Ranking (11–100)',
  NOT_RANKED: 'Not ranked',
  NOT_RECORDED: 'No data this month',
};

export const MOVEMENT_LABELS: Record<RankingMovement, string> = {
  IMPROVED: 'Improved',
  DECLINED: 'Declined',
  UNCHANGED: 'No change',
  NEW: 'New this month',
};

/** Position bands of the monthly report (brief section 29), also offered as table filters. */
export const POSITION_BANDS = [
  { value: '1-3', label: 'Top 3', min: 1, max: 3 },
  { value: '1-10', label: 'Top 10', min: 1, max: 10 },
  { value: '11-20', label: '11–20', min: 11, max: 20 },
  { value: '21-50', label: '21–50', min: 21, max: 50 },
  { value: '51-100', label: '51–100', min: 51, max: 100 },
] as const;

/** Categorical chart tokens from index.css; never the status colours, so a line is not read as a ranking status. */
export const SERIES_COLORS = ['var(--chart-1)', 'var(--chart-2)', 'var(--chart-3)', 'var(--chart-4)', 'var(--chart-5)', 'var(--chart-6)'];

/** More lines than this stop being readable; the chart's caller says how many were left out. */
export const MAX_SERIES = SERIES_COLORS.length;
