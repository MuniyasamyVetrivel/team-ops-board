import { describe, expect, it } from 'vitest';

import { createUserSchema, EMPLOYEE_ID_PATTERN, passwordSchema, profileSchema, resetPasswordSchema, toProfilePayload } from './schemas';

const validProfile = {
  employeeId: 'E-1042',
  email: '  Asha.Iyer@teamops.local ',
  firstName: 'Asha',
  lastName: '',
  jobTitle: '',
  phone: '',
  location: '',
  workingHours: '',
  timeZone: '',
  departmentId: '5',
  reportsToId: '',
  weeklyCapacityHours: '32',
};

describe('passwordSchema (mirrors backend PasswordPolicy)', () => {
  it.each(['Welcome123', 'correct horse 9'])('accepts %s', (password) => {
    expect(passwordSchema.safeParse(password).success).toBe(true);
  });

  it.each(['Short1', 'Welcome12', 'onlyletters', '1234567890'])('rejects %s', (password) => {
    expect(passwordSchema.safeParse(password).success).toBe(false);
  });
});

describe('profileSchema', () => {
  it('accepts a blank or IANA time zone only', () => {
    expect(profileSchema.safeParse({ ...validProfile, timeZone: 'Australia/Sydney' }).success).toBe(true);
    expect(profileSchema.safeParse({ ...validProfile, timeZone: 'Mars/Base' }).success).toBe(false);
    expect(profileSchema.safeParse({ ...validProfile, timeZone: 'IST' }).success).toBe(false);
  });

  it('trims the email and coerces capacity to a number', () => {
    const parsed = profileSchema.parse(validProfile);
    expect(parsed.email).toBe('Asha.Iyer@teamops.local');
    expect(parsed.weeklyCapacityHours).toBe(32);
  });

  it('requires a department and keeps capacity within 1-80 hours', () => {
    const result = profileSchema.safeParse({ ...validProfile, departmentId: '', weeklyCapacityHours: '90' });
    expect(result.success).toBe(false);
    const fields = result.error?.issues.map((issue) => issue.path[0]);
    expect(fields).toEqual(expect.arrayContaining(['departmentId', 'weeklyCapacityHours']));
  });
});

describe('createUserSchema', () => {
  it('requires a strong password and a role', () => {
    expect(createUserSchema.safeParse({ ...validProfile, password: 'weak', role: 'EMPLOYEE' }).success).toBe(false);
    expect(createUserSchema.safeParse({ ...validProfile, password: 'Welcome123', role: 'EMPLOYEE' }).success).toBe(true);
  });
});

describe('resetPasswordSchema', () => {
  it('requires the confirmation to match', () => {
    const result = resetPasswordSchema.safeParse({ newPassword: 'Welcome123', confirm: 'Welcome124' });
    expect(result.error?.issues[0]?.path).toEqual(['confirm']);
  });
});

describe('toProfilePayload', () => {
  it('converts select values to ids and an empty manager to null', () => {
    const payload = toProfilePayload(profileSchema.parse(validProfile), { includeEmployeeId: true });
    expect(payload.departmentId).toBe(5);
    expect(payload.reportsToId).toBeNull();
  });

  it('sends the employee ID only when the viewer may set it, and null to keep it', () => {
    const values = profileSchema.parse({ ...validProfile, employeeId: ' EMP170_IN ' });
    expect(toProfilePayload(values, { includeEmployeeId: true }).employeeId).toBe('EMP170_IN');
    expect(toProfilePayload(values, { includeEmployeeId: false }).employeeId).toBeNull();
    expect(toProfilePayload(profileSchema.parse({ ...validProfile, employeeId: '' }), { includeEmployeeId: true }).employeeId).toBeNull();
  });
});

describe('employee IDs', () => {
  it('accept letters, digits, underscores, dots, dashes and slashes, like the server', () => {
    for (const id of ['EMP170_IN', 'EMP100_AU', 'E-1042', 'TRI/2026/07', 'a.b']) {
      expect(EMPLOYEE_ID_PATTERN.test(id)).toBe(true);
    }
    for (const id of ['_E1', 'E 1', 'E1<x>', 'E'.repeat(41)]) {
      expect(EMPLOYEE_ID_PATTERN.test(id)).toBe(false);
    }
  });

  it('are required when creating a user', () => {
    const result = createUserSchema.safeParse({ ...validProfile, employeeId: '', password: 'Welcome2026', role: 'EMPLOYEE' });
    expect(result.success).toBe(false);
    expect(result.error?.issues.map((i) => i.path[0])).toContain('employeeId');
  });
});
