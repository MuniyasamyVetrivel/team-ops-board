import { zodResolver } from '@hookform/resolvers/zod';
import { CircleAlert, Clock, Eye, EyeOff, Gauge, LifeBuoy, LoaderCircle, Target } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Navigate, useLocation, useNavigate, useSearchParams, type Location } from 'react-router';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { BrandMark } from '@/layouts/BrandMark';
import { errorMessage } from '@/lib/api/errors';

import { loginSchema, type LoginFormValues } from './login-schema';
import { useAuth } from './use-auth';

const HIGHLIGHTS = [
  { icon: Gauge, title: 'Workload at a glance', text: 'See who is overloaded, what is overdue and what is due today.' },
  { icon: LifeBuoy, title: 'Help desk with SLAs', text: 'Track tickets, first responses and breaches in one place.' },
  { icon: Target, title: 'Marketing performance', text: 'Rankings, leads, campaigns and monthly targets.' },
];

function redirectTarget(location: Location): string {
  const from = (location.state as { from?: Location } | null)?.from;
  return from && from.pathname !== '/login' ? `${from.pathname}${from.search}` : '/dashboard';
}

export default function LoginPage() {
  const { status, login } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });

  if (status === 'authenticated') {
    return <Navigate to={redirectTarget(location)} replace />;
  }

  const onSubmit = handleSubmit(async (values) => {
    setServerError(null);
    try {
      await login(values);
      navigate(redirectTarget(location), { replace: true });
    } catch (error) {
      setServerError(errorMessage(error, 'Sign in failed. Please try again.'));
    }
  });

  const sessionExpired = searchParams.get('expired') === '1' && !serverError;

  return (
    <div className="grid min-h-svh lg:grid-cols-[1.05fr_1fr]">
      <section className="relative hidden flex-col justify-between overflow-hidden bg-navy-900 p-10 text-white lg:flex">
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.15]"
          style={{
            backgroundImage:
              'linear-gradient(to right, rgb(185 192 236 / 0.35) 1px, transparent 1px), linear-gradient(to bottom, rgb(185 192 236 / 0.35) 1px, transparent 1px)',
            backgroundSize: '44px 44px',
          }}
          aria-hidden
        />
        <BrandMark inverted className="relative" />
        <div className="relative max-w-md">
          <h1 className="text-3xl leading-tight font-semibold tracking-tight">
            One place to see who is working on what — and how the team is performing.
          </h1>
          <ul className="mt-8 space-y-5">
            {HIGHLIGHTS.map(({ icon: Icon, title, text }) => (
              <li key={title} className="flex gap-3">
                <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-lg bg-white/10">
                  <Icon className="size-4 text-highlight" aria-hidden />
                </span>
                <span>
                  <span className="block text-sm font-medium">{title}</span>
                  <span className="block text-sm text-sidebar-foreground">{text}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
        <p className="relative text-xs text-sidebar-muted">Internal use only. Access is monitored and audited.</p>
      </section>

      <section className="flex items-center justify-center px-4 py-12 sm:px-8">
        <div className="w-full max-w-sm">
          <BrandMark className="mb-10 lg:hidden" />
          <h2 className="text-page-title font-bold tracking-tight">Sign in</h2>
          <p className="mt-1.5 text-sm text-muted-foreground">Use your company email and password.</p>

          {sessionExpired && (
            <div className="mt-6 flex items-start gap-2.5 rounded-lg border border-status-warning/40 bg-status-warning/10 px-3 py-2.5 text-sm" role="status">
              <Clock className="mt-0.5 size-4 shrink-0 text-status-warning" aria-hidden />
              <span>Your session expired. Please sign in again.</span>
            </div>
          )}
          {serverError && (
            <div className="mt-6 flex items-start gap-2.5 rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2.5 text-sm" role="alert">
              <CircleAlert className="mt-0.5 size-4 shrink-0 text-destructive" aria-hidden />
              <span>{serverError}</span>
            </div>
          )}

          <form className="mt-6 space-y-5" onSubmit={onSubmit} noValidate>
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="username"
                autoFocus
                placeholder="name@company.com"
                aria-invalid={errors.email ? true : undefined}
                aria-describedby={errors.email ? 'email-error' : undefined}
                {...register('email')}
              />
              {errors.email && (
                <p id="email-error" className="text-sm text-destructive">
                  {errors.email.message}
                </p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <div className="relative">
                <Input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  className="pr-10"
                  aria-invalid={errors.password ? true : undefined}
                  aria-describedby={errors.password ? 'password-error' : undefined}
                  {...register('password')}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((value) => !value)}
                  className="absolute inset-y-0 right-0 flex w-10 items-center justify-center rounded-r-md text-muted-foreground hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
              {errors.password && (
                <p id="password-error" className="text-sm text-destructive">
                  {errors.password.message}
                </p>
              )}
            </div>

            <Button type="submit" className="h-10 w-full" disabled={isSubmitting}>
              {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
              {isSubmitting ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>

          <p className="mt-8 text-center text-xs text-muted-foreground">
            Forgot your password? Contact your administrator.
          </p>
        </div>
      </section>
    </div>
  );
}
