import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import type { UserSummary } from '@/lib/api/types';
import { applyServerErrors } from '@/lib/api/form-errors';

import type { Milestone, Risk, SaveMilestoneInput, SaveRiskInput } from './api';
import { MILESTONE_STATUS_LABELS, RISK_LEVEL_LABELS, RISK_STATUS_LABELS } from './project-meta';

/** Mirrors ProjectDtos.SaveMilestone. */
const milestoneSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(200),
  description: z.string().max(5000),
  dueDate: z.string(),
  status: z.enum(['PLANNED', 'IN_PROGRESS', 'COMPLETED']),
});

type MilestoneValues = z.infer<typeof milestoneSchema>;

interface MilestoneDialogProps {
  open: boolean;
  milestone: Milestone | null;
  onOpenChange: (open: boolean) => void;
  onSave: (input: SaveMilestoneInput) => Promise<unknown>;
}

/** Add (milestone = null) or edit a milestone. */
export function MilestoneDialog({ open, milestone, onOpenChange, onSave }: MilestoneDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">{open && <MilestoneForm milestone={milestone} onDone={() => onOpenChange(false)} onSave={onSave} />}</DialogContent>
    </Dialog>
  );
}

function MilestoneForm({ milestone, onDone, onSave }: { milestone: Milestone | null; onDone: () => void; onSave: (input: SaveMilestoneInput) => Promise<unknown> }) {
  const [banner, setBanner] = useState<string | null>(null);
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<MilestoneValues>({
    resolver: zodResolver(milestoneSchema),
    defaultValues: {
      name: milestone?.name ?? '',
      description: milestone?.description ?? '',
      dueDate: milestone?.dueDate ?? '',
      status: milestone?.status ?? 'PLANNED',
    },
  });
  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      await onSave({ name: values.name, description: values.description.trim() || null, dueDate: values.dueDate || null, status: values.status });
      onDone();
    } catch (error) {
      setBanner(applyServerErrors(error, setError, ['name', 'description', 'dueDate'] as const));
    }
  });
  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{milestone ? 'Edit milestone' : 'Add milestone'}</DialogTitle>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="milestone-name" label="Name" required error={errors.name?.message}>
          <Input id="milestone-name" autoFocus aria-invalid={errors.name ? true : undefined} {...register('name')} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField id="milestone-due" label="Due date">
            <Input id="milestone-due" type="date" {...register('dueDate')} />
          </FormField>
          <FormField id="milestone-status" label="Status">
            <Select id="milestone-status" {...register('status')}>
              {(Object.keys(MILESTONE_STATUS_LABELS) as (keyof typeof MILESTONE_STATUS_LABELS)[]).map((s) => (
                <option key={s} value={s}>
                  {MILESTONE_STATUS_LABELS[s]}
                </option>
              ))}
            </Select>
          </FormField>
        </div>
        <FormField id="milestone-description" label="Description">
          <Textarea id="milestone-description" rows={3} {...register('description')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          Save milestone
        </Button>
      </DialogFooter>
    </form>
  );
}

/** Mirrors ProjectDtos.SaveRisk. */
const riskSchema = z.object({
  title: z.string().trim().min(1, 'Title is required').max(200),
  description: z.string().max(5000),
  probability: z.enum(['LOW', 'MEDIUM', 'HIGH']),
  impact: z.enum(['LOW', 'MEDIUM', 'HIGH']),
  mitigation: z.string().max(5000),
  ownerId: z.string(),
  status: z.enum(['OPEN', 'MITIGATED', 'CLOSED']),
});

type RiskValues = z.infer<typeof riskSchema>;

interface RiskDialogProps {
  open: boolean;
  risk: Risk | null;
  people: UserSummary[];
  onOpenChange: (open: boolean) => void;
  onSave: (input: SaveRiskInput) => Promise<unknown>;
}

/** Add (risk = null) or edit a risk. Owners are the project's members and owner. */
export function RiskDialog({ open, risk, people, onOpenChange, onSave }: RiskDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">{open && <RiskForm risk={risk} people={people} onDone={() => onOpenChange(false)} onSave={onSave} />}</DialogContent>
    </Dialog>
  );
}

const LEVELS = ['LOW', 'MEDIUM', 'HIGH'] as const;

function RiskForm({ risk, people, onDone, onSave }: { risk: Risk | null; people: UserSummary[]; onDone: () => void; onSave: (input: SaveRiskInput) => Promise<unknown> }) {
  const [banner, setBanner] = useState<string | null>(null);
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<RiskValues>({
    resolver: zodResolver(riskSchema),
    defaultValues: {
      title: risk?.title ?? '',
      description: risk?.description ?? '',
      probability: risk?.probability ?? 'MEDIUM',
      impact: risk?.impact ?? 'MEDIUM',
      mitigation: risk?.mitigation ?? '',
      ownerId: risk?.owner ? String(risk.owner.id) : '',
      status: risk?.status ?? 'OPEN',
    },
  });
  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      await onSave({
        title: values.title,
        description: values.description.trim() || null,
        probability: values.probability,
        impact: values.impact,
        mitigation: values.mitigation.trim() || null,
        ownerId: values.ownerId ? Number(values.ownerId) : null,
        status: values.status,
      });
      onDone();
    } catch (error) {
      setBanner(applyServerErrors(error, setError, ['title', 'description', 'mitigation'] as const));
    }
  });
  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{risk ? 'Edit risk' : 'Add risk'}</DialogTitle>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="risk-title" label="Risk" required error={errors.title?.message}>
          <Input id="risk-title" autoFocus aria-invalid={errors.title ? true : undefined} {...register('title')} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField id="risk-probability" label="Probability">
            <Select id="risk-probability" {...register('probability')}>
              {LEVELS.map((l) => (
                <option key={l} value={l}>
                  {RISK_LEVEL_LABELS[l]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="risk-impact" label="Impact">
            <Select id="risk-impact" {...register('impact')}>
              {LEVELS.map((l) => (
                <option key={l} value={l}>
                  {RISK_LEVEL_LABELS[l]}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="risk-status" label="Status">
            <Select id="risk-status" {...register('status')}>
              {(Object.keys(RISK_STATUS_LABELS) as (keyof typeof RISK_STATUS_LABELS)[]).map((s) => (
                <option key={s} value={s}>
                  {RISK_STATUS_LABELS[s]}
                </option>
              ))}
            </Select>
          </FormField>
        </div>
        <FormField id="risk-owner" label="Owner">
          <Select id="risk-owner" {...register('ownerId')}>
            <option value="">No owner</option>
            {people.map((p) => (
              <option key={p.id} value={p.id}>
                {p.fullName}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField id="risk-mitigation" label="Mitigation">
          <Textarea id="risk-mitigation" rows={2} {...register('mitigation')} />
        </FormField>
        <FormField id="risk-description" label="Details">
          <Textarea id="risk-description" rows={2} {...register('description')} />
        </FormField>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          Save risk
        </Button>
      </DialogFooter>
    </form>
  );
}
