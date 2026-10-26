import { CalendarDays } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { Skeleton } from '@/components/ui/skeleton';
import type { CalendarItem } from '@/features/calendar/api';
import { itemVisual } from '@/features/calendar/calendar-meta';
import { cn } from '@/lib/utils';

import { useCanSeeCelebrations } from './access';
import { useUpcoming } from './api';
import { LinkedInBadge } from './CelebrationBadges';
import { formatOccasionDate, formatTime, relativeDay } from './celebration-meta';
import { OccasionDrawer } from './OccasionDrawer';

export const COMING_UP_DAYS = 14;

/**
 * The dashboard's "Coming up in the next 14 days": birthdays, work anniversaries, important days and marketing events
 * (Digital Marketing users only), plus the team events the viewer's calendar shows. Celebrations open their drawer.
 * @param today the business "today" from the server, used when celebrations are not loaded
 */
export function ComingUp({ teamEvents = [], today: serverToday }: { teamEvents?: CalendarItem[]; today: string }) {
  const canSeeCelebrations = useCanSeeCelebrations();
  const upcoming = useUpcoming(COMING_UP_DAYS, canSeeCelebrations);
  const [openKey, setOpenKey] = useState<string | null>(null);

  if (canSeeCelebrations && upcoming.isPending) {
    return (
      <div className="space-y-2 p-6" role="status" aria-label="Loading what is coming up">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="h-12" />
        ))}
      </div>
    );
  }
  if (canSeeCelebrations && upcoming.isError) return <ErrorState error={upcoming.error} title="Couldn't load what is coming up" onRetry={() => void upcoming.refetch()} />;

  const items = [...(upcoming.data?.items ?? []), ...teamEvents.filter((item) => item.kind === 'EVENT')].sort(
    (a, b) => a.startDate.localeCompare(b.startDate) || (a.startAt ?? '').localeCompare(b.startAt ?? '') || a.title.localeCompare(b.title),
  );
  if (items.length === 0) {
    const description = canSeeCelebrations
      ? 'Birthdays, work anniversaries, important days and events for the next two weeks appear here.'
      : 'Team events for the next two weeks appear here.';
    return <EmptyState icon={CalendarDays} title="Nothing coming up" description={description} className="py-10" />;
  }
  const today = upcoming.data?.today ?? serverToday;
  // The open item is looked up again after a refresh, so its LinkedIn status stays current.
  const open = items.find((item) => item.key === openKey) ?? null;

  return (
    <>
      <ul className="divide-y divide-border/70">
        {items.map((item) => {
          const visual = itemVisual(item);
          const content = (
            <>
              <span className={cn('flex size-8 shrink-0 items-center justify-center rounded-lg border', visual.className)}>
                <visual.icon className="size-4" aria-hidden />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">{item.title}</span>
                <span className="block truncate text-xs text-muted-foreground">
                  {visual.label} · {relativeDay(item.startDate, today)}, {formatOccasionDate(item.startDate)}
                  {item.startAt && ` · ${formatTime(item.startAt)}`}
                  {item.occasion?.years != null && ` · ${item.occasion.detail}`}
                </span>
              </span>
              {item.occasion?.linkedIn?.needed && <LinkedInBadge post={item.occasion.linkedIn} />}
            </>
          );
          return (
            <li key={item.key}>
              {item.occasion ? (
                <button type="button" onClick={() => setOpenKey(item.key)} className="flex min-h-16 w-full items-center gap-3 px-6 py-3 text-left hover:bg-muted/50 focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none">
                  {content}
                </button>
              ) : (
                <div className="flex min-h-16 items-center gap-3 px-6 py-3">{content}</div>
              )}
            </li>
          );
        })}
      </ul>
      <OccasionDrawer item={open} onClose={() => setOpenKey(null)} />
    </>
  );
}
