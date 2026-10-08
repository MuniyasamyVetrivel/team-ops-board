import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { ProjectRef } from '@/features/tasks/types';
import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, type PageResponse, type UserSummary } from '@/lib/api/types';

export interface DocumentVersion {
  versionNo: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  changeNote: string | null;
  uploadedBy: UserSummary | null;
  uploadedAt: string;
}

/** Mirrors DocumentDtos.DocumentItem. {@code department = null} is company-wide. */
export interface DocumentItem {
  id: number;
  name: string;
  description: string | null;
  department: { id: number; name: string; code: string } | null;
  project: ProjectRef | null;
  uploadedBy: UserSummary | null;
  current: DocumentVersion | null;
  versionCount: number;
  createdAt: string;
  updatedAt: string;
  canEdit: boolean;
}

export interface DocumentDetail {
  id: number;
  name: string;
  description: string | null;
  department: { id: number; name: string; code: string } | null;
  project: ProjectRef | null;
  uploadedBy: UserSummary | null;
  versions: DocumentVersion[];
  createdAt: string;
  updatedAt: string;
  version: number;
  canEdit: boolean;
}

export interface DocumentQuery {
  search?: string;
  departmentId?: number;
  projectId?: number;
  page?: number;
  size?: number;
}

export interface UploadDocumentInput {
  file: File;
  name: string;
  description: string;
  departmentId: number | null;
  projectId: number | null;
}

export const documentKeys = {
  all: ['documents'] as const,
  lists: () => [...documentKeys.all, 'list'] as const,
  list: (query: DocumentQuery) => [...documentKeys.lists(), query] as const,
  detail: (id: number) => [...documentKeys.all, 'detail', id] as const,
};

export function useDocuments(query: DocumentQuery) {
  return useQuery({
    queryKey: documentKeys.list(query),
    queryFn: async () => (await api.get<PageResponse<DocumentItem>>('/documents', { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
  });
}

export function useDocument(id: number | null) {
  return useQuery({
    queryKey: documentKeys.detail(id ?? 0),
    queryFn: async () => (await api.get<DocumentDetail>(`/documents/${id}`)).data,
    enabled: id !== null,
  });
}

function useDocumentMutation<V>(mutationFn: (variables: V) => Promise<DocumentDetail>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (detail) => {
      queryClient.setQueryData(documentKeys.detail(detail.id), detail);
      void queryClient.invalidateQueries({ queryKey: documentKeys.lists() });
    },
  });
}

export function useUploadDocument() {
  return useDocumentMutation(async ({ file, name, description, departmentId, projectId }: UploadDocumentInput) => {
    const form = new FormData();
    form.append('file', file);
    if (name.trim()) form.append('name', name.trim());
    if (description.trim()) form.append('description', description.trim());
    if (departmentId !== null) form.append('departmentId', String(departmentId));
    if (projectId !== null) form.append('projectId', String(projectId));
    return (await api.post<DocumentDetail>('/documents', form)).data;
  });
}

export function useAddDocumentVersion(id: number) {
  return useDocumentMutation(async ({ file, changeNote }: { file: File; changeNote: string }) => {
    const form = new FormData();
    form.append('file', file);
    if (changeNote.trim()) form.append('changeNote', changeNote.trim());
    return (await api.post<DocumentDetail>(`/documents/${id}/versions`, form)).data;
  });
}

export function useUpdateDocument(id: number) {
  return useDocumentMutation(async (input: { version: number; name: string; description: string | null }) => (await api.put<DocumentDetail>(`/documents/${id}`, input)).data);
}

export function useDeleteDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`/documents/${id}`);
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: documentKeys.lists() }),
  });
}

export function downloadDocumentVersion(id: number, version: DocumentVersion): Promise<void> {
  return downloadFile(`/documents/${id}/versions/${version.versionNo}/download`, version.fileName);
}
