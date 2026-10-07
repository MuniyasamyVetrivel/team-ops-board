import { describe, expect, it } from 'vitest';

import { createUserSchema, passwordSchema, profileSchema, resetPasswordSchema, toProfilePayload } from './schemas';

const validProfile = {
  email: '  Asha.Iyer@teamops.local ',
  firstName: 'Asha',
  lastName: '',
  jobTitle: '',
  phone: '',
  location: '',
  workingHours: '',
  departmentId: '5',
  reportsToId: '',
  weeklyCapacityHours: '32',
};

describe('passwordSchema (mirrors backend PasswordPolicy)', () => {
  it.each(['Welcome1', 'correct horse 9'])('accepts %s', (password) => {
    expect(passwordSchema.safeParse(password).success).toBe(true);
  });

  it.each(['Short1', 'onlyletters', '12345678'])('rejects %s', (password) => {
    expect(passwordSchema.safeParse(password).success).toBe(false);
  });
});

describe('profileSchema', () => {
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
    expect(createUserSchema.safeParse({ ...validProfile, password: 'Welcome1', role: 'EMPLOYEE' }).success).toBe(true);
  });
});

describe('resetPasswordSchema', () => {
  it('requires the confirmation to match', () => {
    const result = resetPasswordSchema.safeParse({ newPassword: 'Welcome1', confirm: 'Welcome2' });
    expect(result.error?.issues[0]?.path).toEqual(['confirm']);
  });
});

describe('toProfilePayload', () => {
  it('converts select values to ids and an empty manager to null', () => {
    const payload = toProfilePayload(profileSchema.parse(validProfile));
    expect(payload.departmentId).toBe(5);
    expect(payload.reportsToId).toBeNull();
  });
});
