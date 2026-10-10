import { BellRing, CheckCheck, CircleCheck, Eye, Info, Megaphone, Pencil, Plus, Trash2, TriangleAlert, type LucideIcon } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { Badge, type BadgeProps } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { errorMessage } from '@/lib/api/errors';
import { formatDateTime, formatRelative } from '@/lib/format';
import { cn } from '@/lib/utils';

import { useAcknowledgeAnnouncement, useAnnouncements, useDeleteAnnouncement, useMarkAnnouncementRead, type Announcement, type AnnouncementPriority, type AnnouncementState } from './api';
import { AnnouncementDialog } from './AnnouncementDialog';

const PRIORITY: Record<AnnouncementPriority, { label: string; tone: BadgeProps['tone']; icon: LucideIcon }> = {
  NORMAL: { label: 'Normal', tone: 'neutral', icon: Info },
  IMPORTANT: { label: 'Important', tone: 'warning', icon: BellRing },
  URGENT: { label: 'Urgent', tone: 'danger', icon: TriangleAlert },
};

const STATES: { id: AnnouncementState; label: string }[] = [
  { id: 'ACTIVE', label: 'Current' },
  { id: 'SCHEDULED', label: 'Scheduled' },
  { id: 'EXPIRED', label: 'Expired' },
];

async function attempt(action: Promise<unknown>, success?: string) {
  try {
    await action;
    if (success) toast.success(success);
  } catch (error) {
    toast.error(errorMessage(error));
  }
}

/** Company and department announcements, with read and acknowledgement tracking. */
export default function AnnouncementsPage() {
  const { user } = useAuth();
  const canManage = hasPermission(user, 'ANNOUNCEMENT_MANAGE');
  const [state, setState] = useState<AnnouncementState>('ACTIVE');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Announcement | null | undefined>(undefined);
  const announcements = useAnnouncements(state, page);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Announcements"
        description="News for the whole company and for your department."
        actions={
          canManage && (
            <Button onClick={() => setEditing(null)}>
              <Plus aria-hidden />
              New announcement
            </Button>
          )
        }
      />
      {canManage && (
        <div className="flex gap-1 border-b" role="tablist" aria-label="Announcement state">
          {STATES.map((s) => (
            <button
              key={s.id}
              type="button"
              role="tab"
              aria-selected={state === s.id}
              onClick={() => {
                setState(s.id);
                setPage(0);
              }}
              className={cn(
                '-mb-px border-b-2 px-3 py-2 text-sm font-medium focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none',
                state === s.id ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground',
              )}
            >
              {s.label}
            </button>
          ))}
        </div>
      )}
      {announcements.isPending ? (
        <div className="space-y-3" role="status" aria-label="Loading announcements">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-32 rounded-xl" />
          ))}
        </div>
      ) : announcements.isError ? (
        <Card>
          <ErrorState error={announcements.error} title="Couldn't load announcements" onRetry={() => void announcements.refetch()} />
        </Card>
      ) : announcements.data.content.length === 0 ? (
        <Card>
          <EmptyState icon={Megaphone} title={state === 'ACTIVE' ? 'No announcements right now' : `No ${state.toLowerCase()} announcements`} />
        </Card>
      ) : (
        <div className={cn('space-y-3', announcements.isPlaceholderData && 'opacity-60')}>
          {announcements.data.content.map((a) => (
            <AnnouncementCard key={a.id} announcement={a} onEdit={() => setEditing(a)} />
          ))}
          <Pagination {...announcements.data} onPageChange={setPage} />
        </div>
      )}
      <AnnouncementDialog open={editing !== undefined} announcement={editing ?? null} onOpenChange={(open) => !open && setEditing(undefined)} />
    </div>
  );
}

function AnnouncementCard({ announcement: a, onEdit }: { announcement: Announcement; onEdit: () => void }) {
  const markRead = useMarkAnnouncementRead();
  const acknowledge = useAcknowledgeAnnouncement();
  const remove = useDeleteAnnouncement();
  const priority = PRIORITY[a.priority];
  const live = a.state === 'ACTIVE';

  return (
    <Card className={cn('p-6', live && !a.read && 'border-primary/40')}>
      <article aria-labelledby={`announcement-${a.id}`}>
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <Badge tone={priority.tone}>
            <priority.icon aria-hidden />
            {priority.label}
          </Badge>
          {live && !a.read && (
            <Badge tone="primary">
              <span className="size-1.5 rounded-full bg-primary" aria-hidden />
              New
            </Badge>
          )}
          <span>{a.targetDepartment ? a.targetDepartment.name : 'Everyone'}</span>
          <span aria-hidden>·</span>
          <span title={formatDateTime(a.publishAt)}>{a.state === 'SCHEDULED' ? `Publishes ${formatDateTime(a.publishAt)}` : formatRelative(a.publishAt)}</span>
          {a.expiresAt && <span>· {a.state === 'EXPIRED' ? 'expired' : 'until'} {formatDateTime(a.expiresAt)}</span>}
          {a.createdBy && <span>· {a.createdBy.fullName}</span>}
        </div>
        <h2 id={`announcement-${a.id}`} className="mt-2 text-base font-semibold">
          {a.title}
        </h2>
        <p className="mt-1.5 text-sm whitespace-pre-wrap">{a.body}</p>
        <div className="mt-4 flex flex-wrap items-center gap-2">
          {live && !a.read && !a.ackRequired && (
            <Button size="sm" variant="outline" disabled={markRead.isPending} onClick={() => void attempt(markRead.mutateAsync(a.id))}>
              <Eye aria-hidden />
              Mark as read
            </Button>
          )}
          {live && a.ackRequired && !a.acknowledged && (
            <Button size="sm" disabled={acknowledge.isPending} onClick={() => void attempt(acknowledge.mutateAsync(a.id), 'Acknowledged')}>
              <CheckCheck aria-hidden />
              Acknowledge
            </Button>
          )}
          {a.ackRequired && a.acknowledged && (
            <span className="inline-flex items-center gap-1 text-sm text-status-success">
              <CircleCheck className="size-4" aria-hidden />
              You acknowledged this
            </span>
          )}
          {a.stats && (
            <span className="text-xs text-muted-foreground">
              Read by {a.stats.read} of {a.stats.audience}
              {a.ackRequired && ` · ${a.stats.acknowledged} acknowledged`}
            </span>
          )}
          {a.canManage && (
            <div className="ml-auto flex gap-1">
              <Button variant="ghost" size="sm" onClick={onEdit}>
                <Pencil aria-hidden />
                Edit
              </Button>
              <Button variant="ghost" size="sm" disabled={remove.isPending} onClick={() => void attempt(remove.mutateAsync(a.id), 'Announcement deleted')}>
                <Trash2 aria-hidden />
                Delete
              </Button>
            </div>
          )}
        </div>
      </article>
    </Card>
  );
}
