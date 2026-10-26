import { describe, expect, it } from 'vitest';

import { NAV_ITEMS, NAVIGATION, visibleNavigation } from '@/config/navigation';
import { makeUser, seoExecutive, superAdmin, webEmployee } from '@/test/fixtures';

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

  it('shows Backups only to the Super Admin and anyone granted BACKUP_VIEW', () => {
    expect(labels(superAdmin)).toContain('Backups');
    expect(labels(webEmployee)).not.toContain('Backups');
    expect(labels(makeUser(['DEPARTMENT_MANAGER'], [...webEmployee.permissions, 'DATA_EXPORT']))).not.toContain('Backups');
    expect(labels(makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'BACKUP_VIEW']))).toContain('Backups');
    expect(NAV_ITEMS.find((item) => item.path === '/admin/backups')?.requires).toEqual(['BACKUP_VIEW']);
  });

  it('shows a Digital Marketing employee only the marketing pages they hold permissions for', () => {
    const visible = labels(seoExecutive);
    expect(visible).toEqual(
      expect.arrayContaining(['Marketing Dashboard', 'SEO Rankings', 'Marketing Targets', 'Backlinks', 'Content & Blog']),
    );
    expect(visible).not.toContain('Leads');
    expect(visible).not.toContain('Email Campaigns');
  });

  it('puts celebrations in Digital Marketing, below Monthly Report', () => {
    const marketing = NAVIGATION.find((section) => section.id === 'digital-marketing')?.items.map((item) => item.label) ?? [];
    expect(marketing.slice(marketing.indexOf('Monthly Report'))).toEqual(['Monthly Report', 'Employees & celebrations', 'Important days', 'Events', 'LinkedIn posts']);
    expect(NAVIGATION.find((section) => section.id === 'collaboration')?.items.map((item) => item.label)).not.toContain('Events');
    expect(NAV_ITEMS.find((item) => item.path === '/celebrations/employees')?.requires).toEqual(['MARKETING_VIEW']);
  });

  it('gives a Digital Marketing team member the whole marketing area, celebrations included', () => {
    const modules = ['MARKETING', 'SEO', 'CAMPAIGN', 'TARGET', 'LEAD', 'BACKLINK', 'CONTENT'] as const;
    const dmMember = makeUser(['EMPLOYEE'], [...webEmployee.permissions, ...modules.flatMap((m) => [`${m}_VIEW`, `${m}_EDIT`] as const), 'CELEBRATIONS_EDIT']);
    const marketing = NAVIGATION.find((section) => section.id === 'digital-marketing')?.items.map((item) => item.label);
    expect(visibleNavigation(dmMember).find((section) => section.id === 'digital-marketing')?.items.map((item) => item.label)).toEqual(marketing);
  });

  it('hides celebrations from HR and IT employees', () => {
    const hrAdmin = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'USER_MANAGE']);
    for (const viewer of [webEmployee, hrAdmin]) {
      for (const label of ['Employees & celebrations', 'Important days', 'Events', 'LinkedIn posts']) {
        expect(labels(viewer)).not.toContain(label);
      }
    }
  });

  it('shows Audit Logs to the Super Admin only, not to employees or managers by default', () => {
    expect(labels(superAdmin)).toContain('Audit Logs');
    expect(labels(webEmployee)).not.toContain('Audit Logs');
    const manager = makeUser(['DEPARTMENT_MANAGER'], [...webEmployee.permissions, 'WORKLOAD_VIEW', 'TASK_ASSIGN', 'TASK_DELETE']);
    expect(labels(manager)).not.toContain('Audit Logs');
    expect(labels(makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'AUDIT_VIEW']))).toContain('Audit Logs');
  });

  it('never returns empty sections', () => {
    for (const section of visibleNavigation(webEmployee)) {
      expect(section.items.length).toBeGreaterThan(0);
    }
  });

  it('makes every marketing route require MARKETING_VIEW as well as its own permission', () => {
    const seo = NAV_ITEMS.find((item) => item.path === '/digital-marketing/seo');
    expect(seo?.requires).toEqual(['MARKETING_VIEW', 'SEO_VIEW']);
    expect(NAV_ITEMS.find((item) => item.path === '/digital-marketing')?.requires).toEqual(['MARKETING_VIEW']);
    expect(NAV_ITEMS.find((item) => item.path === '/tasks')?.requires).toEqual(['TASK_VIEW']);
  });
});
