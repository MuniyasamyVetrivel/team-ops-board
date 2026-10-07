import { z } from 'zod';

/** Mirrors com.teamops.common.security.PasswordPolicy. */
export const passwordSchema = z
  .string()
  .min(8, 'At least 8 characters')
  .max(128, 'At most 128 characters')
  .regex(/[A-Za-z]/, 'Include at least one letter')
  .regex(/\d/, 'Include at least one number');

const optionalText = (max: number) => z.string().trim().max(max, `At most ${max} characters`);

/** Mirrors UpdateUserRequest. Select values arrive as strings, so ids are coerced. */
export const profileSchema = z.object({
  email: z.string().trim().min(1, 'Email is required').pipe(z.email('Enter a valid email address')),
  firstName: z.string().trim().min(1, 'First name is required').max(100),
  lastName: optionalText(100),
  jobTitle: optionalText(150),
  phone: optionalText(40),
  location: optionalText(150),
  workingHours: optionalText(100),
  departmentId: z.string().min(1, 'Department is required'),
  reportsToId: z.string(),
  weeklyCapacityHours: z.coerce
    .number({ error: 'Enter a number of hours' })
    .min(1, 'At least 1 hour')
    .max(80, 'At most 80 hours'),
});

export const createUserSchema = profileSchema.extend({
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

/** Converts form values (strings) to the API payload. */
export function toProfilePayload(values: ProfileFormValues) {
  return {
    email: values.email,
    firstName: values.firstName,
    lastName: values.lastName,
    jobTitle: values.jobTitle,
    phone: values.phone,
    location: values.location,
    workingHours: values.workingHours,
    departmentId: Number(values.departmentId),
    reportsToId: values.reportsToId ? Number(values.reportsToId) : null,
    weeklyCapacityHours: values.weeklyCapacityHours,
  };
}
