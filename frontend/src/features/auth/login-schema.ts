import { z } from 'zod';

export const loginSchema = z.object({
  email: z.string().trim().min(1, 'Email is required').pipe(z.email('Enter a valid email address')),
  password: z.string().min(1, 'Password is required').max(128, 'Password is too long'),
});

export type LoginFormValues = z.infer<typeof loginSchema>;
