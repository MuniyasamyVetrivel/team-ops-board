import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle } from 'lucide-react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';

import { FormBanner, FormField } from '@/components/common/FormField';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select } from '@/components/ui/select';
import { applyServerErrors } from '@/lib/api/form-errors';

import { LINKEDIN_STATUSES, useSaveLinkedInPost, type LinkedInPost, type OccasionKind } from './api';
import { formatOccasionDate, isHttpLink, LINKEDIN_STYLE } from './celebration-meta';
import { usePeople } from './use-people';

/** The occurrence whose post is tracked: a birthday or anniversary (employee), an important day or an event, on a date. */
export interface PostTarget {
  subjectType: OccasionKind;
  subjectId: number;
  occurrenceDate: string;
  title: string;
  post: LinkedInPost;
}

/** Mirrors CelebrationDtos.SaveLinkedInPost. */
const schema = z.object({
  postNeeded: z.boolean(),
  status: z.enum(['NOT_STARTED', 'DRAFTED', 'SCHEDULED', 'POSTED']),
  ownerId: z.string(),
  postDate: z.string(),
  postLink: z
    .string()
    .trim()
    .max(500, 'At most 500 characters')
    .refine((v) => v === '' || isHttpLink(v), 'Enter a full link (https://…)'),
});

type Values = z.infer<typeof schema>;

export function LinkedInPostDialog({ target, onClose }: { target: PostTarget | null; onClose: () => void }) {
  return (
    <Dialog open={target !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-lg">{target && <PostForm target={target} onDone={onClose} />}</DialogContent>
    </Dialog>
  );
}

function PostForm({ target, onDone }: { target: PostTarget; onDone: () => void }) {
  const save = useSaveLinkedInPost();
  const { people } = usePeople();
  const [banner, setBanner] = useState<string | null>(null);
  const { post } = target;
  const { register, control, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      postNeeded: post.needed,
      status: post.status,
      ownerId: post.owner ? String(post.owner.id) : '',
      postDate: post.postDate ?? '',
      postLink: post.postLink ?? '',
    },
  });
  const needed = useWatch({ control, name: 'postNeeded' });
  const owners = post.owner && !people.some((p) => p.id === post.owner?.id) ? [{ id: post.owner.id, fullName: post.owner.fullName }, ...people] : people;

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      await save.mutateAsync({
        subjectType: target.subjectType,
        subjectId: target.subjectId,
        occurrenceDate: target.occurrenceDate,
        postNeeded: values.postNeeded,
        ownerId: values.ownerId ? Number(values.ownerId) : null,
        status: values.status,
        postDate: values.postDate || null,
        postLink: values.postLink.trim() || null,
        version: post.saved ? post.version : null,
      });
      toast.success('LinkedIn post updated');
      onDone();
    } catch (error) {
      setBanner(applyServerErrors(error, setError, ['status', 'ownerId', 'postDate', 'postLink'] as const));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex min-h-0 flex-1 flex-col">
      <DialogHeader>
        <DialogTitle>LinkedIn post</DialogTitle>
        <DialogDescription>
          {target.title} · {formatOccasionDate(target.occurrenceDate)}
        </DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <FormBanner message={banner} />
        <div className="flex items-center gap-2">
          <Checkbox id="post-needed" {...register('postNeeded')} />
          <Label htmlFor="post-needed" className="font-normal">
            A LinkedIn post is needed
          </Label>
        </div>
        {needed && (
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField id="post-status" label="Status" required error={errors.status?.message}>
              <Select id="post-status" {...register('status')}>
                {LINKEDIN_STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {LINKEDIN_STYLE[s].label}
                  </option>
                ))}
              </Select>
            </FormField>
            <FormField id="post-owner" label="Post owner" error={errors.ownerId?.message}>
              <Select id="post-owner" {...register('ownerId')}>
                <option value="">No owner</option>
                {owners.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.fullName}
                  </option>
                ))}
              </Select>
            </FormField>
            <FormField id="post-date" label="Post date" error={errors.postDate?.message}>
              <Input id="post-date" type="date" {...register('postDate')} />
            </FormField>
            <FormField id="post-link" label="Post link" error={errors.postLink?.message}>
              <Input id="post-link" placeholder="https://www.linkedin.com/posts/…" aria-invalid={errors.postLink ? true : undefined} {...register('postLink')} />
            </FormField>
          </div>
        )}
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          Save post
        </Button>
      </DialogFooter>
    </form>
  );
}
