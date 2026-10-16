import { zodResolver } from '@hookform/resolvers/zod';
import { Award, LoaderCircle, Plus, Search, Trash2, X } from 'lucide-react';
import { useState, type KeyboardEvent } from 'react';
import { Controller, useFieldArray, useForm, type Control, type FieldErrors, type UseFormRegister } from 'react-hook-form';
import { toast } from 'sonner';

import { FormBanner, FormField } from '@/components/common/FormField';
import { UserAvatar } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
import { toastDeleted } from '@/features/recycle-bin/undo-toast';
import { errorMessage } from '@/lib/api/errors';
import { applyServerErrors } from '@/lib/api/form-errors';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { cn } from '@/lib/utils';

import { EMPLOYMENT_STATUSES, useConsultantCandidates, useDeleteConsultant, useSaveConsultant, type Consultant } from './api';
import { consultantName, EMPLOYMENT_STATUS_LABELS } from './bench-meta';
import { addSkill, consultantSchema, MAX_CERTIFICATIONS, MAX_SKILLS, type ConsultantValues } from './bench-schemas';

const SERVER_FIELDS = ['employeeId', 'experienceYears', 'location', 'availableFrom', 'employmentStatus', 'skills', 'certifications', 'notes'] as const;

interface ConsultantFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit this consultant; add a new one when absent. */
  consultant?: Consultant;
  onSaved?: (id: number) => void;
  onDeleted?: () => void;
}

export function ConsultantFormDialog({ open, onOpenChange, consultant, onSaved, onDeleted }: ConsultantFormDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">{open && <ConsultantForm consultant={consultant} onDone={() => onOpenChange(false)} onSaved={onSaved} onDeleted={onDeleted} />}</DialogContent>
    </Dialog>
  );
}

function ConsultantForm({ consultant, onDone, onSaved, onDeleted }: { consultant?: Consultant; onDone: () => void; onSaved?: (id: number) => void; onDeleted?: () => void }) {
  const editing = consultant !== undefined;
  const save = useSaveConsultant(consultant?.id ?? null);
  const remove = useDeleteConsultant();
  const [banner, setBanner] = useState<string | null>(null);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ConsultantValues>({
    resolver: zodResolver(consultantSchema),
    defaultValues: {
      employeeId: consultant ? String(consultant.employeeId) : '',
      experienceYears: consultant?.experienceYears != null ? String(consultant.experienceYears) : '',
      location: consultant?.location ?? '',
      availableFrom: consultant?.availableFrom ?? '',
      employmentStatus: consultant?.employmentStatus ?? 'ACTIVE',
      skills: consultant?.skills ?? [],
      certifications: (consultant?.certifications ?? []).map((c) => ({
        id: c.id,
        name: c.name,
        issuer: c.issuer ?? '',
        issuedOn: c.issuedOn ?? '',
        expiresOn: c.expiresOn ?? '',
        credentialId: c.credentialId ?? '',
      })),
      notes: consultant?.notes ?? '',
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    const optional = (value: string) => value.trim() || null;
    try {
      const saved = await save.mutateAsync({
        version: consultant?.version,
        employeeId: Number(values.employeeId),
        experienceYears: values.experienceYears ? Number(values.experienceYears) : null,
        location: optional(values.location),
        availableFrom: values.availableFrom || null,
        employmentStatus: values.employmentStatus,
        skills: values.skills,
        certifications: values.certifications.map((c) => ({
          id: c.id,
          name: c.name.trim(),
          issuer: optional(c.issuer),
          issuedOn: c.issuedOn || null,
          expiresOn: c.expiresOn || null,
          credentialId: optional(c.credentialId),
        })),
        notes: optional(values.notes),
      });
      toast.success(editing ? 'Consultant updated' : `${consultantName(saved.employee?.fullName)} added as a consultant`);
      onDone();
      onSaved?.(saved.id);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  async function onDelete() {
    if (!consultant) return;
    try {
      await remove.mutateAsync(consultant.id);
      toastDeleted('Consultant deleted', 'CONSULTANT', consultant.id);
      onDone();
      onDeleted?.();
    } catch (error) {
      setConfirmingDelete(false);
      setBanner(errorMessage(error));
    }
  }

  const invalid = (name: keyof ConsultantValues) => (errors[name] ? true : undefined);

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{editing ? `Edit ${consultantName(consultant.employee?.fullName)}` : 'Add a consultant'}</DialogTitle>
        <DialogDescription>
          {editing
            ? 'Name, employee ID and department come from the Employee record.'
            : 'A consultant is an Employee record plus bench details. Pick the employee, then add their skills and certifications.'}
        </DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-6">
        <FormBanner message={banner} />
        {!editing && (
          <Controller
            control={control}
            name="employeeId"
            render={({ field, fieldState }) => <EmployeePicker value={field.value} onChange={field.onChange} error={fieldState.error?.message} />}
          />
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="consultant-experienceYears" label="Experience (years)" error={errors.experienceYears?.message}>
            <Input id="consultant-experienceYears" inputMode="decimal" placeholder="e.g. 6.5" aria-invalid={invalid('experienceYears')} {...register('experienceYears')} />
          </FormField>
          <FormField id="consultant-location" label="Location" error={errors.location?.message}>
            <Input id="consultant-location" placeholder="e.g. Chennai" aria-invalid={invalid('location')} {...register('location')} />
          </FormField>
          <FormField id="consultant-availableFrom" label="Available from" hint="Days on the bench count from here at the earliest" error={errors.availableFrom?.message}>
            <Input id="consultant-availableFrom" type="date" aria-invalid={invalid('availableFrom')} {...register('availableFrom')} />
          </FormField>
          <FormField id="consultant-employmentStatus" label="Employment status" hint="Set by HR. Allocated, partially allocated or on bench is worked out from allocations.">
            <Select id="consultant-employmentStatus" {...register('employmentStatus')}>
              {EMPLOYMENT_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {EMPLOYMENT_STATUS_LABELS[s]}
                </option>
              ))}
            </Select>
          </FormField>
        </div>
        <Controller
          control={control}
          name="skills"
          render={({ field, fieldState }) => <SkillsInput skills={field.value} onChange={field.onChange} error={fieldState.error?.message ?? errors.skills?.root?.message ?? errors.skills?.find?.((e) => e)?.message} />}
        />
        <CertificationFields control={control} register={register} errors={errors} />
        <FormField id="consultant-notes" label="Notes" error={errors.notes?.message}>
          <Textarea id="consultant-notes" rows={3} aria-invalid={invalid('notes')} {...register('notes')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        {editing && (
          <Button type="button" variant="ghost" className="mr-auto text-destructive" disabled={remove.isPending} onClick={() => (confirmingDelete ? void onDelete() : setConfirmingDelete(true))}>
            <Trash2 aria-hidden />
            {confirmingDelete ? 'Confirm delete' : 'Delete consultant'}
          </Button>
        )}
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          {editing ? 'Save changes' : 'Add consultant'}
        </Button>
      </DialogFooter>
    </form>
  );
}

/** Active employees who are not consultants yet, searched on the server. */
function EmployeePicker({ value, onChange, error }: { value: string; onChange: (value: string) => void; error?: string }) {
  const [search, setSearch] = useState('');
  const debounced = useDebouncedValue(search.trim());
  const candidates = useConsultantCandidates(debounced);

  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium">
        Employee <span className="text-destructive">*</span>
      </legend>
      <div className="relative">
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
        <Input className="pl-9" placeholder="Search name or employee ID" aria-label="Search employees" value={search} onChange={(e) => setSearch(e.target.value)} />
      </div>
      <div className={cn('max-h-56 overflow-y-auto rounded-lg border', error && 'border-destructive')} role="radiogroup" aria-label="Employee" aria-invalid={error ? true : undefined}>
        {candidates.isPending ? (
          <div className="space-y-2 p-3" role="status" aria-label="Loading employees">
            <Skeleton className="h-10" />
            <Skeleton className="h-10" />
          </div>
        ) : candidates.isError ? (
          <p className="p-4 text-sm text-destructive">{errorMessage(candidates.error)}</p>
        ) : candidates.data.length === 0 ? (
          <p className="p-4 text-sm text-muted-foreground">{debounced ? 'No matching employee who is not a consultant yet.' : 'Every active employee is already a consultant.'}</p>
        ) : (
          <ul className="divide-y">
            {candidates.data.map((e) => {
              const selected = value === String(e.id);
              return (
                <li key={e.id}>
                  <label className={cn('flex cursor-pointer items-center gap-3 px-3 py-2 hover:bg-muted/50', selected && 'bg-accent')}>
                    <input type="radio" name="consultant-employee" className="accent-primary" value={e.id} checked={selected} onChange={() => onChange(String(e.id))} />
                    <UserAvatar name={e.fullName} user={e.user} size="sm" />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">{e.fullName}</span>
                      <span className="block truncate text-xs text-muted-foreground">{[e.employeeCode, e.designation, e.department?.name].filter(Boolean).join(' · ')}</span>
                    </span>
                  </label>
                </li>
              );
            })}
          </ul>
        )}
      </div>
      {error && <p className="text-sm text-destructive">{error}</p>}
    </fieldset>
  );
}

/** Skills as removable chips; Enter or a comma adds what was typed. */
function SkillsInput({ skills, onChange, error }: { skills: string[]; onChange: (skills: string[]) => void; error?: string }) {
  const [draft, setDraft] = useState('');

  function commit() {
    if (!draft.trim()) return;
    onChange(addSkill(skills, draft));
    setDraft('');
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      commit();
    } else if (event.key === 'Backspace' && !draft && skills.length > 0) {
      onChange(skills.slice(0, -1));
    }
  }

  return (
    <FormField id="consultant-skills" label="Skills" hint={`For example SAP S/4HANA, Tosca, test automation. Press Enter after each; up to ${MAX_SKILLS}.`} error={error}>
      <div className={cn('flex flex-wrap items-center gap-1.5 rounded-md border bg-background px-2 py-1.5 focus-within:ring-[3px] focus-within:ring-ring/40', error && 'border-destructive')}>
        {skills.map((skill) => (
          <span key={skill} className="inline-flex items-center gap-1 rounded-md bg-muted px-2 py-0.5 text-sm">
            {skill}
            <button type="button" className="rounded-sm text-muted-foreground hover:text-foreground" aria-label={`Remove ${skill}`} onClick={() => onChange(skills.filter((s) => s !== skill))}>
              <X className="size-3.5" aria-hidden />
            </button>
          </span>
        ))}
        <input
          id="consultant-skills"
          className="min-w-32 flex-1 bg-transparent px-1 py-1 text-sm outline-none placeholder:text-muted-foreground"
          placeholder={skills.length ? 'Add another skill' : 'Type a skill and press Enter'}
          value={draft}
          aria-invalid={error ? true : undefined}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKeyDown}
          onBlur={commit}
        />
      </div>
    </FormField>
  );
}

function CertificationFields({ control, register, errors }: { control: Control<ConsultantValues>; register: UseFormRegister<ConsultantValues>; errors: FieldErrors<ConsultantValues> }) {
  const { fields, append, remove } = useFieldArray({ control, name: 'certifications', keyName: 'key' });

  return (
    <section aria-label="Certifications" className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-medium">Certifications</h3>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={fields.length >= MAX_CERTIFICATIONS}
          onClick={() => append({ id: null, name: '', issuer: '', issuedOn: '', expiresOn: '', credentialId: '' })}
        >
          <Plus aria-hidden />
          Add certification
        </Button>
      </div>
      {fields.length === 0 ? (
        <p className="flex items-center gap-2 rounded-lg border border-dashed px-4 py-3 text-sm text-muted-foreground">
          <Award className="size-4" aria-hidden />
          No certifications yet.
        </p>
      ) : (
        <ol className="space-y-3">
          {fields.map((field, index) => {
            const fieldErrors = errors.certifications?.[index];
            const id = (name: string) => `certification-${index}-${name}`;
            return (
              <li key={field.key} className="rounded-lg border p-4">
                <div className="grid gap-3 sm:grid-cols-2">
                  <FormField id={id('name')} label="Name" required error={fieldErrors?.name?.message}>
                    <Input id={id('name')} placeholder="e.g. Tricentis Tosca Automation Specialist" aria-invalid={fieldErrors?.name ? true : undefined} {...register(`certifications.${index}.name`)} />
                  </FormField>
                  <FormField id={id('issuer')} label="Issuer" error={fieldErrors?.issuer?.message}>
                    <Input id={id('issuer')} {...register(`certifications.${index}.issuer`)} />
                  </FormField>
                  <FormField id={id('issuedOn')} label="Issued on">
                    <Input id={id('issuedOn')} type="date" {...register(`certifications.${index}.issuedOn`)} />
                  </FormField>
                  <FormField id={id('expiresOn')} label="Expires on" error={fieldErrors?.expiresOn?.message}>
                    <Input id={id('expiresOn')} type="date" aria-invalid={fieldErrors?.expiresOn ? true : undefined} {...register(`certifications.${index}.expiresOn`)} />
                  </FormField>
                  <FormField id={id('credentialId')} label="Credential ID" error={fieldErrors?.credentialId?.message}>
                    <Input id={id('credentialId')} {...register(`certifications.${index}.credentialId`)} />
                  </FormField>
                  <div className="flex items-end justify-end">
                    <Button type="button" variant="ghost" size="sm" className="text-destructive" onClick={() => remove(index)}>
                      <Trash2 aria-hidden />
                      Remove
                    </Button>
                  </div>
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}
