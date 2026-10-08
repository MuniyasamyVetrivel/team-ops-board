import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

export type ArticleStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface KnowledgeCategory {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  articleCount: number;
}

interface CategoryRef {
  id: number;
  name: string;
  slug: string;
}

/** Mirrors KnowledgeDtos.ArticleListItem. */
export interface ArticleListItem {
  id: number;
  title: string;
  slug: string;
  excerpt: string;
  category: CategoryRef;
  status: ArticleStatus;
  author: UserSummary | null;
  tags: string[];
  publishedAt: string | null;
  updatedAt: string;
  viewCount: number;
}

export interface ArticleAttachment {
  fileId: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  addedBy: UserSummary | null;
  addedAt: string;
}

/** Mirrors KnowledgeDtos.ArticleDetail. {@code body} is Markdown. */
export interface ArticleDetail {
  id: number;
  title: string;
  slug: string;
  body: string;
  category: CategoryRef;
  status: ArticleStatus;
  author: UserSummary | null;
  department: { id: number; name: string; code: string } | null;
  tags: string[];
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
  viewCount: number;
  version: number;
  attachments: ArticleAttachment[];
  canEdit: boolean;
}

export interface ArticleQuery {
  search?: string;
  categoryId?: number;
  status?: ArticleStatus[];
  tag?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface SaveArticleInput {
  version?: number;
  title: string;
  body: string;
  categoryId: number;
  departmentId: number | null;
  tags: string[];
  status: ArticleStatus;
}

export const knowledgeKeys = {
  all: ['knowledge'] as const,
  categories: () => [...knowledgeKeys.all, 'categories'] as const,
  lists: () => [...knowledgeKeys.all, 'list'] as const,
  list: (query: ArticleQuery) => [...knowledgeKeys.lists(), query] as const,
  article: (slug: string) => [...knowledgeKeys.all, 'article', slug] as const,
};

export function useKnowledgeCategories() {
  return useQuery({
    queryKey: knowledgeKeys.categories(),
    queryFn: async () => (await api.get<KnowledgeCategory[]>('/knowledge-base/categories')).data,
  });
}

export function useArticles(query: ArticleQuery) {
  return useQuery({
    queryKey: knowledgeKeys.list(query),
    queryFn: async () =>
      (await api.get<PageResponse<ArticleListItem>>('/knowledge-base/articles', { params: cleanParams(query), paramsSerializer: serializeParams })).data,
    placeholderData: keepPreviousData,
  });
}

/** Opening a published article counts a view, so it is not refetched on window focus. */
export function useArticle(slug: string) {
  return useQuery({
    queryKey: knowledgeKeys.article(slug),
    queryFn: async () => (await api.get<ArticleDetail>(`/knowledge-base/articles/${encodeURIComponent(slug)}`)).data,
    enabled: slug !== '',
    refetchOnWindowFocus: false,
  });
}

function useArticleMutation<V>(mutationFn: (variables: V) => Promise<ArticleDetail>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (detail) => {
      queryClient.setQueryData(knowledgeKeys.article(detail.slug), detail);
      void queryClient.invalidateQueries({ queryKey: knowledgeKeys.lists() });
      void queryClient.invalidateQueries({ queryKey: knowledgeKeys.categories() });
    },
  });
}

export function useSaveArticle(id: number | null) {
  return useArticleMutation(async (input: SaveArticleInput) =>
    (id === null ? await api.post<ArticleDetail>('/knowledge-base/articles', input) : await api.put<ArticleDetail>(`/knowledge-base/articles/${id}`, input)).data,
  );
}

export function useArticleAttachments(id: number) {
  return {
    upload: useArticleMutation(async (file: File) => {
      const form = new FormData();
      form.append('file', file);
      return (await api.post<ArticleDetail>(`/knowledge-base/articles/${id}/attachments`, form)).data;
    }),
    remove: useArticleMutation(async (fileId: number) => (await api.delete<ArticleDetail>(`/knowledge-base/articles/${id}/attachments/${fileId}`)).data),
  };
}

export function downloadArticleAttachment(articleId: number, fileId: number, fileName: string): Promise<void> {
  return downloadFile(`/knowledge-base/articles/${articleId}/attachments/${fileId}`, fileName);
}
