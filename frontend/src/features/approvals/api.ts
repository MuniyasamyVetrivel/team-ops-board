import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { RoleCode } from '@/features/auth/permissions';
import { api } from '@/lib/api/client';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
export type StepStatus = 'WAITING' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'SKIPPED';
export type ApproverKind = 'DEPARTMENT_MANAGER' | 'ROLE' | 'USER';
export type ApprovalView = 'ALL' | 'MINE' | 'TO_DECIDE';

export const APPROVAL_STATUSES: ApprovalStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'];

export interface RoleRef {
  code: RoleCode;
  name: string;
}

export interface TypeRef {
  id: number;
  code: string;
  name: string;
  requiresAmount: boolean;
}

export interface TemplateStep {
  stepOrder: number;
  approverKind: ApproverKind;
  role: RoleRef | null;
  user: UserSummary | null;
}

/** Mirrors ApprovalDtos.TypeResponse. */
export interface ApprovalType {
  id: number;
  code: string;
  name: string;
  description: string | null;
  requiresAmount: boolean;
  active: boolean;
  steps: TemplateStep[];
  version: number;
}

export interface ApprovalStep {
  stepOrder: number;
  approverKind: ApproverKind;
  approver: UserSummary | null;
  role: RoleRef | null;
  status: StepStatus;
  decidedBy: UserSummary | null;
  comment: string | null;
  decidedAt: string | null;
}

/** Mirrors ApprovalDtos.ApprovalListItem. */
export interface ApprovalListItem {
  id: number;
  code: string;
  title: string;
  type: TypeRef;
  status: ApprovalStatus;
  requester: UserSummary | null;
  department: { id: number; name: string; code: string };
  amount: number | null;
  currency: string;
  dueDate: string | null;
  currentStep: number | null;
  stepCount: number;
  waitingOn: string | null;
  awaitingMe: boolean;
  createdAt: string;
  decidedAt: string | null;
}

/** Mirrors ApprovalDtos.ApprovalDetail. */
export interface ApprovalDetail {
  id: number;
  code: string;
  title: string;
  description: string | null;
  type: TypeRef;
  status: ApprovalStatus;
  requester: UserSummary | null;
  department: { id: number; name: string; code: string };
  amount: number | null;
  currency: string;
  dueDate: string | null;
  currentStep: number | null;
  steps: ApprovalStep[];
  createdAt: string;
  decidedAt: string | null;
  version: number;
  permissions: { canDecide: boolean; canCancel: boolean };
}

export interface ApprovalQuery {
  search?: string;
  status?: ApprovalStatus[];
  typeId?: number;
  view?: ApprovalView;
  page?: number;
  size?: number;
  sort?: string;
}

export interface SubmitApprovalInput {
  typeId: number;
  title: string;
  description: string | null;
  amount: number | null;
  currency: string;
  dueDate: string | null;
}

export interface WorkflowStepInput {
  approverKind: ApproverKind;
  roleCode?: RoleCode | null;
  userId?: number | null;
}

export const approvalKeys = {
  all: ['approvals'] as const,
  lists: () => [...approvalKeys.all, 'list'] as const,
  list: (query: ApprovalQuery) => [...approvalKeys.lists(), query] as const,
  detail: (id: number) => [...approvalKeys.all, 'detail', id] as const,
  types: () => [...approvalKeys.all, 'types'] as const,
};

export function useApprovals(query: ApprovalQuery) {
  return useQuery({
    queryKey: approvalKeys.list(query),
    queryFn: async () =>
      (await api.get<PageResponse<ApprovalListItem>>('/approvals', { params: cleanParams(query), paramsSerializer: serializeParams })).data,
    placeholderData: keepPreviousData,
  });
}

export function useApproval(id: number | null) {
  return useQuery({
    queryKey: approvalKeys.detail(id ?? 0),
    queryFn: async () => (await api.get<ApprovalDetail>(`/approvals/${id}`)).data,
    enabled: id !== null,
  });
}

export function useApprovalTypes() {
  return useQuery({
    queryKey: approvalKeys.types(),
    queryFn: async () => (await api.get<ApprovalType[]>('/approvals/types')).data,
    staleTime: 5 * 60_000,
  });
}

function useApprovalMutation<V>(mutationFn: (variables: V) => Promise<ApprovalDetail>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (detail) => {
      queryClient.setQueryData(approvalKeys.detail(detail.id), detail);
      void queryClient.invalidateQueries({ queryKey: approvalKeys.lists() });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      void queryClient.invalidateQueries({ queryKey: ['calendar'] });
    },
  });
}

const data = <T,>(promise: Promise<{ data: T }>) => promise.then((response) => response.data);

export function useSubmitApproval() {
  return useApprovalMutation((input: SubmitApprovalInput) => data(api.post<ApprovalDetail>('/approvals', input)));
}

export function useDecideApproval(id: number) {
  return useApprovalMutation(({ decision, comment }: { decision: 'APPROVE' | 'REJECT'; comment: string | null }) =>
    data(api.post<ApprovalDetail>(`/approvals/${id}/decision`, { decision, comment })),
  );
}

export function useCancelApproval(id: number) {
  return useApprovalMutation(() => data(api.post<ApprovalDetail>(`/approvals/${id}/cancel`)));
}

export function useUpdateWorkflow(typeId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (steps: WorkflowStepInput[]) => (await api.put<ApprovalType>(`/approvals/types/${typeId}/steps`, { steps })).data,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: approvalKeys.types() }),
  });
}

/** Mirrors ApprovalDtos.CreateType: the code is permanent. */
export interface CreateApprovalTypeInput {
  code: string;
  name: string;
  description: string | null;
  requiresAmount: boolean;
  steps: WorkflowStepInput[];
}

/** Mirrors ApprovalDtos.UpdateType. Inactive types stay on existing requests but cannot be chosen for new ones. */
export interface UpdateApprovalTypeInput {
  version: number;
  name: string;
  description: string | null;
  requiresAmount: boolean;
  active: boolean;
}

export function useCreateApprovalType() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: CreateApprovalTypeInput) => (await api.post<ApprovalType>('/approvals/types', input)).data,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: approvalKeys.types() }),
  });
}

export function useUpdateApprovalType(typeId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (input: UpdateApprovalTypeInput) => (await api.put<ApprovalType>(`/approvals/types/${typeId}`, input)).data,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: approvalKeys.types() }),
  });
}
