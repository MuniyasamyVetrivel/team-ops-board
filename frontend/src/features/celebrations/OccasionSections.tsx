import { Share2 } from 'lucide-react';

import { AttachmentList } from '@/components/common/AttachmentList';
import { ErrorState } from '@/components/common/ErrorState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';

import { downloadOccasionFile, useOccasionFileMutations, useOccasionFiles, type LinkedInPost } from './api';
import { LinkedInBadge } from './CelebrationBadges';
import { formatShortDate } from './celebration-meta';

/** LinkedIn post tracking of one occurrence, with an edit button for editors. */
export function PostSection({ post, date, canEdit, onEdit }: { post: LinkedInPost; date: string; canEdit: boolean; onEdit: () => void }) {
  return (
    <section aria-labelledby="post-section" className="space-y-3 rounded-xl border p-4">
      <div className="flex items-center justify-between gap-3">
        <h3 id="post-section" className="flex items-center gap-2 text-sm font-semibold">
          <Share2 className="size-4 text-muted-foreground" aria-hidden />
          LinkedIn post for {formatShortDate(date)}
        </h3>
        <LinkedInBadge post={post} />
      </div>
      {post.needed && (
        <p className="text-sm text-muted-foreground">
          {post.owner ? `Owner: ${post.owner.fullName}` : 'No owner yet'}
          {post.postDate && ` · posting ${formatShortDate(post.postDate)}`}
          {post.postLink && (
            <>
              {' · '}
              <a href={post.postLink} target="_blank" rel="noreferrer" className="text-primary hover:underline">
                Open post
              </a>
            </>
          )}
        </p>
      )}
      {!post.saved && <Badge tone="neutral">Not decided yet</Badge>}
      {canEdit && (
        <div>
          <Button variant="outline" size="sm" onClick={onEdit}>
            Update LinkedIn post
          </Button>
        </div>
      )}
    </section>
  );
}

/** Files of an important day or event: download for everyone, upload and remove for editors. */
export function Files({ kind, id, canEdit }: { kind: 'important-days' | 'events'; id: number; canEdit: boolean }) {
  const files = useOccasionFiles(kind, id);
  const { upload, remove } = useOccasionFileMutations(kind, id);
  if (files.isError) return <ErrorState error={files.error} title="Couldn't load the files" onRetry={() => void files.refetch()} />;
  return (
    <AttachmentList
      files={files.data}
      canEdit={canEdit}
      uploading={upload.isPending}
      onUpload={(file) => upload.mutateAsync(file)}
      onRemove={(fileId) => remove.mutateAsync(fileId)}
      onDownload={(file) => downloadOccasionFile(kind, id, file.fileId, file.fileName)}
    />
  );
}
