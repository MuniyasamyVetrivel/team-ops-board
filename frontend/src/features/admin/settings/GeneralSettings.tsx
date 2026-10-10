import { zodResolver } from '@hookform/resolvers/zod';
import { Briefcase, Gauge, LifeBuoy, LoaderCircle, Paperclip, SlidersHorizontal, Target, UsersRound, type LucideIcon } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { ErrorState } from '@/components/common/ErrorState';
import { Panel } from '@/components/common/Panel';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { toApiError } from '@/lib/api/errors';
import { applyServerErrors } from '@/lib/api/form-errors';
import { formatRelative } from '@/lib/format';

import { useSettings, useUpdateSetting, type SettingItem } from './api';
import { groupSettings, settingValueError } from './setting-rules';

const GROUP_ICONS: Record<string, LucideIcon> = {
  Workload: Gauge,
  People: UsersRound,
  'Help desk': LifeBuoy,
  'Digital Marketing': Target,
  Files: Paperclip,
};

/** What each setting changes, in the words of the people who rely on it. */
const EFFECTS: Record<string, string> = {
  'workload.windowDays': 'Workload % counts overdue and undated work plus tasks due within this many days.',
  'workload.defaultTaskHours': 'Hours assumed for an active task without an estimate when workload is calculated.',
  'users.defaultWeeklyCapacityHours': 'Weekly capacity given to new users when none is entered.',
  'sla.warningThresholdPct': 'Tickets show a warning once this share of their SLA time has passed. Applies to new tickets.',
  'marketing.target.behindThresholdPct': 'Targets below this achievement are BEHIND (red). Target types may override it.',
  'upload.maxSizeMb': "Largest file people can attach. It can't exceed the limit the server was started with.",
};

export function GeneralSettings() {
  const settings = useSettings();
  const groups = useMemo(() => groupSettings(settings.data ?? []), [settings.data]);

  if (settings.isPending) {
    return (
      <div className="space-y-4" role="status" aria-label="Loading settings">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="h-36 rounded-xl" />
        ))}
      </div>
    );
  }
  if (settings.isError) return <ErrorState error={settings.error} title="Couldn't load settings" onRetry={() => void settings.refetch()} />;

  return (
    <div className="space-y-4">
      {groups.map(([group, items]) => (
        <Panel key={group} title={group} icon={GROUP_ICONS[group] ?? SlidersHorizontal}>
          <ul className="divide-y">
            {items.map((setting) => (
              <li key={setting.key}>
                <SettingRow setting={setting} onStale={() => void settings.refetch()} />
              </li>
            ))}
          </ul>
        </Panel>
      ))}
      <p className="flex items-center gap-2 text-xs text-muted-foreground">
        <Briefcase className="size-3.5" aria-hidden />
        Every change is recorded in the audit log with the old and new value.
      </p>
    </div>
  );
}

/** One editable setting: its own small form, saved on its own (with the version it was loaded at). */
function SettingRow({ setting, onStale }: { setting: SettingItem; onStale: () => void }) {
  const update = useUpdateSetting();
  const [banner, setBanner] = useState<string | null>(null);
  const schema = useMemo(
    () =>
      z.object({
        value: z.string().superRefine((value, ctx) => {
          const message = settingValueError(setting, value);
          if (message) ctx.addIssue({ code: 'custom', message });
        }),
      }),
    [setting],
  );
  const id = `setting-${setting.key.replace(/\W+/g, '-')}`;
  const { register, handleSubmit, reset, setError, formState: { errors, isDirty, isSubmitting } } = useForm<{ value: string }>({
    resolver: zodResolver(schema),
    values: { value: setting.value },
  });

  const onSubmit = handleSubmit(async ({ value }) => {
    setBanner(null);
    try {
      const saved = await update.mutateAsync({ key: setting.key, version: setting.version, value: value.trim() });
      reset({ value: saved.value });
      toast.success(`${setting.label} saved`);
    } catch (error) {
      const message = applyServerErrors(error, setError, ['value']);
      if (toApiError(error)?.code === 'INVALID_SETTING') setError('value', { type: 'server', message });
      else setBanner(message);
      if (toApiError(error)?.code === 'STALE_UPDATE') onStale();
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-3 px-5 py-4 md:grid-cols-[minmax(0,1fr)_auto] md:items-start">
      <div className="min-w-0">
        <label htmlFor={id} className="text-sm font-medium">
          {setting.label}
        </label>
        <p className="mt-0.5 text-sm text-muted-foreground">{EFFECTS[setting.key] ?? setting.description}</p>
        <p className="mt-1 text-xs text-muted-foreground">
          {setting.min}–{setting.max} {setting.unit}
          {setting.updatedBy && (
            <>
              {' '}
              · Last changed by {setting.updatedBy.fullName} {formatRelative(setting.updatedAt)}
            </>
          )}
        </p>
        {banner && (
          <p className="mt-2 text-sm text-destructive" role="alert">
            {banner}
          </p>
        )}
      </div>
      <div className="flex flex-col gap-1">
        <div className="flex items-center gap-2">
          <div className="relative w-32">
            <Input
              id={id}
              inputMode={setting.valueType === 'INTEGER' ? 'numeric' : 'decimal'}
              className="pr-12 text-right tabular-nums"
              aria-invalid={errors.value ? true : undefined}
              aria-describedby={errors.value ? `${id}-error` : undefined}
              {...register('value')}
            />
            <span className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-xs text-muted-foreground" aria-hidden>
              {setting.unit}
            </span>
          </div>
          <Button type="submit" size="sm" disabled={!isDirty || isSubmitting} aria-label={`Save ${setting.label}`}>
            {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
            Save
          </Button>
          {isDirty && (
            <Button type="button" variant="ghost" size="sm" onClick={() => reset({ value: setting.value })}>
              Undo
            </Button>
          )}
        </div>
        {errors.value && (
          <p id={`${id}-error`} className="max-w-72 text-sm text-destructive">
            {errors.value.message}
          </p>
        )}
      </div>
    </form>
  );
}
