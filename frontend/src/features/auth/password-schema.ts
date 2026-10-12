import { z } from 'zod';

/** Mirrors com.teamops.common.security.PasswordPolicy: at least 10 characters, with letters and numbers. */
export const PASSWORD_MIN_LENGTH = 10;

export const passwordSchema = z
  .string()
  .min(PASSWORD_MIN_LENGTH, `At least ${PASSWORD_MIN_LENGTH} characters`)
  .max(128, 'At most 128 characters')
  .regex(/[A-Za-z]/, 'Include at least one letter')
  .regex(/\d/, 'Include at least one number');

/** Mirrors ChangePasswordRequest; the new password must differ from the current one, as the backend checks. */
export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password').max(128, 'At most 128 characters'),
    newPassword: passwordSchema,
    confirm: z.string(),
  })
  .refine((values) => values.newPassword === values.confirm, { path: ['confirm'], message: 'Passwords do not match' })
  .refine((values) => values.newPassword !== values.currentPassword, {
    path: ['newPassword'],
    message: 'Choose a password different from the current one',
  });

export type ChangePasswordFormValues = z.infer<typeof changePasswordSchema>;

export const PASSWORD_HINT = `${PASSWORD_MIN_LENGTH}+ characters with letters and numbers`;
