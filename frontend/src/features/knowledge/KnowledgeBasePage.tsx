import { BookOpen, Eye, Plus } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { formatRelative } from '@/lib/format';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { cn } from '@/lib/utils';

import { useArticles, useKnowledgeCategories, type ArticleStatus } from './api';
import { ArticleEditorDialog } from './ArticleEditorDialog';

const STATUS_LABELS: Record<ArticleStatus, string> = { DRAFT: 'Draft', PUBLISHED: 'Published', ARCHIVED: 'Archived' };

/** Searchable knowledge base: categories on the left, articles on the right. */
export default function KnowledgeBasePage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const canEdit = hasPermission(user, 'KB_EDIT');
  const categories = useKnowledgeCategories();
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<ArticleStatus | ''>(canEdit ? '' : 'PUBLISHED');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const debounced = useDebouncedValue(search.trim());
  const articles = useArticles({
    search: debounced,
    categoryId: categoryId ?? undefined,
    status: status ? [status] : undefined,
    sort: debounced ? undefined : 'updated,desc',
    page,
    size: 20,
  });
  const total = categories.data?.reduce((sum, c) => sum + c.articleCount, 0);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Knowledge Base"
        description="SOPs, how-tos and answers to common questions."
        actions={
          canEdit && (
            <Button onClick={() => setCreating(true)}>
              <Plus aria-hidden />
              New article
            </Button>
          )
        }
      />
      <div className="grid gap-6 lg:grid-cols-[14rem_1fr]">
        <nav aria-label="Categories" className="space-y-1">
          <CategoryButton label="All articles" count={total} active={categoryId === null} onClick={() => { setCategoryId(null); setPage(0); }} />
          {categories.isPending
            ? Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-8" />)
            : categories.data?.map((c) => (
                <CategoryButton key={c.id} label={c.name} count={c.articleCount} active={categoryId === c.id} onClick={() => { setCategoryId(c.id); setPage(0); }} />
              ))}
        </nav>
        <div className="space-y-4">
          <div className="flex flex-col gap-3 sm:flex-row">
            <SearchInput className="flex-1" placeholder="Search articles" aria-label="Search articles" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
            {canEdit && (
              <Select aria-label="Status" className="sm:w-44" value={status} onChange={(e) => { setStatus(e.target.value as ArticleStatus | ''); setPage(0); }}>
                <option value="">Any status</option>
                <option value="PUBLISHED">Published</option>
                <option value="DRAFT">Drafts</option>
                <option value="ARCHIVED">Archived</option>
              </Select>
            )}
          </div>
          {articles.isPending ? (
            <div className="space-y-3" role="status" aria-label="Loading articles">
              {Array.from({ length: 4 }, (_, i) => (
                <Skeleton key={i} className="h-24 rounded-xl" />
              ))}
            </div>
          ) : articles.isError ? (
            <Card>
              <ErrorState error={articles.error} title="Couldn't load articles" onRetry={() => void articles.refetch()} />
            </Card>
          ) : articles.data.content.length === 0 ? (
            <Card>
              <EmptyState icon={BookOpen} title={debounced ? `No articles match “${debounced}”` : 'No articles here yet'} description={debounced ? 'Try other words, or browse a category.' : undefined} />
            </Card>
          ) : (
            <div className={cn('space-y-3', articles.isPlaceholderData && 'opacity-60')}>
              {articles.data.content.map((a) => (
                <Card key={a.id} className="p-4 transition-colors hover:bg-muted/30">
                  <Link to={`/knowledge-base/${a.slug}`} className="block focus-visible:outline-none">
                    <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                      <span>{a.category.name}</span>
                      {a.status !== 'PUBLISHED' && <Badge tone={a.status === 'DRAFT' ? 'warning' : 'neutral'}>{STATUS_LABELS[a.status]}</Badge>}
                      <span aria-hidden>·</span>
                      <span>Updated {formatRelative(a.updatedAt)}</span>
                      <span className="inline-flex items-center gap-1">
                        <Eye className="size-3.5" aria-hidden />
                        {a.viewCount} views
                      </span>
                    </div>
                    <h2 className="mt-1 font-semibold text-foreground">{a.title}</h2>
                    <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">{a.excerpt}</p>
                  </Link>
                  {a.tags.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-1">
                      {a.tags.map((t) => (
                        <Badge key={t} tone="neutral">
                          #{t}
                        </Badge>
                      ))}
                    </div>
                  )}
                </Card>
              ))}
              <Pagination {...articles.data} onPageChange={setPage} />
            </div>
          )}
        </div>
      </div>
      <ArticleEditorDialog open={creating} article={null} onOpenChange={setCreating} onSaved={(a) => void navigate(`/knowledge-base/${a.slug}`)} />
    </div>
  );
}

function CategoryButton({ label, count, active, onClick }: { label: string; count: number | undefined; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-current={active ? 'true' : undefined}
      onClick={onClick}
      className={cn(
        'flex w-full items-center justify-between rounded-md px-3 py-1.5 text-left text-sm transition-colors focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none',
        active ? 'bg-accent font-medium text-accent-foreground' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
      )}
    >
      {label}
      {count !== undefined && <span className="text-xs tabular-nums">{count}</span>}
    </button>
  );
}
