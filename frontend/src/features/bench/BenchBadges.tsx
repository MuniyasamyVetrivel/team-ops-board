import { Banknote, CircleSlash, TriangleAlert } from 'lucide-react';
import { Link } from 'react-router';

import { UserAvatar } from '@/components/common/UserAvatar';
import { Badge } from '@/components/ui/badge';
import { ToolErrorState } from '@/features/modules/ToolErrorState';
import type { UserSummary } from '@/lib/api/types';
import { cn } from '@/lib/utils';

import type { ConsultantStatus, PeriodStatus } from './api';
import { CONSULTANT_STATUS_META, consultantName, PERIOD_STATUS_META } from './bench-meta';
import { consultantPath } from './bench-paths';

export function ConsultantStatusBadge({ status }: { status: ConsultantStatus }) {
  const meta = CONSULTANT_STATUS_META[status];
  const Icon = meta.icon;
  return (
    <Badge tone={meta.tone}>
      <Icon aria-hidden />
      {meta.label}
    </Badge>
  );
}

export function PeriodStatusBadge({ status }: { status: PeriodStatus }) {
  const meta = PERIOD_STATUS_META[status];
  const Icon = meta.icon;
  return (
    <Badge tone={meta.tone}>
      <Icon aria-hidden />
      {meta.label}
    </Badge>
  );
}

export function BillableBadge({ billable }: { billable: boolean }) {
  return billable ? (
    <Badge tone="primary">
      <Banknote aria-hidden />
      Billable
    </Badge>
  ) : (
    <Badge tone="neutral">
      <CircleSlash aria-hidden />
      Non-billable
    </Badge>
  );
}

/** Today's total allocation as a number and a small bar; above 100% is flagged with an icon and the word "over". */
export function AllocationMeter({ percent, className }: { percent: number; className?: string }) {
  const over = percent > 100;
  return (
    <span className={cn('inline-flex min-w-28 items-center gap-2', className)}>
      <span role="meter" aria-label="Allocated today" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.min(percent, 100)} className="block h-1.5 w-14 shrink-0 overflow-hidden rounded-full bg-muted">
        <span className={cn('block h-full rounded-full', over ? 'bg-status-danger' : percent >= 100 ? 'bg-status-success' : 'bg-primary')} style={{ width: `${Math.min(percent, 100)}%` }} />
      </span>
      <span className={cn('text-sm tabular-nums', over && 'font-medium text-status-danger')}>
        {percent}%
        {over && (
          <span className="ml-1 inline-flex items-center gap-0.5 text-xs">
            <TriangleAlert className="size-3" aria-hidden />
            over
          </span>
        )}
      </span>
    </span>
  );
}

/** The first few skills as chips, then "+N". */
export function SkillChips({ skills, max = 3 }: { skills: string[]; max?: number }) {
  if (skills.length === 0) return <span className="text-sm text-muted-foreground">—</span>;
  const shown = skills.slice(0, max);
  const rest = skills.length - shown.length;
  return (
    <span className="flex flex-wrap gap-1" title={skills.join(', ')}>
      {shown.map((skill) => (
        <span key={skill} className="rounded-md border bg-muted/50 px-1.5 py-0.5 text-xs whitespace-nowrap">
          {skill}
        </span>
      ))}
      {rest > 0 && <span className="px-1 py-0.5 text-xs text-muted-foreground">+{rest}</span>}
    </span>
  );
}

/** Avatar, name (linking to the consultant's profile) and a secondary line. */
export function ConsultantCell({ consultantId, name, user, detail }: { consultantId: number; name: string | null | undefined; user?: UserSummary | null; detail?: string | null }) {
  const label = consultantName(name);
  return (
    <Link to={consultantPath(consultantId)} className="group flex min-w-0 items-center gap-3">
      <UserAvatar name={name ?? '?'} user={user} />
      <span className="min-w-0">
        <span className={cn('block truncate font-medium group-hover:underline', !name && 'text-muted-foreground italic')}>{label}</span>
        {detail && <span className="block truncate text-xs text-muted-foreground">{detail}</span>}
      </span>
    </Link>
  );
}

/** A load error; a module switched off since the page opened reads as that, not as a generic failure. */
export function BenchErrorState({ error, title, onRetry }: { error: unknown; title: string; onRetry?: () => void }) {
  return <ToolErrorState error={error} title={title} onRetry={onRetry} toolName="Bench Management" permission="BENCH_VIEW" />;
}
