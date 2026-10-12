import { CalendarClock, CircleAlert, CircleCheck, Clock, History, ListTodo, LoaderCircle, Ticket, Timer, type LucideIcon } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useLocation } from 'react-router';
import { toast } from 'sonner';

import { BarGraph } from '@/components/charts/BarGraph';
import { ChartTooltip, ChartTooltipRow } from '@/components/charts/ChartTooltip';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { FilterTab, FilterTabs } from '@/components/common/FilterTab';
import { KpiTile } from '@/components/common/KpiCard';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { addDays } from '@/features/calendar/calendar-dates';
import { useProjects } from '@/features/projects/api';
import { TaskDrawer } from '@/features/tasks/TaskDrawer';
import { parseLocalDate, PRIORITY_LABELS, STATUS_LABELS } from '@/features/tasks/task-meta';
import { PRIORITIES } from '@/features/tasks/types';
import { useTaskParam } from '@/features/tasks/use-task-param';
import { useTicketCategories } from '@/features/tickets/api';
import { TICKET_STATUS_LABELS } from '@/features/tickets/ticket-meta';
import { TicketDrawer } from '@/features/tickets/TicketDrawer';
import type { TicketStatus } from '@/features/tickets/types';
import { useTicketParam } from '@/features/tickets/use-ticket-param';
import { errorMessage } from '@/lib/api/errors';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { cn } from '@/lib/utils';

import { toWorkQuery, useWorkHistoryParams, type WorkHistoryParams, type WorkRange } from './use-work-history-params';
import { exportWork, useWorkList, useWorkSummary, type TaskHistoryRow, type TicketHistoryRow, type WorkDateField, type WorkKind, type WorkSummary } from './work-history-api';
import { TaskHistoryTable, TicketHistoryTable } from './WorkHistoryTables';

/** Status tabs, in the order people scan them; OVERDUE is virtual (open and past due). */
const TASK_TABS = ['ALL', 'TODO', 'IN_PROGRESS', 'BLOCKED', 'IN_REVIEW', 'OVERDUE', 'COMPLETED'] as const;
const TICKET_TABS = ['ALL', 'NEW', 'OPEN', 'IN_PROGRESS', 'WAITING_FOR_REQUESTER', 'OVERDUE', 'RESOLVED', 'CLOSED'] as const;

const DATE_FIELDS: Record<WorkKind, { value: WorkDateField; label: string }[]> = {
  tasks: [
    { value: 'COMPLETED', label: 'Completed date' },
    { value: 'DUE', label: 'Due date' },
    { value: 'START', label: 'Start date' },
    { value: 'CREATED', label: 'Created date' },
  ],
  tickets: [
    { value: 'COMPLETED', label: 'Resolved date' },
    { value: 'DUE', label: 'Resolution due' },
    { value: 'CREATED', label: 'Created date' },
  ],
};

const RANGES: { value: WorkRange; label: string }[] = [
  { value: '', label: 'Any time' },
  { value: 'THIS_WEEK', label: 'This week' },
  { value: 'LAST_WEEK', label: 'Last week' },
  { value: 'THIS_MONTH', label: 'This month' },
  { value: 'LAST_MONTH', label: 'Last month' },
  { value: 'LAST_30_DAYS', label: 'Last 30 days' },
  { value: 'LAST_3_MONTHS', label: 'Last 3 months' },
  { value: 'CUSTOM', label: 'Custom range' },
];

const SORTS: Record<WorkKind, { value: string; label: string }[]> = {
  tasks: [
    { value: 'updated,desc', label: 'Recently updated' },
    { value: 'due,asc', label: 'Due date (earliest)' },
    { value: 'due,desc', label: 'Due date (latest)' },
    { value: 'completed,desc', label: 'Recently completed' },
    { value: 'priority,desc', label: 'Priority (urgent first)' },
  ],
  tickets: [
    { value: 'updated,desc', label: 'Recently updated' },
    { value: 'due,asc', label: 'Resolution due (earliest)' },
    { value: 'completed,desc', label: 'Recently resolved' },
    { value: 'priority,desc', label: 'Priority (urgent first)' },
  ],
};

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' });
const yearFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

function tabLabel(kind: WorkKind, status: string): string {
  if (status === 'ALL') return 'All';
  if (status === 'OVERDUE') return 'Overdue';
  return kind === 'tasks' ? STATUS_LABELS[status as keyof typeof STATUS_LABELS] : TICKET_STATUS_LABELS[status as TicketStatus];
}

function tabCount(summary: WorkSummary | undefined, status: string): number | null {
  if (!summary) return null;
  if (status === 'ALL') return summary.total;
  if (status === 'OVERDUE') return summary.overdue;
  return summary.byStatus[status] ?? 0;
}

/**
 * A team member's work history (tasks and tickets): status tabs with counts, filters kept in the URL, a summary strip
 * and a table whose rows open the task or ticket drawer. The server decides whose work the viewer may see.
 */
export function WorkHistory({ userId, fullName, firstName }: { userId: number; fullName: string; firstName: string }) {
  const { user } = useAuth();
  const location = useLocation();
  const [params, update] = useWorkHistoryParams();
  const canSeeTickets = hasPermission(user, 'TICKET_VIEW');
  const kind: WorkKind = canSeeTickets ? params.kind : 'tasks';

  // Arriving from a link such as the Workload page scrolls straight to the history.
  useEffect(() => {
    if (location.hash === '#work-history') document.getElementById('work-history')?.scrollIntoView?.({ block: 'start' });
  }, [location.hash]);

  return (
    <section id="work-history" aria-labelledby="work-history-title" className="scroll-mt-20 space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="work-history-title" className="flex items-center gap-2 text-lg font-semibold">
            <History className="size-5 text-muted-foreground" aria-hidden />
            Work history
          </h2>
          <p className="text-sm text-muted-foreground">Everything assigned to {firstName}, filtered by status, dates, priority and more.</p>
        </div>
        {canSeeTickets && (
          <div className="flex rounded-lg bg-muted p-1" role="group" aria-label="Work type">
            {(['tasks', 'tickets'] as const).map((k) => (
              <Button
                key={k}
                size="sm"
                variant="ghost"
                aria-pressed={kind === k}
                className={cn('h-8', kind === k ? 'bg-card text-foreground shadow-xs hover:bg-card' : 'text-muted-foreground')}
                onClick={() => update({ kind: k })}
              >
                {k === 'tasks' ? <ListTodo aria-hidden /> : <Ticket aria-hidden />}
                {k === 'tasks' ? 'Tasks' : 'Tickets'}
              </Button>
            ))}
          </div>
        )}
      </div>
      <HistoryBody key={kind} userId={userId} fullName={fullName} firstName={firstName} kind={kind} params={params} update={update} />
    </section>
  );
}

interface BodyProps {
  userId: number;
  fullName: string;
  firstName: string;
  kind: WorkKind;
  params: WorkHistoryParams;
  update: (patch: Partial<WorkHistoryParams>) => void;
}

function HistoryBody({ userId, fullName, firstName, kind, params, update }: BodyProps) {
  const [taskId, setTaskId] = useTaskParam();
  const [ticketId, setTicketId] = useTicketParam();
  const [exporting, setExporting] = useState(false);
  const rangeInvalid = params.range === 'CUSTOM' && Boolean(params.from && params.to && params.to < params.from);
  const summaryQuery = toWorkQuery(params, false);
  const listQuery = toWorkQuery(params, true);
  const summary = useWorkSummary(userId, kind, summaryQuery);
  const list = useWorkList<TaskHistoryRow | TicketHistoryRow>(userId, kind, listQuery, params.page, params.sort);
  const tabs = kind === 'tasks' ? TASK_TABS : TICKET_TABS;
  const filtered = Boolean(params.range || params.group || params.priority || params.search || params.status !== 'ALL');

  async function onExport() {
    setExporting(true);
    try {
      const slug = fullName.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '') || 'member';
      await exportWork(userId, kind, listQuery, params.sort, `${slug}-${kind}.csv`);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  }

  return (
    <>
      <FilterTabs columns={tabs.length as 7 | 8} aria-label={kind === 'tasks' ? 'Task status' : 'Ticket status'}>
        {tabs.map((status) => (
          <FilterTab
            key={status}
            label={tabLabel(kind, status)}
            count={tabCount(summary.data, status)}
            loading={summary.isPending}
            countTone={status === 'OVERDUE' && (summary.data?.overdue ?? 0) > 0 ? 'text-status-danger' : undefined}
            active={params.status === status}
            onSelect={() => update({ status })}
          />
        ))}
      </FilterTabs>

      <Card>
        <Filters kind={kind} params={params} update={update} rangeInvalid={rangeInvalid} />
        <SummaryStrip kind={kind} summary={summary.data} loading={summary.isPending} error={summary.error} onRetry={() => void summary.refetch()} />
        <div className="flex flex-wrap items-center justify-between gap-3 border-y bg-muted/20 px-6 py-3">
          <p className="text-sm text-muted-foreground" aria-live="polite">
            {list.data ? `${list.data.totalElements} ${kind === 'tasks' ? (list.data.totalElements === 1 ? 'task' : 'tasks') : list.data.totalElements === 1 ? 'ticket' : 'tickets'}` : ' '}
            {summary.data?.from && summary.data.to && ` · ${yearFormat.format(parseLocalDate(summary.data.from))} – ${yearFormat.format(parseLocalDate(summary.data.to))}`}
          </p>
          <Button variant="outline" size="sm" disabled={exporting || !list.data?.totalElements} onClick={() => void onExport()}>
            {exporting ? <LoaderCircle className="animate-spin" aria-hidden /> : null}
            Export CSV
          </Button>
        </div>

        {rangeInvalid ? (
          <p className="p-6 text-sm text-status-danger" role="alert">
            The end date is before the start date.
          </p>
        ) : list.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label={`Loading ${kind}`}>
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-11" />
            ))}
          </div>
        ) : list.isError ? (
          <ErrorState error={list.error} title={`Couldn't load ${firstName}'s ${kind}`} onRetry={() => void list.refetch()} />
        ) : list.data.content.length === 0 ? (
          <EmptyState
            icon={kind === 'tasks' ? ListTodo : Ticket}
            title={filtered ? `No ${kind} match these filters` : `No ${kind} assigned to ${firstName} yet`}
            description={filtered ? 'Try another status tab or a wider date range.' : undefined}
          />
        ) : (
          <>
            {kind === 'tasks' ? (
              <TaskHistoryTable rows={list.data.content as TaskHistoryRow[]} faded={list.isPlaceholderData} onOpen={setTaskId} />
            ) : (
              <TicketHistoryTable rows={list.data.content as TicketHistoryRow[]} faded={list.isPlaceholderData} onOpen={setTicketId} />
            )}
            <Pagination {...list.data} onPageChange={(page) => update({ page })} />
          </>
        )}
      </Card>
      <TaskDrawer taskId={taskId} onClose={() => setTaskId(null)} />
      <TicketDrawer ticketId={ticketId} onClose={() => setTicketId(null)} />
    </>
  );
}

function Filters({ kind, params, update, rangeInvalid }: { kind: WorkKind; params: WorkHistoryParams; update: (patch: Partial<WorkHistoryParams>) => void; rangeInvalid: boolean }) {
  const { user } = useAuth();
  const canSeeProjects = hasPermission(user, 'PROJECT_VIEW');
  const projects = useProjects({ size: 100 }, kind === 'tasks' && canSeeProjects);
  const categories = useTicketCategories(kind === 'tickets');
  const [search, setSearch] = useState(params.search);
  const debounced = useDebouncedValue(search.trim());

  useEffect(() => {
    if (debounced !== params.search.trim()) update({ search: debounced });
    // Only the typed text drives this; the URL value seeds the input once.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced]);

  const groups = kind === 'tasks' ? (projects.data?.content ?? []).map((p) => ({ id: p.id, name: p.name })) : (categories.data ?? []).map((c) => ({ id: c.id, name: c.name }));
  const showGroups = kind === 'tickets' || canSeeProjects;
  const fields = DATE_FIELDS[kind];

  return (
    <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-40 sm:[&>*:first-child]:min-w-60 sm:[&>*:first-child]:flex-1">
      <SearchInput placeholder={kind === 'tasks' ? 'Search title or task ID' : 'Search subject or ticket ID'} aria-label={`Search ${kind}`} value={search} onChange={(e) => setSearch(e.target.value)} />
      <Select aria-label="Date field" value={fields.some((f) => f.value === params.dateField) ? params.dateField : 'COMPLETED'} onChange={(e) => update({ dateField: e.target.value as WorkDateField })}>
        {fields.map((f) => (
          <option key={f.value} value={f.value}>
            {f.label}
          </option>
        ))}
      </Select>
      <Select aria-label="Date range" value={params.range} onChange={(e) => update({ range: e.target.value as WorkRange, ...(e.target.value === 'CUSTOM' ? {} : { from: '', to: '' }) })}>
        {RANGES.map((r) => (
          <option key={r.value} value={r.value}>
            {r.label}
          </option>
        ))}
      </Select>
      {params.range === 'CUSTOM' && (
        <>
          <Input type="date" aria-label="From" value={params.from} aria-invalid={rangeInvalid || undefined} onChange={(e) => update({ from: e.target.value })} />
          <Input type="date" aria-label="To" value={params.to} aria-invalid={rangeInvalid || undefined} onChange={(e) => update({ to: e.target.value })} />
        </>
      )}
      {showGroups && (
        <Select aria-label={kind === 'tasks' ? 'Project' : 'Category'} value={params.group} onChange={(e) => update({ group: e.target.value })}>
          <option value="">{kind === 'tasks' ? 'All projects' : 'All categories'}</option>
          {groups.map((g) => (
            <option key={g.id} value={g.id}>
              {g.name}
            </option>
          ))}
        </Select>
      )}
      <Select aria-label="Priority" value={params.priority} onChange={(e) => update({ priority: e.target.value })}>
        <option value="">Any priority</option>
        {PRIORITIES.map((p) => (
          <option key={p} value={p}>
            {PRIORITY_LABELS[p]}
          </option>
        ))}
      </Select>
      <Select aria-label="Sort" value={params.sort} onChange={(e) => update({ sort: e.target.value })}>
        {SORTS[kind].map((s) => (
          <option key={s.value} value={s.value}>
            {s.label}
          </option>
        ))}
      </Select>
    </div>
  );
}

interface StripProps {
  kind: WorkKind;
  summary: WorkSummary | undefined;
  loading: boolean;
  error: unknown;
  onRetry: () => void;
}

/** Completed, on time %, average days, overdue, hours (tasks) and completions per week, for the filters (all statuses). */
function SummaryStrip({ kind, summary, loading, error, onRetry }: StripProps) {
  if (error && !summary) return <ErrorState error={error} title="Couldn't load the summary" onRetry={onRetry} />;
  const tiles: { label: string; icon: LucideIcon; value: string; hint: string; critical?: boolean; muted?: boolean }[] = [
    { label: kind === 'tasks' ? 'Completed' : 'Resolved', icon: CircleCheck, value: String(summary?.completed ?? 0), hint: 'In these filters', muted: summary?.completed === 0 },
    {
      label: kind === 'tasks' ? 'On time' : 'SLA met',
      icon: Timer,
      value: summary?.completedOnTimePct == null ? '—' : `${summary.completedOnTimePct}%`,
      hint: kind === 'tasks' ? 'Completed by the due date' : 'Resolved within the SLA',
      muted: summary?.completedOnTimePct == null,
    },
    {
      label: 'Average days to complete',
      icon: Clock,
      value: summary?.averageDaysToComplete == null ? '—' : String(summary.averageDaysToComplete),
      hint: kind === 'tasks' ? 'From start (or creation)' : 'From creation to resolution',
      muted: summary?.averageDaysToComplete == null,
    },
    { label: 'Overdue', icon: CircleAlert, value: String(summary?.overdue ?? 0), hint: 'Open and past due now', critical: (summary?.overdue ?? 0) > 0, muted: summary?.overdue === 0 },
  ];
  if (kind === 'tasks') {
    tiles.push({ label: 'Hours logged', icon: CalendarClock, value: summary?.totalActualHours == null ? '—' : `${summary.totalActualHours} h`, hint: 'Actual hours on these tasks', muted: !summary?.totalActualHours });
  }
  return (
    <div className="grid gap-4 border-b p-6 sm:grid-cols-2 xl:grid-cols-4">
      {tiles.map((tile) => (
        <KpiTile key={tile.label} label={tile.label} icon={tile.icon} value={tile.value} hint={tile.hint} critical={tile.critical} muted={tile.muted} loading={loading} />
      ))}
      <div className={cn('flex min-h-28 min-w-0 flex-col rounded-xl border bg-card px-4 py-3.5 shadow-card', kind === 'tasks' ? 'sm:col-span-2 xl:col-span-3' : 'sm:col-span-2 xl:col-span-4')}>
        <p className="text-label font-medium text-muted-foreground">{kind === 'tasks' ? 'Completed per week' : 'Resolved per week'}</p>
        {loading || !summary ? <Skeleton className="mt-2 h-20" /> : <WeeklyChart summary={summary} kind={kind} />}
      </div>
    </div>
  );
}

function WeeklyChart({ summary, kind }: { summary: WorkSummary; kind: WorkKind }) {
  const thisWeek = summary.completedPerWeek.find((w) => w.weekStart <= summary.today && summary.today < addDays(w.weekStart, 7))?.weekStart;
  const data = summary.completedPerWeek.map((w) => ({ ...w, label: dayFormat.format(parseLocalDate(w.weekStart)), value: w.completed, highlight: w.weekStart === thisWeek }));
  const verb = kind === 'tasks' ? 'Completed' : 'Resolved';
  return (
    <>
      <BarGraph
        data={data}
        height={96}
        renderTooltip={(week) => (
          <ChartTooltip title={week.highlight ? `This week (from ${week.label})` : `Week of ${week.label}`}>
            <ChartTooltipRow color="var(--chart-1)" label={verb} value={week.completed} />
          </ChartTooltip>
        )}
      />
      <table className="sr-only">
        <caption>
          {kind === 'tasks' ? 'Tasks' : 'Tickets'} {verb.toLowerCase()} per week
        </caption>
        <thead>
          <tr>
            <th>Week of</th>
            <th>{verb}</th>
          </tr>
        </thead>
        <tbody>
          {data.map((week) => (
            <tr key={week.weekStart}>
              <td>{week.label}</td>
              <td>{week.completed}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
