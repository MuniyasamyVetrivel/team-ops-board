import { describe, expect, it } from 'vitest';

import { addSkill, allocationSchema, certificationSchema, consultantSchema, outsideEngagement } from './bench-schemas';

const allocation = {
  consultantId: '7',
  engagementId: '5',
  role: 'Test lead',
  startDate: '2026-10-01',
  endDate: '2026-12-31',
  allocationPercent: '50',
  billable: true,
  notes: '',
};

describe('allocationSchema', () => {
  it('accepts 1 to 100 percent', () => {
    expect(allocationSchema.safeParse(allocation).success).toBe(true);
    expect(allocationSchema.safeParse({ ...allocation, allocationPercent: '100' }).success).toBe(true);
    expect(allocationSchema.safeParse({ ...allocation, allocationPercent: '0' }).success).toBe(false);
    expect(allocationSchema.safeParse({ ...allocation, allocationPercent: '101' }).success).toBe(false);
    expect(allocationSchema.safeParse({ ...allocation, allocationPercent: '50.5' }).success).toBe(false);
  });

  it('needs the end on or after the start, or no end', () => {
    expect(allocationSchema.safeParse({ ...allocation, endDate: '2026-10-01' }).success).toBe(true);
    expect(allocationSchema.safeParse({ ...allocation, endDate: '' }).success).toBe(true);
    const result = allocationSchema.safeParse({ ...allocation, endDate: '2026-09-30' });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.path).toEqual(['endDate']);
  });
});

describe('outsideEngagement', () => {
  const engagement = { startDate: '2026-10-01', endDate: '2026-12-31' };

  it('accepts an allocation inside the engagement', () => {
    expect(outsideEngagement({ startDate: '2026-10-01', endDate: '2026-12-31' }, engagement)).toBeNull();
  });

  it('blames the start or the end, as the server rejects it', () => {
    expect(outsideEngagement({ startDate: '2026-09-30', endDate: '2026-11-01' }, engagement)?.field).toBe('startDate');
    expect(outsideEngagement({ startDate: '2026-10-01', endDate: '2027-01-01' }, engagement)?.field).toBe('endDate');
    expect(outsideEngagement({ startDate: '2026-10-01', endDate: '' }, engagement)?.field).toBe('endDate');
  });

  it('lets an open-ended engagement take open-ended allocations', () => {
    expect(outsideEngagement({ startDate: '2026-10-01', endDate: '' }, { startDate: '2026-10-01', endDate: null })).toBeNull();
  });
});

describe('consultant fields', () => {
  it('adds skills once, ignoring case and blanks', () => {
    expect(addSkill(['Tosca'], ' tosca ')).toEqual(['Tosca']);
    expect(addSkill(['Tosca'], '   ')).toEqual(['Tosca']);
    expect(addSkill(['Tosca'], ' SAP S/4HANA ')).toEqual(['Tosca', 'SAP S/4HANA']);
  });

  it('takes experience from 0 to 60 years with one decimal', () => {
    const base = { employeeId: '40', location: '', availableFrom: '', employmentStatus: 'ACTIVE' as const, skills: [], certifications: [], notes: '' };
    expect(consultantSchema.safeParse({ ...base, experienceYears: '6.5' }).success).toBe(true);
    expect(consultantSchema.safeParse({ ...base, experienceYears: '' }).success).toBe(true);
    expect(consultantSchema.safeParse({ ...base, experienceYears: '61' }).success).toBe(false);
    expect(consultantSchema.safeParse({ ...base, experienceYears: '6.55' }).success).toBe(false);
  });

  it('needs a certification to expire on or after it was issued', () => {
    const cert = { id: null, name: 'ISTQB', issuer: '', issuedOn: '2026-01-01', expiresOn: '2025-12-31', credentialId: '' };
    expect(certificationSchema.safeParse(cert).success).toBe(false);
    expect(certificationSchema.safeParse({ ...cert, expiresOn: '' }).success).toBe(true);
  });
});
