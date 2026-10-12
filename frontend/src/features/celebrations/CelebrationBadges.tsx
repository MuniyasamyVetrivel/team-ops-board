import { CircleSlash } from 'lucide-react';
import { useEffect, useMemo } from 'react';

import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { cn, initials } from '@/lib/utils';

import { useEmployeePhoto, type Employee, type LinkedInPost, type MarketingEventStatus, type OccasionCategory } from './api';
import { EVENT_STATUS_STYLE, LINKEDIN_STYLE, OCCASION_STYLE } from './celebration-meta';

/** A calendar category as a pill: its icon, colour and label. */
export function OccasionBadge({ category, className }: { category: OccasionCategory; className?: string }) {
  const { icon: Icon, label, className: colours } = OCCASION_STYLE[category];
  return (
    <span className={cn('inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs leading-5 font-medium whitespace-nowrap', colours, className)}>
      <Icon className="size-3.5" aria-hidden />
      {label}
    </span>
  );
}

/** LinkedIn post status, or "No post needed". */
export function LinkedInBadge({ post }: { post: LinkedInPost }) {
  if (!post.needed) {
    return (
      <Badge tone="neutral">
        <CircleSlash aria-hidden />
        No post needed
      </Badge>
    );
  }
  const { icon: Icon, label, tone } = LINKEDIN_STYLE[post.status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {label}
    </Badge>
  );
}

export function EventStatusBadge({ status }: { status: MarketingEventStatus }) {
  const { icon: Icon, label, tone } = EVENT_STATUS_STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {label}
    </Badge>
  );
}

const SIZES = { md: 'size-8', lg: 'size-16 text-lg' } as const;

/** The employee's photo (fetched with the bearer token), or their initials. */
export function EmployeeAvatar({ employee, size = 'md' }: { employee: Pick<Employee, 'id' | 'fullName' | 'hasPhoto' | 'version'>; size?: keyof typeof SIZES }) {
  const photo = useEmployeePhoto(employee);
  const url = useMemo(() => (photo.data ? URL.createObjectURL(photo.data) : null), [photo.data]);
  useEffect(() => () => {
    if (url) URL.revokeObjectURL(url);
  }, [url]);
  return (
    <Avatar className={SIZES[size]}>
      {url && employee.hasPhoto && <AvatarImage src={url} alt="" />}
      <AvatarFallback>{initials(employee.fullName)}</AvatarFallback>
    </Avatar>
  );
}
