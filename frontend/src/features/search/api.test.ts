import { describe, expect, it } from 'vitest';

import { groupHref, hitHref, type SearchHit } from './api';

const hit = (type: SearchHit['type'], id: number): SearchHit => ({ type, id, code: null, title: 'Acme', subtitle: null, status: null, ref: null });

describe('search links', () => {
  it('opens Bench Management records on their pages', () => {
    expect(hitHref(hit('CONSULTANT', 7))).toBe('/tools/bench/consultants/7');
    expect(hitHref(hit('CLIENT', 3))).toBe('/tools/bench/clients?client=3');
    expect(hitHref(hit('CLIENT_ENGAGEMENT', 5))).toBe('/tools/bench/engagements?engagement=5');
  });

  it('sends "see all" to the matching Bench Management list with the search', () => {
    expect(groupHref('CONSULTANTS', ' sap s/4 ')).toBe('/tools/bench/consultants?search=sap%20s%2F4');
    expect(groupHref('CLIENTS', 'acme')).toBe('/tools/bench/clients?search=acme');
    expect(groupHref('ENGAGEMENTS', 'acme')).toBe('/tools/bench/engagements?search=acme');
  });
});
