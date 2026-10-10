import { ROLE_LABELS } from '@/features/auth/permissions';

import type { ApprovalStatus, ApprovalStep, ApproverKind, StepStatus, TemplateStep } from './api';

export const APPROVAL_STATUS_LABELS: Record<ApprovalStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

export const STEP_STATUS_LABELS: Record<StepStatus, string> = {
  WAITING: 'Waiting',
  PENDING: 'Awaiting decision',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SKIPPED: 'Skipped',
};

export const APPROVER_KIND_LABELS: Record<ApproverKind, string> = {
  DEPARTMENT_MANAGER: "Requester's department manager",
  ROLE: 'Anyone with a role',
  USER: 'A named person',
};

/** Who decides a template step, in words. */
export function templateApprover(step: TemplateStep): string {
  if (step.approverKind === 'DEPARTMENT_MANAGER') return 'Department manager';
  if (step.approverKind === 'ROLE') return step.role ? `Any ${ROLE_LABELS[step.role.code] ?? step.role.name}` : 'A role';
  return step.user?.fullName ?? 'A named person';
}

/** Who decides (or decided) a request step, in words. */
export function stepApprover(step: ApprovalStep): string {
  if (step.approver) return step.approver.fullName;
  if (step.role) return `Any ${ROLE_LABELS[step.role.code] ?? step.role.name}`;
  return 'Nobody';
}

const money = new Map<string, Intl.NumberFormat>();

/** "₹14,500.00"; "—" when there is no amount. */
export function formatAmount(amount: number | null, currency: string): string {
  if (amount === null) return '—';
  let format = money.get(currency);
  if (!format) {
    format = new Intl.NumberFormat('en-IN', { style: 'currency', currency });
    money.set(currency, format);
  }
  return format.format(amount);
}

/** "Travel request" → "TRAVEL_REQUEST", as a starting point for the permanent code. */
export function suggestCode(name: string): string {
  return name
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '_')
    .replace(/^[^A-Z]+/, '')
    .replace(/_+$/, '')
    .slice(0, 40);
}
