import { CalendarHeart, Plus } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useIdParam } from '@/lib/use-id-param';
import { useInitialSearch } from '@/lib/use-initial-search';
import { cn } from '@/lib/utils';

import { EVENT_STATUSES, EVENT_TYPES, useMarketingEvents, useUpcoming, type MarketingEventStatus, type MarketingEventType } from './api';
import { EventStatusBadge, LinkedInBadge, OccasionBadge } from './CelebrationBadges';
import { EVENT_STATUS_STYLE, EVENT_TYPE_LABELS, formatOccasionDate, formatTime } from './celebration-meta';
import { EventFormDialog } from './EventFormDialog';
import { EventSheet } from './EventSheet';

type When = 'UPCOMING' | 'PAST' | 'ALL';

/** Upcoming events, webinars, event marketing and email marketing. */
export default function EventsPage() {
  const { user } = useAuth();
  const canEdit = hasPermission(user, 'CELEBRATIONS_EDIT');
  // The business date (never the browser's) decides what is upcoming.
  const today = useUpcoming().data?.today;
  const [openId, setOpenId] = useIdParam('event');
  const [adding, setAdding] = useState(false);
  const [search, setSearch] = useState(useInitialSearch());
  const [type, setType] = useState<MarketingEventType | ''>('');
  const [status, setStatus] = useState<MarketingEventStatus | ''>('');
  const [when, setWhen] = useState<When>('UPCOMING');
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(search.trim());

  const events = useMarketingEvents({
    search: debounced,
    type: type ? [type] : undefined,
    status: status ? [status] : undefined,
    from: when === 'UPCOMING' ? today : undefined,
    to: when === 'PAST' && today ? today : undefined,
    sort: when === 'PAST' ? 'start,desc' : 'start,asc',
    page,
    size: 25,
  });
  const filtered = Boolean(debounced || type || status);

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Events"
        description="Upcoming events, webinars, event marketing and email marketing, with their LinkedIn posts."
        actions={
          canEdit && (
            <Button onClick={() => setAdding(true)}>
              <Plus aria-hidden />
              New event
            </Button>
          )
        }
      />
      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-40 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
          <SearchInput placeholder="Search title or location" aria-label="Search events" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
          <Select aria-label="When" value={when} onChange={(e) => filter(setWhen)(e.target.value as When)}>
            <option value="UPCOMING">Upcoming</option>
            <option value="PAST">Past</option>
            <option value="ALL">Any time</option>
          </Select>
          <Select aria-label="Event type" value={type} onChange={(e) => filter(setType)(e.target.value as MarketingEventType | '')}>
            <option value="">All types</option>
            {EVENT_TYPES.map((t) => (
              <option key={t} value={t}>
                {EVENT_TYPE_LABELS[t]}
              </option>
            ))}
          </Select>
          <Select aria-label="Event status" value={status} onChange={(e) => filter(setStatus)(e.target.value as MarketingEventStatus | '')}>
            <option value="">All statuses</option>
            {EVENT_STATUSES.map((s) => (
              <option key={s} value={s}>
                {EVENT_STATUS_STYLE[s].label}
              </option>
            ))}
          </Select>
        </div>

        {events.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading events">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : events.isError ? (
          <ErrorState error={events.error} title="Couldn't load events" onRetry={() => void events.refetch()} />
        ) : events.data.content.length === 0 ? (
          <EmptyState
            icon={CalendarHeart}
            title={filtered ? 'No events match these filters' : when === 'UPCOMING' ? 'No upcoming events' : 'No events'}
            description={canEdit ? 'Add webinars, events and campaigns so they show on everyone’s calendar.' : undefined}
          />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Event</TableHead>
                  <TableHead>When</TableHead>
                  <TableHead>Owner</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>LinkedIn post</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={cn(events.isPlaceholderData && 'opacity-60')}>
                {events.data.content.map((e) => (
                  <TableRow key={e.id}>
                    <TableCell className="max-w-sm">
                      <button type="button" className="block max-w-full truncate text-left font-medium hover:underline" onClick={() => setOpenId(e.id)}>
                        {e.title}
                      </button>
                      <div className="mt-1 flex flex-wrap items-center gap-2">
                        <OccasionBadge category={e.category} />
                        {e.location && <span className="truncate text-xs text-muted-foreground">{e.location}</span>}
                      </div>
                    </TableCell>
                    <TableCell className="text-sm whitespace-nowrap">
                      {formatOccasionDate(e.startDate)}
                      <span className="block text-xs text-muted-foreground">{formatTime(e.startAt)}</span>
                    </TableCell>
                    <TableCell className="max-w-44">
                      <UserCell name={e.owner.fullName} />
                    </TableCell>
                    <TableCell>
                      <EventStatusBadge status={e.status} />
                    </TableCell>
                    <TableCell>{e.status === 'CANCELLED' ? <span className="text-sm text-muted-foreground">—</span> : <LinkedInBadge post={e.linkedIn} />}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination {...events.data} onPageChange={setPage} />
          </>
        )}
      </Card>
      <EventSheet eventId={openId} onClose={() => setOpenId(null)} />
      <EventFormDialog open={adding} onOpenChange={setAdding} onSaved={setOpenId} />
    </div>
  );
}
