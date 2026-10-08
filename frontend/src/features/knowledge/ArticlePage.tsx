import { ArrowLeft, Download, Eye, FileText, LoaderCircle, Pencil, Trash2, Upload } from 'lucide-react';
import { useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { toast } from 'sonner';

import { ErrorState } from '@/components/common/ErrorState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { formatBytes } from '@/features/tasks/task-meta';
import { errorMessage } from '@/lib/api/errors';
import { formatDate, formatRelative } from '@/lib/format';
import { ACCEPTED_UPLOADS } from '@/lib/uploads';

import { downloadArticleAttachment, useArticle, useArticleAttachments, type ArticleDetail } from './api';
import { ArticleEditorDialog } from './ArticleEditorDialog';
import { Markdown } from './Markdown';

/** One knowledge base article, rendered from Markdown. */
export default function ArticlePage() {
  const slug = useParams().slug ?? '';
  const navigate = useNavigate();
  const article = useArticle(slug);
  const [editing, setEditing] = useState(false);

  if (article.isPending) {
    return (
      <div className="mx-auto max-w-3xl space-y-4" role="status" aria-label="Loading article">
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-96 rounded-xl" />
      </div>
    );
  }
  if (article.isError) {
    return (
      <Card className="mx-auto max-w-3xl">
        <ErrorState error={article.error} title="Couldn't open this article" onRetry={() => void article.refetch()} />
      </Card>
    );
  }
  const a = article.data;
  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <Link to="/knowledge-base" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden />
        Knowledge Base
      </Link>
      <header className="space-y-2">
        <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          <span>{a.category.name}</span>
          {a.status !== 'PUBLISHED' && <Badge tone={a.status === 'DRAFT' ? 'warning' : 'neutral'}>{a.status === 'DRAFT' ? 'Draft' : 'Archived'}</Badge>}
        </div>
        <div className="flex items-start justify-between gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">{a.title}</h1>
          {a.canEdit && (
            <Button variant="outline" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit
            </Button>
          )}
        </div>
        <p className="flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
          {a.author && <span>By {a.author.fullName}</span>}
          <span>· Updated {formatRelative(a.updatedAt)}</span>
          {a.publishedAt && <span>· Published {formatDate(a.publishedAt)}</span>}
          <span className="inline-flex items-center gap-1">
            · <Eye className="size-3.5" aria-hidden /> {a.viewCount} views
          </span>
        </p>
        {a.tags.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {a.tags.map((t) => (
              <Badge key={t} tone="neutral">
                #{t}
              </Badge>
            ))}
          </div>
        )}
      </header>
      <Card className="p-6">
        <Markdown>{a.body}</Markdown>
      </Card>
      <Attachments article={a} />
      <ArticleEditorDialog open={editing} article={a} onOpenChange={setEditing} onSaved={(saved) => saved.slug !== slug && void navigate(`/knowledge-base/${saved.slug}`)} />
    </div>
  );
}

function Attachments({ article }: { article: ArticleDetail }) {
  const attachments = useArticleAttachments(article.id);
  const input = useRef<HTMLInputElement>(null);
  if (article.attachments.length === 0 && !article.canEdit) return null;

  async function run(action: Promise<unknown>, success?: string) {
    try {
      await action;
      if (success) toast.success(success);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <section aria-labelledby="attachments-heading" className="space-y-3">
      <h2 id="attachments-heading" className="text-sm font-semibold">
        Attachments
      </h2>
      {article.attachments.length === 0 ? (
        <p className="text-sm text-muted-foreground">No files attached.</p>
      ) : (
        <ul className="divide-y rounded-lg border bg-card">
          {article.attachments.map((file) => (
            <li key={file.fileId} className="flex items-center gap-3 px-3 py-2">
              <FileText className="size-4 shrink-0 text-muted-foreground" aria-hidden />
              <span className="min-w-0 flex-1 truncate text-sm">
                {file.fileName} <span className="text-xs text-muted-foreground">· {formatBytes(file.sizeBytes)}</span>
              </span>
              <Button variant="ghost" size="icon" className="size-8" aria-label={`Download ${file.fileName}`} onClick={() => void run(downloadArticleAttachment(article.id, file.fileId, file.fileName))}>
                <Download />
              </Button>
              {article.canEdit && (
                <Button variant="ghost" size="icon" className="size-8" aria-label={`Remove ${file.fileName}`} onClick={() => void run(attachments.remove.mutateAsync(file.fileId))}>
                  <Trash2 />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {article.canEdit && (
        <>
          <input
            ref={input}
            type="file"
            className="hidden"
            accept={ACCEPTED_UPLOADS}
            onChange={(event) => {
              const file = event.target.files?.[0];
              event.target.value = '';
              if (file) void run(attachments.upload.mutateAsync(file), `${file.name} uploaded`);
            }}
          />
          <Button variant="outline" size="sm" disabled={attachments.upload.isPending} onClick={() => input.current?.click()}>
            {attachments.upload.isPending ? <LoaderCircle className="animate-spin" aria-hidden /> : <Upload aria-hidden />}
            Attach file
          </Button>
        </>
      )}
    </section>
  );
}
