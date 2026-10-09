import { describe, expect, it } from 'vitest';

import { contentDateProblem, isLive, type ContentDates } from './content-meta';

const TODAY = '2026-10-09';
const values = (overrides: Partial<ContentDates>): ContentDates => ({ status: 'IDEA', url: '', publicationDate: '', refreshedDate: '', ...overrides });

describe('contentDateProblem', () => {
  it('accepts each stage with the dates it needs', () => {
    expect(contentDateProblem(values({}), TODAY)).toBeNull();
    expect(contentDateProblem(values({ status: 'PUBLISHED', url: '/blog/sap', publicationDate: '2026-10-02' }), TODAY)).toBeNull();
    expect(contentDateProblem(values({ status: 'UPDATED', url: '/blog/sap', publicationDate: '2026-10-02', refreshedDate: '2026-10-06' }), TODAY)).toBeNull();
  });

  it('needs the URL and publication date once live, and neither before', () => {
    expect(contentDateProblem(values({ status: 'PUBLISHED', url: '/blog/sap' }), TODAY)?.field).toBe('publicationDate');
    expect(contentDateProblem(values({ status: 'PUBLISHED', publicationDate: '2026-10-02' }), TODAY)?.field).toBe('url');
    expect(contentDateProblem(values({ status: 'DRAFT', publicationDate: '2026-10-02' }), TODAY)?.field).toBe('publicationDate');
    expect(contentDateProblem(values({ status: 'UPDATED', url: '/x', publicationDate: '2026-10-02' }), TODAY)?.field).toBe('refreshedDate');
    expect(contentDateProblem(values({ status: 'PUBLISHED', url: '/x', publicationDate: '2026-10-02', refreshedDate: '2026-10-06' }), TODAY)?.field).toBe('refreshedDate');
  });

  it('keeps dates out of the future and in order', () => {
    expect(contentDateProblem(values({ status: 'PUBLISHED', url: '/x', publicationDate: '2026-10-10' }), TODAY)?.message).toMatch(/future/);
    expect(contentDateProblem(values({ status: 'UPDATED', url: '/x', publicationDate: '2026-10-06', refreshedDate: '2026-10-02' }), TODAY)?.message).toMatch(/before the publication/);
  });
});

describe('isLive', () => {
  it('is true for published and updated content only', () => {
    expect(isLive('PUBLISHED')).toBe(true);
    expect(isLive('UPDATED')).toBe(true);
    expect(isLive('DRAFT')).toBe(false);
  });
});
