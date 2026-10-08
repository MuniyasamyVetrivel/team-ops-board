import { zodResolver } from '@hookform/resolvers/zod';
import { CircleCheck, Copy, LoaderCircle } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { EmptyState } from '@/components/common/EmptyState';
import { FormBanner } from '@/components/common/FormField';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogFooter, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useAuth } from '@/features/auth/use-auth';
import { errorMessage } from '@/lib/api/errors';

import { useMarketingContext } from '../api';
import { formatMetric, periodLabel, previousPeriod } from '../marketing-format';
import { useMonthlyTargets, useSetMonthlyTargets, type TypeRef } from './api';
import { parseValue, UNIT_FORMATS, UNIT_LABELS, valueField } from './target-meta';

interface SetTargetsSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  month: number;
  year: number;
  /** Active types with no target for the month yet. */
  types: TypeRef[];
}

/** Sets the month's targets for every type that has none yet, in one all-or-nothing request. */
export function SetTargetsSheet({ open, onOpenChange, month, year, types }: SetTargetsSheetProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-2xl">
        <DialogHeader className="pr-12">
          <DialogTitle>Set targets for {periodLabel(month, year)}</DialogTitle>
          <DialogDescription>Target types without a target this month. Leave a row empty to skip it.</DialogDescription>
        </DialogHeader>
        {open &&
          (types.length === 0 ? (
            <EmptyState icon={CircleCheck} title={`Every type has a target for ${periodLabel(month, year)}`} description="Change a target from its card or the table." />
          ) : (
            <SetTargetsForm key={types.map((t) => t.id).join(',')} month={month} year={year} types={types} onDone={() => onOpenChange(false)} />
          ))}
      </SheetContent>
    </Dialog>
  );
}

const fieldName = (type: TypeRef) => `t${type.id}`;

function SetTargetsForm({ month, year, types, onDone }: { month: number; year: number; types: TypeRef[]; onDone: () => void }) {
  const { user } = useAuth();
  const context = useMarketingContext();
  const setMonthly = useSetMonthlyTargets();
  const before = previousPeriod(month, year);
  const lastMonth = useMonthlyTargets(before);
  const [banner, setBanner] = useState<string | null>(null);

  // One field per type, each checked against its own unit; at least one must be filled.
  const schema = useMemo(
    () =>
      z
        .object({
          ownerId: z.string(),
          values: z.object(Object.fromEntries(types.map((type) => [fieldName(type), valueField(type.unit, { required: false })]))),
        })
        .refine((v) => Object.values(v.values).some((value) => String(value).trim() !== ''), { path: ['root'], message: 'Enter at least one target' }),
    [types],
  );
  type Values = z.infer<typeof schema>;

  const owners = context.data?.owners ?? [];
  const { register, handleSubmit, setValue, control, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      ownerId: String(owners.some((o) => o.id === user?.id) ? user?.id : ''),
      values: Object.fromEntries(types.map((type) => [fieldName(type), ''])),
    },
  });
  const values = useWatch({ control, name: 'values' }) as Record<string, string>;
  const filled = Object.values(values).filter((value) => String(value ?? '').trim() !== '').length;
  const previous = new Map((lastMonth.data?.targets ?? []).map((t) => [t.type.id, t.targetValue]));
  const copyable = types.filter((type) => previous.has(type.id));
  const valueErrors = (errors.values ?? {}) as Record<string, { message?: string } | undefined>;
  const formError = (errors as { root?: { message?: string } }).root?.message;

  function copyLastMonth() {
    copyable.forEach((type) => setValue(`values.${fieldName(type)}` as `values.${string}`, String(previous.get(type.id)), { shouldDirty: true }));
  }

  const onSubmit = handleSubmit(async (form) => {
    setBanner(null);
    const entered = form.values as Record<string, string>;
    try {
      const result = await setMonthly.mutateAsync({
        month,
        year,
        ownerId: form.ownerId ? Number(form.ownerId) : null,
        departmentId: user?.department.id ?? 0,
        entries: types
          .filter((type) => (entered[fieldName(type)] ?? '').trim() !== '')
          .map((type) => ({ typeId: type.id, targetValue: parseValue(entered[fieldName(type)] ?? '')! })),
      });
      toast.success(`${result.created} ${result.created === 1 ? 'target' : 'targets'} set for ${result.period.label}`);
      onDone();
    } catch (error) {
      setBanner(errorMessage(error));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <div className="min-h-0 flex-1 space-y-4 overflow-y-auto px-6">
        <FormBanner message={banner ?? formError ?? null} />
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div className="grid gap-1">
            <label htmlFor="set-owner" className="text-xs font-medium text-muted-foreground">
              Owner of these targets
            </label>
            <Select id="set-owner" className="w-56" {...register('ownerId')}>
              <option value="">No owner</option>
              {owners.map((owner) => (
                <option key={owner.id} value={owner.id}>
                  {owner.fullName}
                </option>
              ))}
            </Select>
          </div>
          <Button type="button" variant="outline" size="sm" disabled={copyable.length === 0} onClick={copyLastMonth}>
            <Copy aria-hidden />
            Copy {periodLabel(before.month, before.year)}
          </Button>
        </div>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Target type</TableHead>
              <TableHead>{periodLabel(before.month, before.year)}</TableHead>
              <TableHead className="w-40">Target</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {types.map((type) => {
              const error = valueErrors[fieldName(type)]?.message;
              return (
                <TableRow key={type.id}>
                  <TableCell>
                    <p className="font-medium">{type.name}</p>
                    <p className="flex flex-wrap gap-1.5 text-xs text-muted-foreground">
                      {UNIT_LABELS[type.unit]}
                      {type.automatic && <Badge tone="primary">Automatic actual</Badge>}
                    </p>
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground tabular-nums">{previous.has(type.id) ? formatMetric(previous.get(type.id), UNIT_FORMATS[type.unit]) : '—'}</TableCell>
                  <TableCell>
                    <Input inputMode="decimal" className="h-8" aria-label={`${type.name} target`} aria-invalid={error ? true : undefined} {...register(`values.${fieldName(type)}` as `values.${string}`)} />
                    {error && <p className="mt-1 text-xs text-destructive">{error}</p>}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
      <DialogFooter>
        <span className="mr-auto text-sm text-muted-foreground" aria-live="polite">
          {filled} of {types.length} filled in
        </span>
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          Set {filled > 0 ? filled : ''} {filled === 1 ? 'target' : 'targets'}
        </Button>
      </DialogFooter>
    </form>
  );
}
