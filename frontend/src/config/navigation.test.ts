import { describe, expect, it } from 'vitest';

import { NAVIGATION, visibleNavigation } from '@/config/navigation';
import { seoExecutive, superAdmin, webEmployee } from '@/test/fixtures';

function labels(viewer: Parameters<typeof visibleNavigation>[0]) {
  return visibleNavigation(viewer).flatMap((section) => section.items.map((item) => item.label));
}

function sectionIds(viewer: Parameters<typeof visibleNavigation>[0]) {
  return visibleNavigation(viewer).map((section) => section.id);
}

describe('visibleNavigation', () => {
  it('shows every section and item to the Super Admin', () => {
    const total = NAVIGATION.reduce((sum, section) => sum + section.items.length, 0);
    expect(labels(superAdmin)).toHaveLength(total);
    expect(sectionIds(superAdmin)).toContain('digital-marketing');
    expect(sectionIds(superAdmin)).toContain('administration');
  });

  it('hides Digital Marketing and Administration from a non-marketing employee', () => {
    const ids = sectionIds(webEmployee);
    expect(ids).not.toContain('digital-marketing');
    expect(ids).not.toContain('administration');
    expect(labels(webEmployee)).not.toContain('Workload');
    expect(labels(webEmployee)).toEqual(expect.arrayContaining(['Dashboard', 'My Tasks', 'Tickets', 'Team']));
  });

  it('shows a Digital Marketing employee only the marketing pages they hold permissions for', () => {
    const visible = labels(seoExecutive);
    expect(visible).toEqual(
      expect.arrayContaining(['Marketing Dashboard', 'SEO Rankings', 'Marketing Targets', 'Backlinks', 'Content & Blog']),
    );
    expect(visible).not.toContain('Leads');
    expect(visible).not.toContain('Email Campaigns');
  });

  it('never returns empty sections', () => {
    for (const section of visibleNavigation(webEmployee)) {
      expect(section.items.length).toBeGreaterThan(0);
    }
  });
});
