import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { DepartmentSummary } from '@/features/auth/types';
import type { ImportDefinition, ImportEndpoint } from '@/features/marketing/api';
import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

/** Everything lives under the module's path, where the server enforces the module switch and BENCH_VIEW / BENCH_EDIT. */
const BASE = '/modules/bench';

/** Mirrors com.teamops.bench.entity.EmploymentStatus: set by HR. */
export type EmploymentStatus = 'ACTIVE' | 'LEAVE_OF_ABSENCE' | 'NOTICE_PERIOD' | 'LEFT';
/** Mirrors com.teamops.bench.service.ConsultantStatus: computed by the server from HR's status and today's allocations. */
export type ConsultantStatus = 'ALLOCATED' | 'PARTIALLY_ALLOCATED' | 'ON_BENCH' | 'LEAVE_OF_ABSENCE' | 'NOTICE_PERIOD' | 'LEFT';
/** Mirrors com.teamops.bench.service.PeriodStatus: engagements and allocations, from their dates on today's business date. */
export type PeriodStatus = 'UPCOMING' | 'ACTIVE' | 'ENDED';
export type ClientStatus = 'ACTIVE' | 'INACTIVE';

export const EMPLOYMENT_STATUSES: EmploymentStatus[] = ['ACTIVE', 'LEAVE_OF_ABSENCE', 'NOTICE_PERIOD', 'LEFT'];
export const CONSULTANT_STATUSES: ConsultantStatus[] = ['ALLOCATED', 'PARTIALLY_ALLOCATED', 'ON_BENCH', 'LEAVE_OF_ABSENCE', 'NOTICE_PERIOD', 'LEFT'];
export const PERIOD_STATUSES: PeriodStatus[] = ['UPCOMING', 'ACTIVE', 'ENDED'];

/** Mirrors BenchDtos.ConsultantEmployee: the Employee record a consultant is; `user` is the linked app user (avatar). */
export interface ConsultantEmployee {
  id: number;
  employeeCode: string;
  fullName: string;
  designation: string | null;
  department: DepartmentSummary | null;
  workEmail: string | null;
  user: UserSummary | null;
}

/** Mirrors BenchDtos.CertificationDto; `expired` comes from the business calendar. */
export interface Certification {
  id: number;
  name: string;
  issuer: string | null;
  issuedOn: string | null;
  expiresOn: string | null;
  credentialId: string | null;
  expired: boolean;
}

/** Mirrors BenchDtos.ConsultantDto. `employee` is null while the Employee record is in the recycle bin. */
export interface Consultant {
  id: number;
  employeeId: number;
  employee: ConsultantEmployee | null;
  experienceYears: number | null;
  location: string | null;
  availableFrom: string | null;
  employmentStatus: EmploymentStatus;
  status: ConsultantStatus;
  /** Total allocation today; may exceed 100. */
  allocatedPercent: number;
  skills: string[];
  certifications: Certification[];
  notes: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors BenchDtos.SaveCertification; `id` keeps an existing certification. */
export interface SaveCertificationInput {
  id: number | null;
  name: string;
  issuer: string | null;
  issuedOn: string | null;
  expiresOn: string | null;
  credentialId: string | null;
}

/** Mirrors BenchDtos.SaveConsultant. Skills and certifications replace the consultant's lists. */
export interface SaveConsultantInput {
  employeeId: number;
  experienceYears: number | null;
  location: string | null;
  availableFrom: string | null;
  employmentStatus: EmploymentStatus;
  skills: string[];
  certifications: SaveCertificationInput[];
  notes: string | null;
  version?: number;
}

export interface ClientRef {
  id: number;
  name: string;
}

/** Mirrors BenchDtos.ClientDto; `engagementCount` counts live engagements. */
export interface Client {
  id: number;
  name: string;
  location: string | null;
  status: ClientStatus;
  notes: string | null;
  engagementCount: number;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface SaveClientInput {
  name: string;
  location: string | null;
  status: ClientStatus;
  notes: string | null;
  version?: number;
}

/** Mirrors BenchDtos.EngagementDto. */
export interface Engagement {
  id: number;
  client: ClientRef;
  name: string;
  location: string | null;
  startDate: string;
  endDate: string | null;
  status: PeriodStatus;
  accountManager: UserSummary | null;
  notes: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface SaveEngagementInput {
  clientId: number;
  name: string;
  location: string | null;
  startDate: string;
  endDate: string | null;
  accountManagerId: number | null;
  notes: string | null;
  version?: number;
}

/** Mirrors BenchDtos.ConsultantRef; `fullName` is null while the employee record is in the recycle bin. */
export interface ConsultantRef {
  id: number;
  employeeCode: string | null;
  fullName: string | null;
  user: UserSummary | null;
}

export interface EngagementRef {
  id: number;
  name: string;
  client: ClientRef;
}

/** Mirrors BenchDtos.AllocationDto. `daysUntilEnd` is null when open-ended or already ended. */
export interface Allocation {
  id: number;
  consultant: ConsultantRef;
  engagement: EngagementRef;
  role: string;
  startDate: string;
  endDate: string | null;
  allocationPercent: number;
  billable: boolean;
  status: PeriodStatus;
  daysUntilEnd: number | null;
  notes: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface SaveAllocationInput {
  consultantId: number;
  engagementId: number;
  role: string;
  startDate: string;
  endDate: string | null;
  allocationPercent: number;
  billable: boolean;
  notes: string | null;
  version?: number;
}

/** Mirrors BenchDtos.OverAllocation: a stretch above 100%; `to` is null when open-ended. */
export interface OverAllocation {
  from: string;
  to: string | null;
  totalPercent: number;
}

/** Mirrors BenchDtos.AllocationSaved: saved, with a warning wherever it takes the consultant above 100%. */
export interface AllocationSaved {
  allocation: Allocation;
  overAllocations: OverAllocation[];
}

/** Mirrors BenchDtos.BenchEntry: an active consultant below 100% today. */
export interface BenchEntry {
  consultantId: number;
  employee: ConsultantEmployee | null;
  location: string | null;
  skills: string[];
  availableFrom: string | null;
  status: ConsultantStatus;
  allocatedPercent: number;
  benchPercent: number;
  benchSince: string;
  daysOnBench: number;
}

/** Mirrors BenchDtos.BenchDashboard on the business date `asOf`. FTE: one consultant at 100% is 1.00; null % without capacity. */
export interface BenchDashboard {
  asOf: string;
  byStatus: { status: ConsultantStatus; count: number }[];
  capacityConsultants: number;
  capacityFte: number;
  allocatedFte: number;
  billableFte: number;
  nonBillableFte: number;
  benchFte: number;
  utilisationPercent: number | null;
  billablePercent: number | null;
  nonBillablePercent: number | null;
  onBenchNow: number;
  unallocated: number;
  averageDaysOnBench: number | null;
  longestDaysOnBench: number;
  longestOnBench: BenchEntry[];
  upcomingRollOffCount: number;
  upcomingRollOffs: Allocation[];
  benchSkills: { skill: string; consultants: number }[];
}

interface Paging {
  page?: number;
  size?: number;
  sort?: string;
}

export interface ConsultantQuery extends Paging {
  search?: string;
  status?: ConsultantStatus;
  skill?: string;
}

export interface BenchQuery extends Paging {
  search?: string;
  skill?: string;
}

export interface ClientQuery extends Paging {
  search?: string;
  status?: ClientStatus;
}

export interface EngagementQuery extends Paging {
  search?: string;
  clientId?: number;
  status?: PeriodStatus;
  accountManagerId?: number;
}

export interface AllocationQuery extends Paging {
  search?: string;
  consultantId?: number;
  engagementId?: number;
  clientId?: number;
  status?: PeriodStatus;
  billable?: boolean;
}

export interface AllocationCheckQuery {
  consultantId: number;
  startDate: string;
  endDate: string | null;
  allocationPercent: number;
  excludeId: number | null;
}

export const benchKeys = {
  all: ['bench'] as const,
  dashboard: () => [...benchKeys.all, 'dashboard'] as const,
  bench: (query: BenchQuery) => [...benchKeys.all, 'bench', query] as const,
  consultants: () => [...benchKeys.all, 'consultants'] as const,
  consultantList: (query: ConsultantQuery) => [...benchKeys.consultants(), 'list', query] as const,
  consultant: (id: number) => [...benchKeys.consultants(), 'detail', id] as const,
  candidates: (search: string) => [...benchKeys.consultants(), 'candidates', search] as const,
  clients: () => [...benchKeys.all, 'clients'] as const,
  clientList: (query: ClientQuery) => [...benchKeys.clients(), 'list', query] as const,
  client: (id: number) => [...benchKeys.clients(), 'detail', id] as const,
  engagements: () => [...benchKeys.all, 'engagements'] as const,
  engagementList: (query: EngagementQuery) => [...benchKeys.engagements(), 'list', query] as const,
  engagement: (id: number) => [...benchKeys.engagements(), 'detail', id] as const,
  allocations: () => [...benchKeys.all, 'allocations'] as const,
  allocationList: (query: AllocationQuery) => [...benchKeys.allocations(), 'list', query] as const,
  allocation: (id: number) => [...benchKeys.allocations(), 'detail', id] as const,
  check: (query: AllocationCheckQuery) => [...benchKeys.allocations(), 'check', query] as const,
  importDefinition: () => [...benchKeys.all, 'import-definition'] as const,
};

/**
 * Lists, details, the bench and the dashboard all follow from consultants, engagements and allocations (a consultant's
 * status and allocated %, days on bench, live engagement counts), so every change refreshes the whole module; the
 * edited record's detail cache is set from the response first.
 */
function useInvalidateBench() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: benchKeys.all });
  };
}

// --- dashboard and bench ----------------------------------------------------------------------------------------

export function useBenchDashboard() {
  return useQuery({
    queryKey: benchKeys.dashboard(),
    queryFn: async () => (await api.get<BenchDashboard>(`${BASE}/dashboard`)).data,
    staleTime: 60_000,
  });
}

export function useBench(query: BenchQuery) {
  return useQuery({
    queryKey: benchKeys.bench(query),
    queryFn: async () => (await api.get<PageResponse<BenchEntry>>(`${BASE}/bench`, { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
  });
}

// --- consultants ------------------------------------------------------------------------------------------------

export function useConsultants(query: ConsultantQuery, enabled = true) {
  return useQuery({
    queryKey: benchKeys.consultantList(query),
    queryFn: async () => (await api.get<PageResponse<Consultant>>(`${BASE}/consultants`, { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useConsultant(id: number | null) {
  return useQuery({
    queryKey: benchKeys.consultant(id ?? 0),
    queryFn: async () => (await api.get<Consultant>(`${BASE}/consultants/${id}`)).data,
    enabled: id !== null,
  });
}

/** Active employees who are not consultants yet (BENCH_EDIT). */
export function useConsultantCandidates(search: string, enabled = true) {
  return useQuery({
    queryKey: benchKeys.candidates(search),
    queryFn: async () => (await api.get<ConsultantEmployee[]>(`${BASE}/consultants/candidates`, { params: cleanParams({ search }) })).data,
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useSaveConsultant(id: number | null) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateBench();
  return useMutation({
    mutationFn: async (input: SaveConsultantInput) =>
      id === null ? (await api.post<Consultant>(`${BASE}/consultants`, input)).data : (await api.put<Consultant>(`${BASE}/consultants/${id}`, input)).data,
    onSuccess: (saved) => {
      queryClient.setQueryData(benchKeys.consultant(saved.id), saved);
      invalidate();
    },
  });
}

export function useDeleteConsultant() {
  const invalidate = useInvalidateBench();
  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`${BASE}/consultants/${id}`);
    },
    onSuccess: invalidate,
  });
}

export function exportConsultants(query: Omit<ConsultantQuery, 'page' | 'size' | 'sort'>): Promise<void> {
  const search = serializeParams(cleanParams(query));
  return downloadFile(`${BASE}/consultants/export${search ? `?${search}` : ''}`, 'consultants.csv');
}

/** The consultant CSV import, through the shared import flow (validate, review, commit). */
export const CONSULTANT_IMPORT = 'consultants';

export const BENCH_IMPORTS: ImportEndpoint = {
  path: () => `${BASE}/imports/consultants`,
  invalidate: [benchKeys.all],
};

/** The consultant import's columns (BENCH_EDIT). */
export function useConsultantImportDefinition(enabled: boolean) {
  return useQuery({
    queryKey: benchKeys.importDefinition(),
    queryFn: async () => (await api.get<ImportDefinition>(`${BASE}/imports/consultants`)).data,
    staleTime: 5 * 60_000,
    enabled,
  });
}

// --- clients ----------------------------------------------------------------------------------------------------

export function useClients(query: ClientQuery, enabled = true) {
  return useQuery({
    queryKey: benchKeys.clientList(query),
    queryFn: async () => (await api.get<PageResponse<Client>>(`${BASE}/clients`, { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useClient(id: number | null) {
  return useQuery({
    queryKey: benchKeys.client(id ?? 0),
    queryFn: async () => (await api.get<Client>(`${BASE}/clients/${id}`)).data,
    enabled: id !== null,
  });
}

export function useSaveClient(id: number | null) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateBench();
  return useMutation({
    mutationFn: async (input: SaveClientInput) =>
      id === null ? (await api.post<Client>(`${BASE}/clients`, input)).data : (await api.put<Client>(`${BASE}/clients/${id}`, input)).data,
    onSuccess: (saved) => {
      queryClient.setQueryData(benchKeys.client(saved.id), saved);
      invalidate();
    },
  });
}

export function useDeleteClient() {
  const invalidate = useInvalidateBench();
  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`${BASE}/clients/${id}`);
    },
    onSuccess: invalidate,
  });
}

// --- engagements ------------------------------------------------------------------------------------------------

export function useEngagements(query: EngagementQuery, enabled = true) {
  return useQuery({
    queryKey: benchKeys.engagementList(query),
    queryFn: async () => (await api.get<PageResponse<Engagement>>(`${BASE}/engagements`, { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useEngagement(id: number | null) {
  return useQuery({
    queryKey: benchKeys.engagement(id ?? 0),
    queryFn: async () => (await api.get<Engagement>(`${BASE}/engagements/${id}`)).data,
    enabled: id !== null,
  });
}

export function useSaveEngagement(id: number | null) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateBench();
  return useMutation({
    mutationFn: async (input: SaveEngagementInput) =>
      id === null ? (await api.post<Engagement>(`${BASE}/engagements`, input)).data : (await api.put<Engagement>(`${BASE}/engagements/${id}`, input)).data,
    onSuccess: (saved) => {
      queryClient.setQueryData(benchKeys.engagement(saved.id), saved);
      invalidate();
    },
  });
}

export function useDeleteEngagement() {
  const invalidate = useInvalidateBench();
  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`${BASE}/engagements/${id}`);
    },
    onSuccess: invalidate,
  });
}

// --- allocations ------------------------------------------------------------------------------------------------

export function useAllocations(query: AllocationQuery, enabled = true) {
  return useQuery({
    queryKey: benchKeys.allocationList(query),
    queryFn: async () => (await api.get<PageResponse<Allocation>>(`${BASE}/allocations`, { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useAllocation(id: number | null) {
  return useQuery({
    queryKey: benchKeys.allocation(id ?? 0),
    queryFn: async () => (await api.get<Allocation>(`${BASE}/allocations/${id}`)).data,
    enabled: id !== null,
  });
}

/** Where an allocation like this would take the consultant above 100%. Saves nothing. */
export function useAllocationCheck(query: AllocationCheckQuery | null) {
  return useQuery({
    queryKey: benchKeys.check(query ?? { consultantId: 0, startDate: '', endDate: null, allocationPercent: 0, excludeId: null }),
    queryFn: async () => (await api.get<{ overAllocations: OverAllocation[] }>(`${BASE}/allocations/check`, { params: cleanParams({ ...query }) })).data.overAllocations,
    enabled: query !== null,
    placeholderData: keepPreviousData,
    staleTime: 0,
  });
}

export function useSaveAllocation(id: number | null) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateBench();
  return useMutation({
    mutationFn: async (input: SaveAllocationInput) =>
      id === null ? (await api.post<AllocationSaved>(`${BASE}/allocations`, input)).data : (await api.put<AllocationSaved>(`${BASE}/allocations/${id}`, input)).data,
    onSuccess: (saved) => {
      queryClient.setQueryData(benchKeys.allocation(saved.allocation.id), saved.allocation);
      invalidate();
    },
  });
}

export function useDeleteAllocation() {
  const invalidate = useInvalidateBench();
  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`${BASE}/allocations/${id}`);
    },
    onSuccess: invalidate,
  });
}
