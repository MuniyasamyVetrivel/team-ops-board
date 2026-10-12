import { describe, expect, it } from 'vitest';

import { dayMonth, isHttpLink, occasionHref, relativeDay, yearsLabel } from './celebration-meta';
import { employeeSchema, eventSchema } from './celebration-schemas';

describe('celebration-meta', () => {
  it('shows birthdays without the year, including 29 February', () => {
    expect(dayMonth(10, 14)).toMatch(/14/);
    expect(dayMonth(2, 29)).toMatch(/29/);
  });

  it('counts years and days', () => {
    expect(yearsLabel(1)).toBe('1 year');
    expect(yearsLabel(5)).toBe('5 years');
    expect(relativeDay('2026-10-12', '2026-10-12')).toBe('Today');
    expect(relativeDay('2026-10-13', '2026-10-12')).toBe('Tomorrow');
    expect(relativeDay('2026-10-26', '2026-10-12')).toBe('In 14 days');
  });

  it('accepts only full http(s) links, as the server does', () => {
    expect(isHttpLink('https://www.linkedin.com/posts/tritusa')).toBe(true);
    expect(isHttpLink('javascript:alert(1)')).toBe(false);
    expect(isHttpLink('/blog/post')).toBe(false);
  });

  it('links each occasion to the page that manages it', () => {
    expect(occasionHref({ subjectType: 'BIRTHDAY', subjectId: 40 })).toBe('/celebrations/employees?employee=40');
    expect(occasionHref({ subjectType: 'IMPORTANT_DAY', subjectId: 4 })).toBe('/celebrations/important-days?day=4');
    expect(occasionHref({ subjectType: 'EVENT', subjectId: 9 })).toBe('/celebrations/events?event=9');
  });
});

describe('celebration-schemas', () => {
  const employee = {
    employeeCode: 'TRI-1',
    firstName: '',
    lastName: '',
    dateOfBirth: '1996-02-29',
    designation: '',
    reportingManager: '',
    dateOfJoining: '',
    departmentId: '',
    workEmail: '',
    officeLocation: '',
    userId: '',
    status: 'ACTIVE' as const,
  };

  it('needs names, designation and manager unless an app user fills them', () => {
    const unlinked = employeeSchema('2026-10-12').safeParse(employee);
    expect(unlinked.success).toBe(false);
    expect(unlinked.error?.issues.map((i) => i.path[0])).toEqual(['firstName', 'lastName', 'designation', 'reportingManager']);
    expect(employeeSchema('2026-10-12').safeParse({ ...employee, userId: '12' }).success).toBe(true);
  });

  it('rejects a future birth date and joining before birth', () => {
    const named = { ...employee, firstName: 'A', lastName: 'B', designation: 'QA', reportingManager: 'Rakesh' };
    expect(employeeSchema('2026-10-12').safeParse({ ...named, dateOfBirth: '2026-10-13' }).error?.issues[0]?.path).toEqual(['dateOfBirth']);
    expect(employeeSchema(null).safeParse({ ...named, dateOfJoining: '1990-01-01' }).error?.issues[0]?.path).toEqual(['dateOfJoining']);
  });

  it('checks event links and the end time', () => {
    const event = {
      title: 'Webinar',
      eventType: 'WEBINAR' as const,
      status: 'PLANNED' as const,
      startAt: '2026-10-18T15:00',
      endAt: '2026-10-18T14:00',
      location: '',
      onlineLink: 'ftp://example.com',
      registrationLink: '',
      description: '',
      ownerId: '11',
      emailCampaignId: '',
    };
    expect(eventSchema.safeParse(event).error?.issues.map((i) => i.path[0])).toEqual(['onlineLink', 'endAt']);
  });
});
