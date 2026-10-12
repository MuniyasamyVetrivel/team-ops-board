import { ExternalLink, Pencil, Share2 } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { addDays } from '@/features/calendar/calendar-dates';
import { cn } from '@/lib/utils';

import { LINKEDIN_STATUSES, useLinkedInPosts, useUpcoming, type LinkedInPostRow, type LinkedInPostStatus, type OccasionKind } from './api';
import { LinkedInBadge, OccasionBadge } from './CelebrationBadges';
import { formatOccasionDate, formatShortDate, KIND_LABELS, LINKEDIN_STYLE, occasionHref, REGION_LABELS, relativeDay } from './celebration-meta';
import { LinkedInPostDialog, type PostTarget } from './LinkedInPostDialog';

const WINDOWS = [14, 30, 60, 90] as const;
const KINDS: OccasionKind[] = ['BIRTHDAY', 'WORK_ANNIVERSARY', 'IMPORTANT_DAY', 'EVENT'];

type Needed = 'NEEDED' | 'NOT_NEEDED' | 'ALL';

/**
 * Everything coming up (birthdays, anniversaries, important days, events) with its LinkedIn post status, so nothing is
 * missed. Everyone can look; CELEBRATIONS_EDIT holders update the posts.
 */
export default function LinkedInPostsPage() {
  const { user } = useAuth();
  const canEdit = hasPermission(user, 'CELEBRATIONS_EDIT');
  const today = useUpcoming().data?.today;
  const [days, setDays] = useState<(typeof WINDOWS)[number]>(30);
  const [kind, setKind] = useState<OccasionKind | ''>('');
  const [status, setStatus] = useState<LinkedInPostStatus | ''>('');
  const [needed, setNeeded] = useState<Needed>('NEEDED');
  const [page, setPage] = useState(0);
  const [posting, setPosting] = useState<PostTarget | null>(null);

  const posts = useLinkedInPosts({
    from: today,
    to: today ? addDays(today, days - 1) : undefined,
    kind: kind ? [kind] : undefined,
    status: status ? [status] : undefined,
    needed: needed === 'ALL' ? undefined : needed === 'NEEDED',
    page,
    size: 50,
  });

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  function edit(row: LinkedInPostRow) {
    setPosting({ subjectType: row.subjectType, subjectId: row.subjectId, occurrenceDate: row.occurrenceDate, title: row.title, post: row.linkedIn });
  }

  return (
    <div className="space-y-6">
      <PageHeader title="LinkedIn posts" description="Every birthday, anniversary, important day and event coming up, with its post status." />
      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-44">
          <Select aria-label="Coming up within" value={days} onChange={(e) => filter(setDays)(Number(e.target.value) as (typeof WINDOWS)[number])}>
            {WINDOWS.map((d) => (
              <option key={d} value={d}>
                Next {d} days
              </option>
            ))}
          </Select>
          <Select aria-label="Post needed" value={needed} onChange={(e) => filter(setNeeded)(e.target.value as Needed)}>
            <option value="NEEDED">Post needed</option>
            <option value="NOT_NEEDED">No post needed</option>
            <option value="ALL">Everything</option>
          </Select>
          <Select aria-label="Post status" value={status} onChange={(e) => filter(setStatus)(e.target.value as LinkedInPostStatus | '')}>
            <option value="">All statuses</option>
            {LINKEDIN_STATUSES.map((s) => (
              <option key={s} value={s}>
                {LINKEDIN_STYLE[s].label}
              </option>
            ))}
          </Select>
          <Select aria-label="Kind" value={kind} onChange={(e) => filter(setKind)(e.target.value as OccasionKind | '')}>
            <option value="">All kinds</option>
            {KINDS.map((k) => (
              <option key={k} value={k}>
                {KIND_LABELS[k]}
              </option>
            ))}
          </Select>
        </div>

        {posts.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading LinkedIn posts">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : posts.isError ? (
          <ErrorState error={posts.error} title="Couldn't load LinkedIn posts" onRetry={() => void posts.refetch()} />
        ) : posts.data.content.length === 0 ? (
          <EmptyState icon={Share2} title="Nothing to post in this period" description="Birthdays, anniversaries, important days and events that need a post appear here." />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Occasion</TableHead>
                  <TableHead>Post</TableHead>
                  <TableHead>Owner</TableHead>
                  <TableHead>Post date</TableHead>
                  {canEdit && (
                    <TableHead>
                      <span className="sr-only">Actions</span>
                    </TableHead>
                  )}
                </TableRow>
              </TableHeader>
              <TableBody className={cn(posts.isPlaceholderData && 'opacity-60')}>
                {posts.data.content.map((row) => (
                  <TableRow key={row.key}>
                    <TableCell className="text-sm whitespace-nowrap">
                      {formatOccasionDate(row.occurrenceDate)}
                      {today && <span className="block text-xs text-muted-foreground">{relativeDay(row.occurrenceDate, today)}</span>}
                    </TableCell>
                    <TableCell className="max-w-sm">
                      <Link to={occasionHref({ subjectType: row.subjectType, subjectId: row.subjectId })} className="block truncate font-medium hover:underline">
                        {row.title}
                      </Link>
                      <div className="mt-1 flex flex-wrap items-center gap-2">
                        <OccasionBadge category={row.category} />
                        {row.region && <span className="text-xs text-muted-foreground">{REGION_LABELS[row.region]}</span>}
                      </div>
                    </TableCell>
                    <TableCell>
                      <LinkedInBadge post={row.linkedIn} />
                    </TableCell>
                    <TableCell className="text-sm">{row.linkedIn.owner?.fullName ?? <span className="text-muted-foreground">—</span>}</TableCell>
                    <TableCell className="text-sm whitespace-nowrap">
                      {formatShortDate(row.linkedIn.postDate) ?? <span className="text-muted-foreground">—</span>}
                      {row.linkedIn.postLink && (
                        <a href={row.linkedIn.postLink} target="_blank" rel="noreferrer" className="ml-2 inline-flex items-center text-primary" aria-label={`Open the post for ${row.title}`}>
                          <ExternalLink className="size-3.5" aria-hidden />
                        </a>
                      )}
                    </TableCell>
                    {canEdit && (
                      <TableCell className="text-right">
                        <Button variant="ghost" size="sm" onClick={() => edit(row)} aria-label={`Update the post for ${row.title}`}>
                          <Pencil aria-hidden />
                          Update
                        </Button>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination {...posts.data} onPageChange={setPage} />
          </>
        )}
      </Card>
      <LinkedInPostDialog target={posting} onClose={() => setPosting(null)} />
    </div>
  );
}
