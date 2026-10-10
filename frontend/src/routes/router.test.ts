import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';

import { NAV_ITEMS } from '@/config/navigation';

import { routes } from './router';

function paths(list: RouteObject[]): string[] {
  return list.flatMap((route) => [...(route.path ? [route.path] : []), ...paths(route.children ?? [])]);
}

describe('routes', () => {
  it('registers a real page for every sidebar entry', () => {
    // Building the routes throws when an entry has no page, so importing them is already half the check.
    expect(paths(routes)).toEqual(expect.arrayContaining(NAV_ITEMS.map((item) => item.path)));
  });
});
