import { ArrowLeft, CalendarCheck, CalendarClock, ClipboardList, History, ListChecks, Pencil, Repeat, type LucideIcon } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { Pagination } from '@/components/common/Pagination';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { DueBadge } from '@/features/tasks/TaskBadges';
import { parseLocalDate, PRIORITY_LABELS } from '@/features/tasks/task-meta';

import { useActivity, useActivityHistory } from './api';
import { FrequencyBadge, InactiveBadge } from './ActivityBadges';
import { ActivityFormDialog } from './ActivityFormDialog';
import { formatInstantDate, PERIOD_NAMES } from './activity-meta';
import { OccurrenceTable } from './OccurrenceTable';

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

/** One recurring activity: schedule, people, task template, checklist and the full occurrence history. */
export default function ActivityDetailPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const activity = useActivity(id);
  const [editing, setEditing] = useState(false);

  if (activity.isPending) {
    return (
      <div className="space-y-6" role="status" aria-label="Loading activity">
        <Skeleton className="h-10 w-1/2" />
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-24 rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-72 rounded-xl" />
      </div>
    );
  }
  if (activity.isError) {
    return (
      <div className="space-y-4">
        <BackLink />
        <Card>
          <ErrorState error={activity.error} title="Couldn't open this activity" onRetry={() => void activity.refetch()} />
        </Card>
      </div>
    );
  }

  const a = activity.data;
  const schedule = `Due ${a.dueOffsetDays === 0 ? 'on the first day' : `${a.dueOffsetDays} ${a.dueOffsetDays === 1 ? 'day' : 'days'} after the start`} of each ${PERIOD_NAMES[a.frequency]}`;

  return (
    <div className="space-y-6">
      <div className="space-y-3">
        <BackLink />
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
              <FrequencyBadge frequency={a.frequency} />
              {!a.active && <InactiveBadge />}
              <span>{a.department.name}</span>
            </div>
            <h1 className="mt-1 text-xl font-semibold tracking-tight sm:text-2xl">{a.name}</h1>
            {a.description && <p className="mt-1 max-w-3xl text-sm whitespace-pre-wrap text-muted-foreground">{a.description}</p>}
          </div>
          {a.permissions.canEdit && (
            <Button variant="outline" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit activity
            </Button>
          )}
        </div>
      </div>

      <section aria-label="Activity summary" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Next due" icon={CalendarClock}>
          {a.nextOccurrence ? (
            <>
              <DueBadge dueDate={a.nextOccurrence.dueDate} state={a.nextOccurrence.dueState} />
              <p className="mt-1 text-xs text-muted-foreground">{a.nextOccurrence.periodLabel}</p>
            </>
          ) : (
            <p className="text-sm text-muted-foreground">{a.active ? 'Starts with its next period' : 'Inactive: no new occurrences'}</p>
          )}
        </Stat>
        <Stat label="Last completed" icon={CalendarCheck}>
          <p className="text-lg font-semibold">{formatInstantDate(a.lastCompletedAt)}</p>
        </Stat>
        <Stat label="Schedule" icon={Repeat}>
          <p className="text-sm font-medium">
            From {dayFormat.format(parseLocalDate(a.startDate))}
            {a.endDate ? ` to ${dayFormat.format(parseLocalDate(a.endDate))}` : ', no end date'}
          </p>
          <p className="text-xs text-muted-foreground">{schedule}</p>
        </Stat>
        <Stat label="People" icon={ClipboardList}>
          <p className="text-sm">Owner: {a.owner?.fullName ?? 'none'}</p>
          <p className="text-sm">Assignee: {a.defaultAssignee?.fullName ?? 'the owner'}</p>
        </Stat>
      </section>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,22rem)]">
        <Card className="order-2 lg:order-1">
          <div className="border-b px-5 py-3.5">
            <h2 className="flex items-center gap-2 font-semibold">
              <History className="size-4 text-muted-foreground" aria-hidden />
              Occurrence history
            </h2>
            <p className="text-sm text-muted-foreground">{a.occurrenceCount} so far, newest first. Past occurrences are kept as they were.</p>
          </div>
          <OccurrenceHistory activityId={a.id} />
        </Card>
        <div className="order-1 space-y-6 lg:order-2">
          <Card>
            <div className="border-b px-5 py-3.5">
              <h2 className="font-semibold">Tasks</h2>
            </div>
            <div className="space-y-2 px-5 py-4 text-sm">
              {a.taskTitleTemplate ? (
                <>
                  <p>
                    Creates <span className="font-medium">“{a.taskTitleTemplate}”</span> each {PERIOD_NAMES[a.frequency]} ({PRIORITY_LABELS[a.taskPriority]} priority).
                  </p>
                  {a.nextTaskTitle && (
                    <p className="text-muted-foreground">
                      Next task: <span className="font-medium text-foreground">{a.nextTaskTitle}</span>
                    </p>
                  )}
                </>
              ) : (
                <p className="text-muted-foreground">No tasks: occurrences are completed or skipped here.</p>
              )}
            </div>
          </Card>
          <Card>
            <div className="border-b px-5 py-3.5">
              <h2 className="flex items-center gap-2 font-semibold">
                <ListChecks className="size-4 text-muted-foreground" aria-hidden />
                Checklist
              </h2>
            </div>
            {a.checklist.length === 0 ? (
              <p className="px-5 py-4 text-sm text-muted-foreground">No checklist.</p>
            ) : (
              <ol className="list-decimal space-y-1.5 py-4 pr-5 pl-10 text-sm" aria-label="Checklist">
                {a.checklist.map((item, index) => (
                  <li key={index}>{item}</li>
                ))}
              </ol>
            )}
          </Card>
        </div>
      </div>

      <ActivityFormDialog open={editing} onOpenChange={setEditing} activity={a} onDeleted={() => void navigate('/digital-marketing/activities', { replace: true })} />
    </div>
  );
}

function BackLink() {
  return (
    <Link to="/digital-marketing/activities" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
      <ArrowLeft className="size-4" aria-hidden />
      Marketing Activities
    </Link>
  );
}

function Stat({ label, icon: Icon, children }: { label: string; icon: LucideIcon; children: ReactNode }) {
  return (
    <div className="rounded-xl border bg-card p-4 shadow-xs">
      <span className="mb-2 flex items-center gap-2 text-sm text-muted-foreground">
        <Icon className="size-4" aria-hidden />
        {label}
      </span>
      {children}
    </div>
  );
}

function OccurrenceHistory({ activityId }: { activityId: number }) {
  const [page, setPage] = useState(0);
  const history = useActivityHistory(activityId, page);
  if (history.isPending) {
    return (
      <div className="space-y-3 p-4" role="status" aria-label="Loading history">
        {Array.from({ length: 4 }, (_, i) => (
          <Skeleton key={i} className="h-11" />
        ))}
      </div>
    );
  }
  if (history.isError) return <ErrorState error={history.error} title="Couldn't load the history" onRetry={() => void history.refetch()} />;
  if (history.data.content.length === 0) {
    return <EmptyState icon={History} title="No occurrences yet" description="The first one is created when its period starts." />;
  }
  return (
    <>
      <OccurrenceTable occurrences={history.data.content} dimmed={history.isPlaceholderData} />
      <Pagination {...history.data} onPageChange={setPage} />
    </>
  );
}
