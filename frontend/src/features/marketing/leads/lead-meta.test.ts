import { describe, expect, it } from 'vitest';

import { LEAD_SOURCES } from '../targets/api';
import { linkFields, linkFits, linkKindFor, TREND_SERIES } from './lead-meta';

describe('linkKindFor', () => {
  it('mirrors LeadRules: email, LinkedIn/paid and blog leads take a link; the rest none', () => {
    expect(linkKindFor('EMAIL')).toBe('EMAIL_CAMPAIGN');
    expect(linkKindFor('LINKEDIN')).toBe('PAID_CAMPAIGN');
    expect(linkKindFor('PAID_CAMPAIGN')).toBe('PAID_CAMPAIGN');
    expect(linkKindFor('BLOG')).toBe('CONTENT');
    for (const source of ['ORGANIC', 'WEBSITE', 'REFERRAL', 'OTHER'] as const) expect(linkKindFor(source)).toBeNull();
  });
});

describe('linkFits', () => {
  it('only offers LinkedIn campaigns to LinkedIn leads', () => {
    expect(linkFits('LINKEDIN', { kind: 'PAID_CAMPAIGN', detail: 'LINKEDIN' })).toBe(true);
    expect(linkFits('LINKEDIN', { kind: 'PAID_CAMPAIGN', detail: 'GOOGLE_ADS' })).toBe(false);
    expect(linkFits('PAID_CAMPAIGN', { kind: 'PAID_CAMPAIGN', detail: 'GOOGLE_ADS' })).toBe(true);
  });

  it('never offers a link of another kind', () => {
    expect(linkFits('EMAIL', { kind: 'CONTENT', detail: 'BLOG' })).toBe(false);
    expect(linkFits('ORGANIC', { kind: 'EMAIL_CAMPAIGN', detail: null })).toBe(false);
  });
});

describe('linkFields', () => {
  it('fills only the field of the link kind', () => {
    expect(linkFields('CONTENT', 4)).toEqual({ emailCampaignId: null, paidCampaignId: null, contentItemId: 4 });
    expect(linkFields(null, null)).toEqual({ emailCampaignId: null, paidCampaignId: null, contentItemId: null });
  });
});

describe('TREND_SERIES', () => {
  it('covers every source exactly once', () => {
    expect(TREND_SERIES.flatMap((s) => s.sources).sort()).toEqual([...LEAD_SOURCES].sort());
  });
});
