import { z } from 'zod';

import type { RoleCode } from '@/features/auth/permissions';

import type { TemplateStep, WorkflowStepInput } from './api';

/** Mirrors ApprovalDtos.WorkflowStep lists: 1–5 steps; role and user steps need their approver. */
export const stepsSchema = z
  .array(
    z
      .object({ approverKind: z.enum(['DEPARTMENT_MANAGER', 'ROLE', 'USER']), roleCode: z.string(), userId: z.string() })
      .refine((s) => s.approverKind !== 'ROLE' || s.roleCode !== '', { path: ['roleCode'], message: 'Choose a role' })
      .refine((s) => s.approverKind !== 'USER' || s.userId !== '', { path: ['userId'], message: 'Choose a person' }),
  )
  .min(1, 'At least one step is required')
  .max(5, 'At most 5 steps');

export type StepValues = z.infer<typeof stepsSchema>[number];

/** Any form that edits a workflow has its steps under `steps`. */
export interface StepsFormValues {
  steps: StepValues[];
}

export const DEFAULT_STEP: StepValues = { approverKind: 'DEPARTMENT_MANAGER', roleCode: '', userId: '' };

export function stepValues(steps: readonly TemplateStep[]): StepValues[] {
  return steps.map((s) => ({ approverKind: s.approverKind, roleCode: s.role?.code ?? '', userId: s.user ? String(s.user.id) : '' }));
}

export function toStepInputs(steps: readonly StepValues[]): WorkflowStepInput[] {
  return steps.map((s) => ({
    approverKind: s.approverKind,
    roleCode: s.approverKind === 'ROLE' ? (s.roleCode as RoleCode) : null,
    userId: s.approverKind === 'USER' ? Number(s.userId) : null,
  }));
}

