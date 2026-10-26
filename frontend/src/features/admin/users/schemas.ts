import { z } from 'zod';

import { passwordSchema } from '@/features/auth/password-schema';

export { passwordSchema };

/** Weekly capacity of a new user, and of an existing one with none saved. */
export const DEFAULT_WEEKLY_CAPACITY_HOURS = 40;

/** The saved weekly capacity as a number for the edit form, or the default when none is saved. */
export function capacityOrDefault(saved: number | string | null | undefined): number {
  const hours = Number(saved);
  return saved === null || saved === undefined || saved === '' || !Number.isFinite(hours) ? DEFAULT_WEEKLY_CAPACITY_HOURS : hours;
}

const optionalText = (max: number) => z.string().trim().max(max, `At most ${max} characters`);

/** Blank, or a zone the browser knows (the backend checks against the Java zone list). */
function isTimeZone(value: string): boolean {
  if (!value) return true;
  if (value !== 'UTC' && !value.includes('/')) return false;
  try {
    new Intl.DateTimeFormat('en', { timeZone: value });
    return true;
  } catch {
    return false;
  }
}

/** Mirrors EmployeeIds on the server: a letter or digit, then letters, digits, . _ - or /; up to 40 characters. */
export const EMPLOYEE_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._/-]{0,39}$/;
export const EMPLOYEE_ID_HINT = 'Letters, digits, . _ - or /, e.g. EMP170_IN';
const EMPLOYEE_ID_MESSAGE = 'Use letters, digits, . _ - or / (starting with a letter or digit), up to 40 characters';

/** Mirrors UpdateUserRequest. Select values arrive as strings, so ids are coerced. */
export const profileSchema = z.object({
  /** Blank keeps it (users from before employee IDs); only a Super Admin can change it. */
  employeeId: z
    .string()
    .trim()
    .refine((value) => value === '' || EMPLOYEE_ID_PATTERN.test(value), EMPLOYEE_ID_MESSAGE),
  email: z.string().trim().min(1, 'Email is required').pipe(z.email('Enter a valid email address')),
  firstName: z.string().trim().min(1, 'First name is required').max(100),
  lastName: optionalText(100),
  jobTitle: optionalText(150),
  phone: optionalText(40),
  location: optionalText(150),
  workingHours: optionalText(100),
  timeZone: optionalText(64).refine(isTimeZone, 'Use a time zone such as Asia/Kolkata'),
  departmentId: z.string().min(1, 'Department is required'),
  reportsToId: z.string(),
  weeklyCapacityHours: z.coerce
    .number({ error: 'Enter a number of hours' })
    .min(1, 'At least 1 hour')
    .max(80, 'At most 80 hours'),
});

export const createUserSchema = profileSchema.extend({
  employeeId: z.string().trim().min(1, 'Employee ID is required').regex(EMPLOYEE_ID_PATTERN, EMPLOYEE_ID_MESSAGE),
  password: passwordSchema,
  role: z.enum(['SUPER_ADMIN', 'DEPARTMENT_MANAGER', 'EMPLOYEE'], { error: 'Choose a role' }),
});

export const resetPasswordSchema = z
  .object({ newPassword: passwordSchema, confirm: z.string() })
  .refine((values) => values.newPassword === values.confirm, { path: ['confirm'], message: 'Passwords do not match' });

export type ProfileFormInput = z.input<typeof profileSchema>;
export type ProfileFormValues = z.output<typeof profileSchema>;
export type CreateUserFormInput = z.input<typeof createUserSchema>;
export type CreateUserFormValues = z.output<typeof createUserSchema>;
export type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;

/**
 * Converts form values (strings) to the API payload. {@code employeeId} is sent only when the viewer may set it;
 * null keeps the current one.
 */
export function toProfilePayload(values: ProfileFormValues, options: { includeEmployeeId: boolean }) {
  return {
    employeeId: options.includeEmployeeId && values.employeeId ? values.employeeId : null,
    email: values.email,
    firstName: values.firstName,
    lastName: values.lastName,
    jobTitle: values.jobTitle,
    phone: values.phone,
    location: values.location,
    workingHours: values.workingHours,
    timeZone: values.timeZone,
    departmentId: Number(values.departmentId),
    reportsToId: values.reportsToId ? Number(values.reportsToId) : null,
    weeklyCapacityHours: values.weeklyCapacityHours,
  };
}
