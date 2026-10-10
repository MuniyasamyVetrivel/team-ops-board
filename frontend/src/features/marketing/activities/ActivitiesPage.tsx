import { AlarmClock, CalendarCheck, Plus, Repeat } from 'lucide-react';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';

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
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { DueBadge } from '@/features/tasks/TaskBadges';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { cn } from '@/lib/utils';

import { FREQUENCIES, OPEN_STATUSES, useActivities, useOccurrences, type Frequency } from './api';
import { FrequencyBadge, InactiveBadge } from './ActivityBadges';
import { ActivityFormDialog } from './ActivityFormDialog';
import { FREQUENCY_LABELS, formatInstantDate } from './activity-meta';
import { OccurrenceTable } from './OccurrenceTable';

/**
 * Marketing Activities (brief sections 35–36): recurring processes with their next occurrence, plus everything open
 * across activities (?tab=due).
 */
export default function ActivitiesPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') === 'due' ? 'due' : 'activities';
  const [creating, setCreating] = useState(false);
  const canEdit = hasPermission(user, 'MARKETING_EDIT');

  function selectTab(next: string) {
    setParams(
      (existing) => {
        const updated = new URLSearchParams(existing);
        if (next === 'due') updated.set('tab', 'due');
        else updated.delete('tab');
        return updated;
      },
      { replace: true },
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Marketing Activities"
        description="Recurring marketing processes. Completing an occurrence (or its task) creates the next one."
        actions={
          canEdit && (
            <Button onClick={() => setCreating(true)}>
              <Plus aria-hidden />
              New activity
            </Button>
          )
        }
      />
      <Card>
        <Tabs value={tab} onValueChange={selectTab}>
          <TabsList>
            <TabsTrigger value="activities">Activities</TabsTrigger>
            <TabsTrigger value="due">Due &amp; overdue</TabsTrigger>
          </TabsList>
          <TabsContent value="activities">
            <ActivitiesTab />
          </TabsContent>
          <TabsContent value="due">
            <DueTab />
          </TabsContent>
        </Tabs>
      </Card>
      <ActivityFormDialog open={creating} onOpenChange={setCreating} onSaved={(saved) => void navigate(`/digital-marketing/activities/${saved.id}`)} />
    </div>
  );
}

function ListSkeleton({ label }: { label: string }) {
  return (
    <div className="space-y-3 p-4" role="status" aria-label={label}>
      {Array.from({ length: 5 }, (_, i) => (
        <Skeleton key={i} className="h-12" />
      ))}
    </div>
  );
}

function ActivitiesTab() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [frequency, setFrequency] = useState('');
  const [state, setState] = useState('active');
  const [mine, setMine] = useState('');
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(search.trim());
  const activities = useActivities({
    search: debounced,
    frequency: frequency ? (frequency as Frequency) : undefined,
    active: state === 'all' ? undefined : state === 'active',
    ownerId: mine ? user?.id : undefined,
    page,
    size: 25,
  });

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  const filtered = Boolean(search || frequency || state !== 'active' || mine);
  const open = (id: number) => void navigate(`/digital-marketing/activities/${id}`);

  return (
    <>
      <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-40 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
        <SearchInput placeholder="Search activities" aria-label="Search activities" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
        <Select aria-label="Frequency" value={frequency} onChange={(e) => filter(setFrequency)(e.target.value)}>
          <option value="">All frequencies</option>
          {FREQUENCIES.map((f) => (
            <option key={f} value={f}>
              {FREQUENCY_LABELS[f]}
            </option>
          ))}
        </Select>
        <Select aria-label="Active" value={state} onChange={(e) => filter(setState)(e.target.value)}>
          <option value="active">Active</option>
          <option value="inactive">Inactive</option>
          <option value="all">Active and inactive</option>
        </Select>
        <Select aria-label="Whose activities" value={mine} onChange={(e) => filter(setMine)(e.target.value)}>
          <option value="">Everyone’s</option>
          <option value="mine">Mine (owner or assignee)</option>
        </Select>
      </div>
      {activities.isPending ? (
        <ListSkeleton label="Loading activities" />
      ) : activities.isError ? (
        <ErrorState error={activities.error} title="Couldn't load activities" onRetry={() => void activities.refetch()} />
      ) : activities.data.content.length === 0 ? (
        <EmptyState
          icon={Repeat}
          title={filtered ? 'No activities match these filters' : 'No recurring activities yet'}
          description={filtered ? 'Try a different search or clear some filters.' : 'Set up processes like the monthly SEO ranking update, so each period gets its own task.'}
        />
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Activity</TableHead>
                <TableHead>Frequency</TableHead>
                <TableHead>Assignee</TableHead>
                <TableHead>Next due</TableHead>
                <TableHead>Open</TableHead>
                <TableHead>Last completed</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody className={cn(activities.isPlaceholderData && 'opacity-60')}>
              {activities.data.content.map((a) => (
                <TableRow
                  key={a.id}
                  data-clickable="true"
                  tabIndex={0}
                  aria-label={`Open ${a.name}`}
                  onClick={() => open(a.id)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') open(a.id);
                  }}
                >
                  <TableCell className="max-w-sm">
                    <p className="flex items-center gap-2 truncate font-medium">
                      {a.name}
                      {!a.active && <InactiveBadge />}
                    </p>
                    <p className="text-xs text-muted-foreground">{a.generatesTasks ? 'Creates a task each period' : 'Completed on the activity'}</p>
                  </TableCell>
                  <TableCell>
                    <FrequencyBadge frequency={a.frequency} />
                  </TableCell>
                  <TableCell className="max-w-44">{a.assignee ? <UserCell name={a.assignee.fullName} /> : <span className="text-sm text-muted-foreground">Unassigned</span>}</TableCell>
                  <TableCell>
                    {a.nextOccurrence ? (
                      <div className="space-y-0.5">
                        <DueBadge dueDate={a.nextOccurrence.dueDate} state={a.nextOccurrence.dueState} />
                        <p className="text-xs text-muted-foreground">{a.nextOccurrence.periodLabel}</p>
                      </div>
                    ) : (
                      <span className="text-sm text-muted-foreground">Nothing open</span>
                    )}
                  </TableCell>
                  <TableCell className="text-sm tabular-nums">
                    {a.openCount}
                    {a.overdueCount > 0 && (
                      <span className="ml-2 inline-flex items-center gap-1 text-xs font-medium text-status-danger">
                        <AlarmClock className="size-3.5" aria-hidden />
                        {a.overdueCount} overdue
                      </span>
                    )}
                  </TableCell>
                  <TableCell className="text-sm whitespace-nowrap text-muted-foreground">{formatInstantDate(a.lastCompletedAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Pagination {...activities.data} onPageChange={setPage} />
        </>
      )}
    </>
  );
}

function DueTab() {
  const { user } = useAuth();
  const [mine, setMine] = useState(false);
  const [page, setPage] = useState(0);
  const occurrences = useOccurrences({ status: OPEN_STATUSES, assigneeId: mine ? user?.id : undefined, sort: 'due,asc', page, size: 25 });

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <p className="text-sm text-muted-foreground">Every open occurrence, earliest due first. Occurrences with a task are completed from the task.</p>
        <Select
          aria-label="Assigned to"
          className="w-48"
          value={mine ? 'mine' : ''}
          onChange={(e) => {
            setMine(e.target.value === 'mine');
            setPage(0);
          }}
        >
          <option value="">Everyone</option>
          <option value="mine">Assigned to me</option>
        </Select>
      </div>
      {occurrences.isPending ? (
        <ListSkeleton label="Loading open occurrences" />
      ) : occurrences.isError ? (
        <ErrorState error={occurrences.error} title="Couldn't load open occurrences" onRetry={() => void occurrences.refetch()} />
      ) : occurrences.data.content.length === 0 ? (
        <EmptyState icon={CalendarCheck} title={mine ? 'Nothing open for you' : 'Nothing open'} description="New occurrences appear at the start of each period." />
      ) : (
        <>
          <OccurrenceTable occurrences={occurrences.data.content} showActivity dimmed={occurrences.isPlaceholderData} />
          <Pagination {...occurrences.data} onPageChange={setPage} />
        </>
      )}
    </>
  );
}
