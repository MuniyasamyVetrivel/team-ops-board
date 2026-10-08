import { zodResolver } from '@hookform/resolvers/zod';
import { CircleSlash, LoaderCircle, Lock, Pencil, Plus, Trash2, Zap } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { ErrorState } from '@/components/common/ErrorState';
import { FormBanner, FormField } from '@/components/common/FormField';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { applyServerErrors } from '@/lib/api/form-errors';
import { errorMessage } from '@/lib/api/errors';

import { formatPercent } from '../marketing-format';
import { ACTUAL_SOURCES, LEAD_SOURCES, TARGET_UNITS, useCreateTargetType, useDeleteTargetType, useTargetTypes, useUpdateTargetType, type TargetTypeItem } from './api';
import { ACTUAL_SOURCE_LABELS, LEAD_SOURCE_LABELS, UNIT_LABELS } from './target-meta';

/** Target type administration for Super Admins and marketing managers (MARKETING_EDIT). */
export function TargetTypesSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-3xl">
        <DialogHeader className="pr-12">
          <DialogTitle>Target types</DialogTitle>
          <DialogDescription>Once a type has targets, its unit and actual source are fixed so past months keep their meaning.</DialogDescription>
        </DialogHeader>
        {open && <TypesBody />}
      </SheetContent>
    </Dialog>
  );
}

type Mode = { kind: 'list' } | { kind: 'create' } | { kind: 'edit'; type: TargetTypeItem };

function TypesBody() {
  const types = useTargetTypes(true);
  const remove = useDeleteTargetType();
  const [mode, setMode] = useState<Mode>({ kind: 'list' });

  async function onDelete(type: TargetTypeItem) {
    try {
      await remove.mutateAsync(type.id);
      toast.success(`${type.name} deleted`);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <div className="min-h-0 flex-1 space-y-4 overflow-y-auto px-6 pb-6">
      {mode.kind === 'list' ? (
        <div className="flex justify-end">
          <Button size="sm" onClick={() => setMode({ kind: 'create' })}>
            <Plus aria-hidden />
            Add target type
          </Button>
        </div>
      ) : (
        <TypeForm type={mode.kind === 'edit' ? mode.type : undefined} onDone={() => setMode({ kind: 'list' })} />
      )}
      {types.isPending ? (
        <div className="space-y-2" role="status" aria-label="Loading target types">
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} className="h-11" />
          ))}
        </div>
      ) : types.isError ? (
        <ErrorState error={types.error} title="Couldn't load target types" onRetry={() => void types.refetch()} />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Type</TableHead>
              <TableHead>Actual</TableHead>
              <TableHead className="text-right">Behind below</TableHead>
              <TableHead className="text-right">Months</TableHead>
              <TableHead>
                <span className="sr-only">Actions</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {types.data.map((type) => (
              <TableRow key={type.id} className={type.active ? undefined : 'opacity-70'}>
                <TableCell>
                  <p className="font-medium">{type.name}</p>
                  <p className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                    <span className="font-mono">{type.code}</span> · {UNIT_LABELS[type.unit]}
                    {!type.active && (
                      <Badge tone="neutral">
                        <CircleSlash aria-hidden />
                        Inactive
                      </Badge>
                    )}
                  </p>
                </TableCell>
                <TableCell className="text-sm">
                  <span className="flex items-center gap-1.5">
                    {type.automatic && <Zap className="size-3.5 text-primary" aria-label="Automatic" />}
                    {ACTUAL_SOURCE_LABELS[type.actualSource]}
                    {type.leadSourceFilter && ` (${LEAD_SOURCE_LABELS[type.leadSourceFilter]})`}
                  </span>
                  {!type.automatic && type.actualSource !== 'MANUAL' && <p className="text-xs text-muted-foreground">Entered by hand until that module is available</p>}
                </TableCell>
                <TableCell className="text-right text-sm tabular-nums">
                  {formatPercent(type.effectiveThresholdPct)}
                  {type.behindThresholdPct === null && <span className="block text-xs text-muted-foreground">default</span>}
                </TableCell>
                <TableCell className="text-right text-sm tabular-nums">
                  <span className="inline-flex items-center gap-1">
                    {type.locked && <Lock className="size-3 text-muted-foreground" aria-label="In use: unit and source are fixed" />}
                    {type.targetCount}
                  </span>
                </TableCell>
                <TableCell>
                  <div className="flex justify-end">
                    <Button variant="ghost" size="icon" aria-label={`Edit ${type.name}`} disabled={mode.kind !== 'list'} onClick={() => setMode({ kind: 'edit', type })}>
                      <Pencil aria-hidden />
                    </Button>
                    {type.targetCount === 0 && (
                      <Button variant="ghost" size="icon" className="text-destructive" aria-label={`Delete ${type.name}`} disabled={mode.kind !== 'list' || remove.isPending} onClick={() => void onDelete(type)}>
                        <Trash2 aria-hidden />
                      </Button>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  );
}

/** Mirrors TargetDtos.CreateTargetType / UpdateTargetType. */
const typeSchema = z
  .object({
    name: z.string().trim().min(1, 'Name is required').max(100, 'At most 100 characters'),
    // Disabled (fixed) when editing, which RHF reports as undefined.
    code: z
      .string()
      .trim()
      .max(50, 'At most 50 characters')
      .refine((v) => v === '' || /^[A-Za-z][A-Za-z0-9_]*$/.test(v), 'Letters, digits and underscores, starting with a letter')
      .optional(),
    description: z.string().max(500, 'At most 500 characters'),
    // Disabled (locked) selects are reported as undefined; the type's current values are used then.
    unit: z.enum(['COUNT', 'CURRENCY', 'PERCENT']).optional(),
    actualSource: z.enum(['MANUAL', 'LEADS', 'LEADS_BY_SOURCE', 'BACKLINKS_LIVE', 'BLOGS_PUBLISHED', 'KEYWORDS_TOP10', 'EMAIL_CAMPAIGNS', 'PAID_CAMPAIGNS', 'LANDING_PAGES']).optional(),
    leadSourceFilter: z.string().optional(),
    threshold: z
      .string()
      .trim()
      .refine((v) => v === '' || (/^\d+(\.\d{1,2})?$/.test(v) && Number(v) <= 100), 'A percentage from 0 to 100'),
    active: z.boolean(),
    position: z.string().trim().refine((v) => /^\d+$/.test(v), 'A whole number'),
  })
  .refine((v) => v.actualSource !== 'LEADS_BY_SOURCE' || Boolean(v.leadSourceFilter), { path: ['leadSourceFilter'], message: 'Choose which lead source counts' });

type TypeValues = z.infer<typeof typeSchema>;

const SERVER_FIELDS = ['name', 'code', 'description', 'unit', 'actualSource', 'leadSourceFilter', 'position'] as const;

function TypeForm({ type, onDone }: { type?: TargetTypeItem; onDone: () => void }) {
  const create = useCreateTargetType();
  const update = useUpdateTargetType(type?.id ?? 0);
  const [banner, setBanner] = useState<string | null>(null);
  const editing = type !== undefined;
  const locked = type?.locked ?? false;

  const { register, handleSubmit, control, setError, formState: { errors, isSubmitting } } = useForm<TypeValues>({
    resolver: zodResolver(typeSchema),
    defaultValues: {
      name: type?.name ?? '',
      code: type?.code ?? '',
      description: type?.description ?? '',
      unit: type?.unit ?? 'COUNT',
      actualSource: type?.actualSource ?? 'MANUAL',
      leadSourceFilter: type?.leadSourceFilter ?? '',
      threshold: type?.behindThresholdPct?.toString() ?? '',
      active: type?.active ?? true,
      position: String(type?.position ?? 0),
    },
  });
  const source = useWatch({ control, name: 'actualSource' }) ?? type?.actualSource;

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const actualSource = (locked ? type?.actualSource : values.actualSource) ?? 'MANUAL';
    const body = {
      name: values.name,
      description: values.description.trim() || null,
      unit: (locked ? type?.unit : values.unit) ?? 'COUNT',
      actualSource,
      leadSourceFilter: actualSource === 'LEADS_BY_SOURCE' ? ((locked ? type?.leadSourceFilter : values.leadSourceFilter) as TargetTypeItem['leadSourceFilter']) || null : null,
      behindThresholdPct: values.threshold === '' ? null : Number(values.threshold),
    };
    try {
      if (editing) {
        await update.mutateAsync({ ...body, version: type.version, active: values.active, position: Number(values.position) });
        toast.success(`${values.name} updated`);
      } else {
        await create.mutateAsync({ ...body, code: values.code || null });
        toast.success(`${values.name} added`);
      }
      onDone();
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4 rounded-xl border bg-muted/30 p-4" aria-label={editing ? `Edit ${type.name}` : 'Add target type'}>
      <h3 className="text-sm font-semibold">{editing ? `Edit ${type.name}` : 'Add target type'}</h3>
      <FormBanner message={banner} />
      {locked && <p className="text-xs text-muted-foreground">This type has {type?.targetCount} monthly targets, so its unit and actual source are fixed.</p>}
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField id="type-name" label="Name" required error={errors.name?.message}>
          <Input id="type-name" autoFocus aria-invalid={errors.name ? true : undefined} aria-describedby="type-name-message" {...register('name')} />
        </FormField>
        <FormField id="type-code" label="Code" hint={editing ? 'Fixed once created' : 'Optional; made from the name'} error={errors.code?.message}>
          <Input id="type-code" className="font-mono" disabled={editing} aria-invalid={errors.code ? true : undefined} aria-describedby="type-code-message" {...register('code')} />
        </FormField>
        <FormField id="type-unit" label="Unit" required>
          <Select id="type-unit" disabled={locked} {...register('unit')}>
            {TARGET_UNITS.map((unit) => (
              <option key={unit} value={unit}>
                {UNIT_LABELS[unit]}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField id="type-source" label="Actual comes from" required>
          <Select id="type-source" disabled={locked} {...register('actualSource')}>
            {ACTUAL_SOURCES.map((s) => (
              <option key={s} value={s}>
                {ACTUAL_SOURCE_LABELS[s]}
              </option>
            ))}
          </Select>
        </FormField>
        {source === 'LEADS_BY_SOURCE' && (
          <FormField id="type-lead-source" label="Lead source" required error={errors.leadSourceFilter?.message}>
            <Select id="type-lead-source" disabled={locked} aria-invalid={errors.leadSourceFilter ? true : undefined} {...register('leadSourceFilter')}>
              <option value="">Choose…</option>
              {LEAD_SOURCES.map((s) => (
                <option key={s} value={s}>
                  {LEAD_SOURCE_LABELS[s]}
                </option>
              ))}
            </Select>
          </FormField>
        )}
        <FormField id="type-threshold" label="Behind below (%)" hint="Empty uses the global setting" error={errors.threshold?.message}>
          <Input id="type-threshold" inputMode="decimal" aria-invalid={errors.threshold ? true : undefined} aria-describedby="type-threshold-message" {...register('threshold')} />
        </FormField>
        <FormField id="type-description" label="Description" error={errors.description?.message} className="sm:col-span-2">
          <Input id="type-description" aria-invalid={errors.description ? true : undefined} {...register('description')} />
        </FormField>
        {editing && (
          <>
            <FormField id="type-position" label="Order" error={errors.position?.message}>
              <Input id="type-position" inputMode="numeric" aria-invalid={errors.position ? true : undefined} aria-describedby="type-position-message" {...register('position')} />
            </FormField>
            <div className="flex items-end pb-2.5">
              <label className="flex items-center gap-2 text-sm">
                <Checkbox {...register('active')} />
                Active (offered for new months)
              </label>
            </div>
          </>
        )}
      </div>
      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save type' : 'Add type'}
        </Button>
      </div>
    </form>
  );
}
