import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { applyServerErrors } from '@/lib/api/form-errors';
import { cn } from '@/lib/utils';

import { useKnowledgeCategories, useSaveArticle, type ArticleDetail } from './api';
import { Markdown } from './Markdown';

/** Mirrors KnowledgeDtos.SaveArticle. */
const articleSchema = z.object({
  title: z.string().trim().min(1, 'Title is required').max(250),
  body: z.string().trim().min(1, 'Content is required').max(200000, 'The article is too long'),
  categoryId: z.string().min(1, 'Category is required'),
  tags: z.string().refine((v) => v.split(',').filter((t) => t.trim()).length <= 10, 'At most 10 tags'),
  status: z.enum(['DRAFT', 'PUBLISHED', 'ARCHIVED']),
});

type ArticleValues = z.infer<typeof articleSchema>;

const SERVER_FIELDS = ['title', 'body', 'categoryId', 'tags'] as const;

export function ArticleEditorDialog({ open, article, onOpenChange, onSaved }: { open: boolean; article: ArticleDetail | null; onOpenChange: (open: boolean) => void; onSaved?: (article: ArticleDetail) => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        {open && (
          <ArticleForm
            article={article}
            onDone={() => onOpenChange(false)}
            onSaved={(saved) => {
              onOpenChange(false);
              onSaved?.(saved);
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function ArticleForm({ article, onDone, onSaved }: { article: ArticleDetail | null; onDone: () => void; onSaved: (article: ArticleDetail) => void }) {
  const categories = useKnowledgeCategories();
  const save = useSaveArticle(article?.id ?? null);
  const [banner, setBanner] = useState<string | null>(null);
  const [preview, setPreview] = useState(false);
  const { register, handleSubmit, control, setError, formState: { errors, isSubmitting } } = useForm<ArticleValues>({
    resolver: zodResolver(articleSchema),
    defaultValues: {
      title: article?.title ?? '',
      body: article?.body ?? '',
      categoryId: article ? String(article.category.id) : '',
      tags: article?.tags.join(', ') ?? '',
      status: article?.status ?? 'DRAFT',
    },
  });
  const body = useWatch({ control, name: 'body' });

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      const saved = await save.mutateAsync({
        version: article?.version,
        title: values.title,
        body: values.body,
        categoryId: Number(values.categoryId),
        departmentId: article?.department?.id ?? null,
        tags: values.tags.split(',').map((t) => t.trim()).filter(Boolean),
        status: values.status,
      });
      toast.success(saved.status === 'PUBLISHED' ? 'Article published' : 'Article saved');
      onSaved(saved);
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>{article ? 'Edit article' : 'New article'}</DialogTitle>
        <DialogDescription>Write in Markdown. Drafts are only visible to editors.</DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <FormField id="title" label="Title" required error={errors.title?.message}>
          <Input id="title" autoFocus aria-invalid={errors.title ? true : undefined} {...register('title')} />
        </FormField>
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField id="categoryId" label="Category" required error={errors.categoryId?.message}>
            <Select id="categoryId" aria-invalid={errors.categoryId ? true : undefined} {...register('categoryId')}>
              <option value="">Choose…</option>
              {categories.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="status" label="Status">
            <Select id="status" {...register('status')}>
              <option value="DRAFT">Draft</option>
              <option value="PUBLISHED">Published</option>
              <option value="ARCHIVED">Archived</option>
            </Select>
          </FormField>
          <FormField id="tags" label="Tags" hint="Comma separated" error={errors.tags?.message}>
            <Input id="tags" placeholder="vpn, onboarding" {...register('tags')} />
          </FormField>
        </div>
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <label htmlFor="body" className="text-sm font-medium">
              Content<span className="text-destructive" aria-hidden>*</span>
            </label>
            <div className="flex gap-1" role="group" aria-label="Editor mode">
              <Button type="button" size="sm" variant={preview ? 'ghost' : 'secondary'} aria-pressed={!preview} onClick={() => setPreview(false)}>
                Write
              </Button>
              <Button type="button" size="sm" variant={preview ? 'secondary' : 'ghost'} aria-pressed={preview} onClick={() => setPreview(true)}>
                Preview
              </Button>
            </div>
          </div>
          <Textarea id="body" rows={14} className={cn('font-mono text-sm', preview && 'hidden')} aria-invalid={errors.body ? true : undefined} {...register('body')} />
          {preview && <div className="min-h-64 rounded-md border p-4">{body?.trim() ? <Markdown>{body}</Markdown> : <p className="text-sm text-muted-foreground">Nothing to preview.</p>}</div>}
          {errors.body && <p className="text-sm text-destructive">{errors.body.message}</p>}
        </div>
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          Save article
        </Button>
      </DialogFooter>
    </form>
  );
}
