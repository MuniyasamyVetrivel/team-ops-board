import { ArrowUpRight, ExternalLink, Share2 } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';

import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import type { CalendarItem } from '@/features/calendar/api';

import type { OccasionInfo } from './api';
import { EventStatusBadge, LinkedInBadge, OccasionBadge } from './CelebrationBadges';
import { DAY_CATEGORY_LABELS, formatOccasionDate, formatShortDate, formatTime, occasionHref, REGION_LABELS } from './celebration-meta';
import { Detail, Muted } from './Details';
import { LinkedInPostDialog, type PostTarget } from './LinkedInPostDialog';

/**
 * A birthday, work anniversary, important day, marketing event or scheduled email campaign from the calendar or
 * "Coming up": its details and LinkedIn post status. CELEBRATIONS_EDIT holders can update the post here.
 */
export function OccasionDrawer({ item, onClose }: { item: CalendarItem | null; onClose: () => void }) {
  return (
    <Dialog open={item !== null && item.occasion !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="sm:max-w-md">{item?.occasion && <OccasionBody item={item} occasion={item.occasion} />}</SheetContent>
    </Dialog>
  );
}

function OccasionBody({ item, occasion }: { item: CalendarItem; occasion: OccasionInfo }) {
  const { user } = useAuth();
  const [posting, setPosting] = useState<PostTarget | null>(null);
  const canEdit = hasPermission(user, 'CELEBRATIONS_EDIT');
  const isCampaign = occasion.subjectType === null;
  const canOpen = !isCampaign || hasPermission(user, 'CAMPAIGN_VIEW');
  const linkedIn = occasion.linkedIn;

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <OccasionBadge category={occasion.category} className="self-start" />
        <DialogTitle>{item.title}</DialogTitle>
        <DialogDescription>
          {formatOccasionDate(item.startDate)}
          {item.startAt && ` · ${formatTime(item.startAt)}`}
          {item.endDate !== item.startDate && ` – ${formatShortDate(item.endDate)}`}
        </DialogDescription>
      </DialogHeader>
      <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 pb-6">
        <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
          {occasion.years !== null && <Detail label="Milestone">{occasion.detail}</Detail>}
          {occasion.category === 'BIRTHDAY' && occasion.detail && <Detail label="Designation">{occasion.detail}</Detail>}
          {occasion.region && <Detail label="Region">{REGION_LABELS[occasion.region]}</Detail>}
          {occasion.dayCategory && <Detail label="Category">{DAY_CATEGORY_LABELS[occasion.dayCategory]}</Detail>}
          {occasion.eventStatus && (
            <Detail label="Status">
              <EventStatusBadge status={occasion.eventStatus} />
            </Detail>
          )}
          {occasion.location && <Detail label="Location">{occasion.location}</Detail>}
          {isCampaign && occasion.detail && <Detail label="Audience">{occasion.detail}</Detail>}
          {item.department && <Detail label="Department">{item.department.name}</Detail>}
          {item.user && <Detail label={occasion.subjectType === 'EVENT' || isCampaign ? 'Owner' : 'App user'}>{<UserCell name={item.user.fullName} />}</Detail>}
        </dl>

        {linkedIn && occasion.subjectType && (
          <section aria-labelledby="occasion-linkedin" className="space-y-3 rounded-xl border p-4">
            <div className="flex items-center justify-between gap-3">
              <h3 id="occasion-linkedin" className="flex items-center gap-2 text-sm font-semibold">
                <Share2 className="size-4 text-muted-foreground" aria-hidden />
                LinkedIn post
              </h3>
              <LinkedInBadge post={linkedIn} />
            </div>
            {linkedIn.needed && (
              <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
                <Detail label="Owner">{linkedIn.owner?.fullName ?? <Muted>Not assigned</Muted>}</Detail>
                <Detail label="Post date">{formatShortDate(linkedIn.postDate) ?? <Muted>Not set</Muted>}</Detail>
                {linkedIn.postLink && (
                  <Detail label="Link">
                    <a href={linkedIn.postLink} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-primary hover:underline">
                      Open post
                      <ExternalLink className="size-3.5" aria-hidden />
                    </a>
                  </Detail>
                )}
              </dl>
            )}
            {canEdit && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPosting({ subjectType: occasion.subjectType!, subjectId: occasion.subjectId, occurrenceDate: item.startDate, title: item.title, post: linkedIn })}
              >
                Update LinkedIn post
              </Button>
            )}
          </section>
        )}

        {canOpen && (
          <Button asChild variant="ghost" size="sm" className="px-0 text-primary hover:bg-transparent hover:underline">
            <Link to={occasionHref(occasion)}>
              {isCampaign ? 'Open Email Campaigns' : occasion.subjectType === 'EVENT' ? 'Open the event' : occasion.subjectType === 'IMPORTANT_DAY' ? 'Open the important day' : 'Open the employee'}
              <ArrowUpRight aria-hidden />
            </Link>
          </Button>
        )}
      </div>
      <LinkedInPostDialog target={posting} onClose={() => setPosting(null)} />
    </div>
  );
}
