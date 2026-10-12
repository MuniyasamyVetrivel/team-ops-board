import { zodResolver } from '@hookform/resolvers/zod';
import { CircleCheck, Circle, KeyRound, LoaderCircle, LogOut } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { useLocation, useNavigate, type Location } from 'react-router';
import { toast } from 'sonner';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { BrandMark } from '@/layouts/BrandMark';
import { errorMessage, toApiError } from '@/lib/api/errors';
import { cn } from '@/lib/utils';

import { changePasswordSchema, PASSWORD_MIN_LENGTH, type ChangePasswordFormValues } from './password-schema';
import { useAuth } from './use-auth';

/** Server error codes that belong to one field rather than the whole form. */
const FIELD_ERRORS: Record<string, keyof ChangePasswordFormValues> = {
  WRONG_PASSWORD: 'currentPassword',
  WEAK_PASSWORD: 'newPassword',
  PASSWORD_UNCHANGED: 'newPassword',
};

function returnTarget(location: Location): string {
  const from = (location.state as { from?: Location } | null)?.from;
  return from && from.pathname !== '/change-password' && from.pathname !== '/login' ? `${from.pathname}${from.search}` : '/dashboard';
}

/**
 * Choosing your own password. Signed-in users whose password was set by an administrator (new accounts, imports,
 * resets) land here first and cannot use the rest of the app until they finish; the backend enforces the same.
 */
export default function ChangePasswordPage() {
  const { user, changePassword, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [banner, setBanner] = useState<string | null>(null);
  const forced = user?.mustChangePassword ?? false;

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordFormValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirm: '' },
  });
  const newPassword = useWatch({ control, name: 'newPassword' });

  const onSubmit = handleSubmit(async ({ currentPassword, newPassword: chosen }) => {
    setBanner(null);
    try {
      await changePassword({ currentPassword, newPassword: chosen });
      toast.success('Password changed. You have been signed out of other devices.');
      navigate(returnTarget(location), { replace: true });
    } catch (error) {
      const apiError = toApiError(error);
      const field = apiError ? FIELD_ERRORS[apiError.code] : undefined;
      if (field && apiError) setError(field, { message: apiError.message }, { shouldFocus: true });
      else setBanner(errorMessage(error));
    }
  });

  const rules = [
    { met: newPassword.length >= PASSWORD_MIN_LENGTH, label: `At least ${PASSWORD_MIN_LENGTH} characters` },
    { met: /[A-Za-z]/.test(newPassword), label: 'A letter' },
    { met: /\d/.test(newPassword), label: 'A number' },
  ];

  return (
    <div className="flex min-h-svh items-center justify-center bg-muted/30 px-4 py-12">
      <div className="w-full max-w-md rounded-xl border bg-card p-6 shadow-sm sm:p-8">
        <BrandMark className="mb-8" />
        <div className="flex items-center gap-2">
          <KeyRound className="size-5 text-primary" aria-hidden />
          <h1 className="text-page-title font-bold tracking-tight">{forced ? 'Choose your password' : 'Change password'}</h1>
        </div>
        <p className="mt-1.5 text-sm text-muted-foreground">
          {forced
            ? `Welcome${user ? `, ${user.firstName}` : ''}. Your administrator gave you a temporary password. Choose your own to continue.`
            : 'Enter your current password, then choose a new one.'}
        </p>

        <form className="mt-6 space-y-5" onSubmit={onSubmit} noValidate>
          <FormBanner message={banner} />
          <FormField id="currentPassword" label={forced ? 'Temporary password' : 'Current password'} required error={errors.currentPassword?.message}>
            <Input
              id="currentPassword"
              type="password"
              autoComplete="current-password"
              autoFocus
              aria-invalid={errors.currentPassword ? true : undefined}
              aria-describedby="currentPassword-message"
              {...register('currentPassword')}
            />
          </FormField>
          <FormField id="newPassword" label="New password" required error={errors.newPassword?.message}>
            <Input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              aria-invalid={errors.newPassword ? true : undefined}
              aria-describedby="newPassword-message newPassword-rules"
              {...register('newPassword')}
            />
          </FormField>
          <ul id="newPassword-rules" className="-mt-2 space-y-1 text-xs" aria-label="Password rules">
            {rules.map((rule) => (
              <li key={rule.label} className={cn('flex items-center gap-1.5', rule.met ? 'text-status-success' : 'text-muted-foreground')}>
                {rule.met ? <CircleCheck className="size-3.5" aria-hidden /> : <Circle className="size-3.5" aria-hidden />}
                {rule.label}
                <span className="sr-only">{rule.met ? '(done)' : '(not yet)'}</span>
              </li>
            ))}
          </ul>
          <FormField id="confirm" label="Confirm new password" required error={errors.confirm?.message}>
            <Input
              id="confirm"
              type="password"
              autoComplete="new-password"
              aria-invalid={errors.confirm ? true : undefined}
              aria-describedby="confirm-message"
              {...register('confirm')}
            />
          </FormField>

          <Button type="submit" className="h-10 w-full" disabled={isSubmitting}>
            {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
            {isSubmitting ? 'Saving…' : 'Save password'}
          </Button>
        </form>

        <div className="mt-6 flex justify-center">
          {forced ? (
            <Button variant="ghost" size="sm" onClick={() => void logout()}>
              <LogOut aria-hidden />
              Sign out
            </Button>
          ) : (
            <Button variant="ghost" size="sm" onClick={() => navigate(-1)}>
              Cancel
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
