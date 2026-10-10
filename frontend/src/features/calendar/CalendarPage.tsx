import { CalendarDays, ChevronLeft, ChevronRight, Plus } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { TaskDrawer } from '@/features/tasks/TaskDrawer';
import { parseLocalDate } from '@/features/tasks/task-meta';
import { useTaskParam } from '@/features/tasks/use-task-param';
import { cn } from '@/lib/utils';

import { useCalendar, type CalendarItem } from './api';
import { covers, daysBetween, shift, toIsoDate, viewRange, type CalendarView } from './calendar-dates';
import { itemVisual } from './calendar-meta';
import { EventDialog } from './EventDialog';

const VIEWS: { id: CalendarView; label: string }[] = [
  { id: 'MONTH', label: 'Month' },
  { id: 'WEEK', label: 'Week' },
  { id: 'AGENDA', label: 'Agenda' },
];

const monthTitle = new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' });
const shortDay = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' });
const longDay = new Intl.DateTimeFormat(undefined, { weekday: 'long', day: 'numeric', month: 'long' });
const weekday = new Intl.DateTimeFormat(undefined, { weekday: 'short' });
const time = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' });

/**
 * Team calendar (brief section 19): events, leave, task deadlines, project milestones and approval due dates, in
 * month, week and agenda views. "Today" is highlighted from the server's business date.
 */
export default function CalendarPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [view, setView] = useState<CalendarView>('MONTH');
  // Navigation starts from the browser's date; the highlighted "today" comes from the server.
  const [anchor, setAnchor] = useState(() => toIsoDate(new Date()));
  const [mine, setMine] = useState(true);
  const [taskId, setTaskId] = useTaskParam();
  const [eventTarget, setEventTarget] = useState<{ eventId: number } | { day: string } | null>(null);
  const range = viewRange(view, anchor);
  const calendar = useCalendar({ ...range, mine });
  const canEdit = hasPermission(user, 'CALENDAR_EDIT');
  const today = calendar.data?.today;

  function open(item: CalendarItem) {
    switch (item.kind) {
      case 'TASK_DEADLINE':
        setTaskId(item.id);
        break;
      case 'MILESTONE':
        if (item.parentId !== null) void navigate(`/projects/${item.parentId}`);
        break;
      case 'APPROVAL_DUE':
        void navigate(`/approvals?approval=${item.id}`);
        break;
      default:
        setEventTarget({ eventId: item.id });
    }
  }

  const title =
    view === 'MONTH'
      ? monthTitle.format(parseLocalDate(anchor))
      : `${shortDay.format(parseLocalDate(range.from))} – ${shortDay.format(parseLocalDate(range.to))}`;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Calendar"
        description="Deadlines, milestones, approvals, events and leave."
        actions={
          canEdit && (
            <Button onClick={() => setEventTarget({ day: today ?? anchor })}>
              <Plus aria-hidden />
              New event
            </Button>
          )
        }
      />
      <Card>
        <div className="flex flex-wrap items-center gap-3 border-b px-6 py-4">
          <div className="flex items-center gap-1">
            <Button variant="outline" size="icon" aria-label="Previous" onClick={() => setAnchor(shift(view, anchor, -1))}>
              <ChevronLeft />
            </Button>
            <Button variant="outline" size="sm" onClick={() => setAnchor(today ?? toIsoDate(new Date()))}>
              Today
            </Button>
            <Button variant="outline" size="icon" aria-label="Next" onClick={() => setAnchor(shift(view, anchor, 1))}>
              <ChevronRight />
            </Button>
          </div>
          <h2 className="text-base font-semibold" aria-live="polite">
            {title}
          </h2>
          <div className="ml-auto flex flex-wrap items-center gap-4">
            <div className="flex items-center gap-2">
              <Checkbox id="only-mine" checked={mine} onChange={(e) => setMine(e.target.checked)} />
              <Label htmlFor="only-mine" className="font-normal">
                Only my tasks
              </Label>
            </div>
            <div className="flex rounded-lg bg-muted p-1" role="group" aria-label="Calendar view">
              {VIEWS.map((v) => (
                <Button key={v.id} size="sm" variant="ghost" className={cn('h-8', view === v.id ? 'bg-card text-foreground shadow-xs hover:bg-card' : 'text-muted-foreground')} aria-pressed={view === v.id} onClick={() => setView(v.id)}>
                  {v.label}
                </Button>
              ))}
            </div>
          </div>
        </div>
        {calendar.isError ? (
          <ErrorState error={calendar.error} title="Couldn't load the calendar" onRetry={() => void calendar.refetch()} />
        ) : calendar.isPending ? (
          <div className="p-4" role="status" aria-label="Loading calendar">
            <Skeleton className="h-[28rem]" />
          </div>
        ) : view === 'AGENDA' ? (
          <Agenda items={calendar.data.items} from={range.from} to={range.to} today={today} onOpen={open} />
        ) : (
          <Grid view={view} items={calendar.data.items} from={range.from} to={range.to} anchor={anchor} today={today} onOpen={open} onDay={canEdit ? (day) => setEventTarget({ day }) : undefined} />
        )}
      </Card>
      <TaskDrawer taskId={taskId} onClose={() => setTaskId(null)} />
      <EventDialog target={eventTarget} onClose={() => setEventTarget(null)} />
    </div>
  );
}

function ItemChip({ item, onOpen, compact }: { item: CalendarItem; onOpen: (item: CalendarItem) => void; compact?: boolean }) {
  const visual = itemVisual(item);
  return (
    <button
      type="button"
      onClick={(event) => {
        event.stopPropagation();
        onOpen(item);
      }}
      className={cn(
        'flex w-full min-w-0 items-center gap-1 rounded border px-1.5 py-0.5 text-left text-xs transition-opacity hover:opacity-80 focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none',
        visual.className,
      )}
      title={`${visual.label}: ${item.title}`}
      aria-label={`${visual.label}: ${item.title}`}
    >
      <visual.icon className="size-3 shrink-0" aria-hidden />
      {!compact && item.startAt && <span className="shrink-0 tabular-nums">{time.format(new Date(item.startAt))}</span>}
      <span className="truncate">{item.title}</span>
    </button>
  );
}

const MAX_PER_DAY = 3;

function Grid({ view, items, from, to, anchor, today, onOpen, onDay }: {
  view: CalendarView;
  items: CalendarItem[];
  from: string;
  to: string;
  anchor: string;
  today?: string;
  onOpen: (item: CalendarItem) => void;
  onDay?: (day: string) => void;
}) {
  const days = daysBetween(from, to);
  const month = parseLocalDate(anchor).getMonth();
  const [expanded, setExpanded] = useState<string | null>(null);
  return (
    <div>
      <div className="grid grid-cols-7 border-b text-[11px] font-semibold tracking-[0.06em] text-muted-foreground uppercase" aria-hidden>
        {days.slice(0, 7).map((day) => (
          <div key={day} className="px-3 py-2.5">
            {weekday.format(parseLocalDate(day))}
          </div>
        ))}
      </div>
      <div className="grid grid-cols-7">
        {days.map((day) => {
          const dayItems = items.filter((item) => covers(item.startDate, item.endDate, day));
          const show = expanded === day || view === 'WEEK' ? dayItems : dayItems.slice(0, MAX_PER_DAY);
          const outside = view === 'MONTH' && parseLocalDate(day).getMonth() !== month;
          return (
            <div
              key={day}
              className={cn('group min-h-28 border-r border-b p-1.5 [&:nth-child(7n)]:border-r-0', view === 'WEEK' && 'min-h-64', outside && 'bg-muted/30')}
            >
              <div className="mb-1 flex items-center justify-between">
                <span
                  className={cn('flex size-6 items-center justify-center rounded-full text-xs tabular-nums', outside ? 'text-muted-foreground' : 'font-medium', day === today && 'bg-primary text-primary-foreground')}
                  aria-label={`${longDay.format(parseLocalDate(day))}${day === today ? ', today' : ''}, ${dayItems.length} ${dayItems.length === 1 ? 'item' : 'items'}`}
                >
                  {parseLocalDate(day).getDate()}
                </span>
                {onDay && (
                  <button type="button" className="rounded p-0.5 text-muted-foreground opacity-0 group-hover:opacity-100 hover:bg-muted hover:text-foreground focus-visible:opacity-100" aria-label={`Add event on ${longDay.format(parseLocalDate(day))}`} onClick={() => onDay(day)}>
                    <Plus className="size-3.5" />
                  </button>
                )}
              </div>
              <div className="space-y-1">
                {show.map((item) => (
                  <ItemChip key={item.key} item={item} onOpen={onOpen} compact={view === 'MONTH'} />
                ))}
                {show.length < dayItems.length && (
                  <button type="button" className="text-xs font-medium text-primary hover:underline" onClick={() => setExpanded(day)}>
                    +{dayItems.length - show.length} more
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function Agenda({ items, from, to, today, onOpen }: { items: CalendarItem[]; from: string; to: string; today?: string; onOpen: (item: CalendarItem) => void }) {
  const days = daysBetween(from, to)
    .map((day) => ({ day, items: items.filter((item) => covers(item.startDate, item.endDate, day)) }))
    .filter((d) => d.items.length > 0);
  if (days.length === 0) return <EmptyState icon={CalendarDays} title="Nothing scheduled in the next 30 days" />;
  return (
    <ol className="divide-y">
      {days.map(({ day, items: dayItems }) => (
        <li key={day} className="grid gap-2 px-4 py-3 sm:grid-cols-[12rem_1fr]">
          <p className={cn('text-sm font-medium', day === today && 'text-primary')}>
            {longDay.format(parseLocalDate(day))}
            {day === today && <span className="ml-2 text-xs">Today</span>}
          </p>
          <ul className="space-y-1.5">
            {dayItems.map((item) => {
              const visual = itemVisual(item);
              return (
                <li key={item.key}>
                  <button type="button" onClick={() => onOpen(item)} className="flex w-full items-center gap-3 rounded-md px-2 py-1.5 text-left hover:bg-muted focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none">
                    <span className={cn('flex size-7 shrink-0 items-center justify-center rounded border', visual.className)}>
                      <visual.icon className="size-3.5" aria-hidden />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">{item.title}</span>
                      <span className="block truncate text-xs text-muted-foreground">
                        {visual.label}
                        {item.reference && ` · ${item.reference}`}
                        {item.startAt && ` · ${time.format(new Date(item.startAt))}`}
                        {item.department && ` · ${item.department.name}`}
                        {item.kind === 'TASK_DEADLINE' && item.user && ` · ${item.user.fullName}`}
                      </span>
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </li>
      ))}
    </ol>
  );
}
