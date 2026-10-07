import { zodResolver } from '@hookform/resolvers/zod';
import { KeyRound, LoaderCircle, UserCheck, UserX } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';

import { FormBanner, FormField } from '@/components/common/FormField';
import { StatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAuth } from '@/features/auth/use-auth';
import { errorMessage } from '@/lib/api/errors';
import { formatRelative } from '@/lib/format';

import { useResetPassword, useSetUserStatus, type UserDetail } from './api';
import { resetPasswordSchema, type ResetPasswordFormValues } from './schemas';

export function SecurityPanel({ user }: { user: UserDetail }) {
  const { user: actor } = useAuth();
  const isSelf = actor?.id === user.id;
  const setStatus = useSetUserStatus(user.id);
  const resetPassword = useResetPassword(user.id);
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmDisable, setConfirmDisable] = useState(false);

  const form = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: '', confirm: '' },
  });
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = form;

  async function changeStatus(next: 'ACTIVE' | 'DISABLED') {
    setBanner(null);
    try {
      await setStatus.mutateAsync(next);
      setConfirmDisable(false);
      toast.success(next === 'DISABLED' ? `${user.fullName} was disabled and signed out` : `${user.fullName} was re-enabled`);
    } catch (error) {
      setBanner(errorMessage(error));
    }
  }

  const onReset = handleSubmit(async (values) => {
    setBanner(null);
    try {
      await resetPassword.mutateAsync(values.newPassword);
      reset();
      toast.success('Password reset. They have been signed out of every device.');
    } catch (error) {
      setBanner(errorMessage(error));
    }
  });

  if (isSelf) {
    return <p className="text-sm text-muted-foreground">You cannot disable or reset the password of your own account here.</p>;
  }

  return (
    <div className="space-y-8">
      <FormBanner message={banner} />

      <section className="space-y-3">
        <h3 className="text-sm font-semibold">Account status</h3>
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-4">
          <div className="space-y-1 text-sm">
            <StatusBadge status={user.status} />
            <p className="text-muted-foreground">Last sign-in {formatRelative(user.lastLoginAt)}</p>
          </div>
          {user.status === 'ACTIVE' ? (
            confirmDisable ? (
              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={() => setConfirmDisable(false)}>
                  Cancel
                </Button>
                <Button variant="destructive" size="sm" disabled={setStatus.isPending} onClick={() => void changeStatus('DISABLED')}>
                  {setStatus.isPending && <LoaderCircle className="animate-spin" aria-hidden />}
                  Confirm disable
                </Button>
              </div>
            ) : (
              <Button variant="outline" size="sm" onClick={() => setConfirmDisable(true)}>
                <UserX aria-hidden />
                Disable account
              </Button>
            )
          ) : (
            <Button variant="outline" size="sm" disabled={setStatus.isPending} onClick={() => void changeStatus('ACTIVE')}>
              <UserCheck aria-hidden />
              Enable account
            </Button>
          )}
        </div>
        <p className="text-xs text-muted-foreground">Disabling signs them out everywhere. Their history is kept.</p>
      </section>

      <section className="space-y-3">
        <h3 className="text-sm font-semibold">Reset password</h3>
        <form onSubmit={onReset} noValidate className="space-y-4 rounded-lg border p-4">
          <FormField id="newPassword" label="New password" hint="8+ characters with a letter and a number" error={errors.newPassword?.message}>
            <Input id="newPassword" type="password" autoComplete="new-password" aria-invalid={errors.newPassword ? true : undefined} {...register('newPassword')} />
          </FormField>
          <FormField id="confirm" label="Confirm password" error={errors.confirm?.message}>
            <Input id="confirm" type="password" autoComplete="new-password" aria-invalid={errors.confirm ? true : undefined} {...register('confirm')} />
          </FormField>
          <Button type="submit" variant="outline" disabled={isSubmitting}>
            {isSubmitting ? <LoaderCircle className="animate-spin" aria-hidden /> : <KeyRound aria-hidden />}
            Reset password
          </Button>
        </form>
      </section>
    </div>
  );
}
