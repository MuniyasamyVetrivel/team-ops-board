import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { ImportDefinition, ImportEndpoint } from '@/features/marketing/api';
import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

/** Everything lives under the module's path, where the server enforces the module switch and VENDOR_VIEW / VENDOR_EDIT. */
const BASE = '/modules/vendors';

/** Mirrors com.teamops.vendor.entity.VendorCategory. */
export type VendorCategory = 'SOFTWARE' | 'HARDWARE' | 'RECRUITMENT_AGENCY' | 'MARKETING' | 'SERVICES' | 'OTHER';
/** Mirrors com.teamops.vendor.entity.VendorStatus: set by people. */
export type VendorStatus = 'ACTIVE' | 'INACTIVE' | 'BLACKLISTED';
/** Mirrors ContractRules.ContractStatus: computed by the server from the dates on today's business date. */
export type ContractStatus = 'UPCOMING' | 'ACTIVE' | 'EXPIRED';

export const VENDOR_CATEGORIES: VendorCategory[] = ['SOFTWARE', 'HARDWARE', 'RECRUITMENT_AGENCY', 'MARKETING', 'SERVICES', 'OTHER'];
export const VENDOR_STATUSES: VendorStatus[] = ['ACTIVE', 'INACTIVE', 'BLACKLISTED'];
export const CONTRACT_STATUSES: ContractStatus[] = ['UPCOMING', 'ACTIVE', 'EXPIRED'];

/** Mirrors VendorDtos.VendorRef. */
export interface VendorRef {
  id: number;
  name: string;
  status: VendorStatus;
}

/** Mirrors VendorDtos.ContactDto. The first contact is the main one. */
export interface Contact {
  id: number;
  name: string;
  role: string | null;
  email: string | null;
  phone: string | null;
}

/** Mirrors VendorDtos.VendorDto. `nextRenewal` is computed by the server (the earliest renewal of a live contract). */
export interface Vendor {
  id: number;
  name: string;
  taxId: string | null;
  category: VendorCategory;
  country: string | null;
  address: string | null;
  website: string | null;
  paymentTerms: string | null;
  status: VendorStatus;
  rating: number | null;
  notes: string | null;
  contacts: Contact[];
  contractCount: number;
  nextRenewal: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors VendorDtos.SaveContact; `id` keeps an existing contact. */
export interface SaveContactInput {
  id: number | null;
  name: string;
  role: string | null;
  email: string | null;
  phone: string | null;
}

/** Mirrors VendorDtos.SaveVendor. Contacts replace the vendor's list, in order. */
export interface SaveVendorInput {
  name: string;
  taxId: string | null;
  category: VendorCategory;
  country: string | null;
  address: string | null;
  website: string | null;
  paymentTerms: string | null;
  status: VendorStatus;
  rating: number | null;
  notes: string | null;
  contacts: SaveContactInput[];
  version?: number;
}

/** Mirrors VendorDtos.DocumentDto. */
export interface ContractDocument {
  fileId: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  addedBy: UserSummary | null;
  addedAt: string;
}

/**
 * Mirrors VendorDtos.ContractDto. `value` is null with `valueHidden` true for viewers without FINANCE_VIEW. Status,
 * `renewsOn` (the renewal date, or else the end date), `daysUntilRenewal` and `noticeBy` come from the server.
 */
export interface Contract {
  id: number;
  vendor: VendorRef;
  title: string;
  startDate: string;
  endDate: string | null;
  value: number | null;
  valueHidden: boolean;
  currency: string;
  renewalDate: string | null;
  autoRenew: boolean;
  noticePeriodDays: number | null;
  notes: string | null;
  status: ContractStatus;
  renewsOn: string | null;
  daysUntilRenewal: number | null;
  noticeBy: string | null;
  documents: ContractDocument[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors VendorDtos.SaveContract. The server ignores `value` on an update by someone without FINANCE_VIEW. */
export interface SaveContractInput {
  vendorId: number;
  title: string;
  startDate: string;
  endDate: string | null;
  value: number | null;
  currency: string;
  renewalDate: string | null;
  autoRenew: boolean;
  noticePeriodDays: number | null;
  notes: string | null;
  version?: number;
}

/** Mirrors VendorLinkSource.LinkGroup: records of other modules (licences and expenses from Phase X8). */
export interface VendorLinkGroup {
  kind: string;
  label: string;
  total: number;
  items: { id: number; title: string; detail: string | null; status: string | null }[];
}

interface Paging {
  page?: number;
  size?: number;
  sort?: string;
}

export interface VendorQuery extends Paging {
  search?: string;
  category?: VendorCategory;
  status?: VendorStatus;
  country?: string;
}

export interface ContractQuery extends Paging {
  search?: string;
  vendorId?: number;
  status?: ContractStatus;
  renewalWithinDays?: number;
  autoRenew?: boolean;
}

export const vendorKeys = {
  all: ['vendors'] as const,
  vendors: () => [...vendorKeys.all, 'vendors'] as const,
  vendorList: (query: VendorQuery) => [...vendorKeys.vendors(), 'list', query] as const,
  vendor: (id: number) => [...vendorKeys.vendors(), 'detail', id] as const,
  links: (id: number) => [...vendorKeys.vendors(), 'links', id] as const,
  contracts: () => [...vendorKeys.all, 'contracts'] as const,
  contractList: (query: ContractQuery) => [...vendorKeys.contracts(), 'list', query] as const,
  contract: (id: number) => [...vendorKeys.contracts(), 'detail', id] as const,
  imports: () => [...vendorKeys.all, 'imports'] as const,
};

/**
 * Vendor lists show contract counts and next renewals, and contracts show their vendor, so every change refreshes the
 * whole module; the edited record's detail cache is set from the response first.
 */
function useInvalidateVendors() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: vendorKeys.all });
  };
}

// --- vendors ----------------------------------------------------------------------------------------------------

export function useVendors(query: VendorQuery, enabled = true) {
  return useQuery({
    queryKey: vendorKeys.vendorList(query),
    queryFn: async () => (await api.get<PageResponse<Vendor>>(`${BASE}/vendors`, { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useVendor(id: number | null) {
  return useQuery({
    queryKey: vendorKeys.vendor(id ?? 0),
    queryFn: async () => (await api.get<Vendor>(`${BASE}/vendors/${id}`)).data,
    enabled: id !== null,
  });
}

/** Licences and expenses linked to the vendor (Phase X8); empty until those modules exist. */
export function useVendorLinks(id: number) {
  return useQuery({
    queryKey: vendorKeys.links(id),
    queryFn: async () => (await api.get<VendorLinkGroup[]>(`${BASE}/vendors/${id}/links`)).data,
  });
}

export function useSaveVendor(id: number | null) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateVendors();
  return useMutation({
    mutationFn: async (input: SaveVendorInput) =>
      id === null ? (await api.post<Vendor>(`${BASE}/vendors`, input)).data : (await api.put<Vendor>(`${BASE}/vendors/${id}`, input)).data,
    onSuccess: (saved) => {
      queryClient.setQueryData(vendorKeys.vendor(saved.id), saved);
      invalidate();
    },
  });
}

export function useDeleteVendor() {
  const invalidate = useInvalidateVendors();
  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`${BASE}/vendors/${id}`);
    },
    onSuccess: invalidate,
  });
}

export function exportVendors(query: Omit<VendorQuery, 'page' | 'size' | 'sort'>): Promise<void> {
  const search = serializeParams(cleanParams(query));
  return downloadFile(`${BASE}/vendors/export${search ? `?${search}` : ''}`, 'vendors.csv');
}

// --- contracts --------------------------------------------------------------------------------------------------

export function useContracts(query: ContractQuery, enabled = true) {
  return useQuery({
    queryKey: vendorKeys.contractList(query),
    queryFn: async () => (await api.get<PageResponse<Contract>>(`${BASE}/contracts`, { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useContract(id: number | null) {
  return useQuery({
    queryKey: vendorKeys.contract(id ?? 0),
    queryFn: async () => (await api.get<Contract>(`${BASE}/contracts/${id}`)).data,
    enabled: id !== null,
  });
}

export function useSaveContract(id: number | null) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateVendors();
  return useMutation({
    mutationFn: async (input: SaveContractInput) =>
      id === null ? (await api.post<Contract>(`${BASE}/contracts`, input)).data : (await api.put<Contract>(`${BASE}/contracts/${id}`, input)).data,
    onSuccess: (saved) => {
      queryClient.setQueryData(vendorKeys.contract(saved.id), saved);
      invalidate();
    },
  });
}

export function useDeleteContract() {
  const invalidate = useInvalidateVendors();
  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`${BASE}/contracts/${id}`);
    },
    onSuccess: invalidate,
  });
}

export function exportContracts(query: Omit<ContractQuery, 'page' | 'size' | 'sort'>): Promise<void> {
  const search = serializeParams(cleanParams(query));
  return downloadFile(`${BASE}/contracts/export${search ? `?${search}` : ''}`, 'vendor-contracts.csv');
}

// --- documents --------------------------------------------------------------------------------------------------

/** Uploads or removes a document; the contract's cached documents are replaced with the server's list. */
export function useContractDocuments(contractId: number) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateVendors();
  const replace = (documents: ContractDocument[]) => {
    queryClient.setQueryData<Contract>(vendorKeys.contract(contractId), (contract) => (contract ? { ...contract, documents } : contract));
    invalidate();
  };
  const upload = useMutation({
    mutationFn: async (file: File) => {
      const form = new FormData();
      form.append('file', file);
      return (await api.post<ContractDocument[]>(`${BASE}/contracts/${contractId}/documents`, form)).data;
    },
    onSuccess: replace,
  });
  const remove = useMutation({
    mutationFn: async (fileId: number) => (await api.delete<ContractDocument[]>(`${BASE}/contracts/${contractId}/documents/${fileId}`)).data,
    onSuccess: replace,
  });
  return { upload, remove };
}

/** Always a download (the server sends it as an attachment). */
export function downloadContractDocument(contractId: number, document: ContractDocument): Promise<void> {
  return downloadFile(`${BASE}/contracts/${contractId}/documents/${document.fileId}`, document.fileName);
}

// --- CSV imports ------------------------------------------------------------------------------------------------

/** `vendors` and `vendor-contracts`, through the shared import flow (validate, review, commit). */
export const VENDOR_IMPORTS: ImportEndpoint = {
  path: (type) => `${BASE}/imports/${type}`,
  invalidate: [vendorKeys.all],
};

export const VENDOR_IMPORT = 'vendors';
export const CONTRACT_IMPORT = 'vendor-contracts';

/** The imports the user may run (VENDOR_EDIT). */
export function useVendorImportDefinitions(enabled: boolean) {
  return useQuery({
    queryKey: vendorKeys.imports(),
    queryFn: async () => (await api.get<ImportDefinition[]>(`${BASE}/imports`)).data,
    staleTime: 5 * 60_000,
    enabled,
  });
}
