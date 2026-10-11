import { AlarmClock, CalendarClock, CalendarDays, CircleCheck, CircleDot, ListTodo, OctagonX, Plus, type LucideIcon } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { FilterTab, FilterTabs } from '@/components/common/FilterTab';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useMyTaskSummary, useTasks } from '@/features/tasks/api';
import { CreateTaskDialog } from '@/features/tasks/CreateTaskDialog';
import { TaskDrawer } from '@/features/tasks/TaskDrawer';
import { TaskTable } from '@/features/tasks/TaskTable';
import { parseLocalDate } from '@/features/tasks/task-meta';
import { ACTIVE_STATUSES, type MyTaskSummary, type TaskQuery } from '@/features/tasks/types';
import { useTaskParam } from '@/features/tasks/use-task-param';
import { cn } from '@/lib/utils';

type Tab = 'overdue' | 'today' | 'upcoming' | 'active' | 'blocked' | 'completed';

interface TabDefinition {
  id: Tab;
  label: string;
  icon: LucideIcon;
  tone: string;
  count: (s: MyTaskSummary) => number;
  query: TaskQuery;
  empty: string;
}

/** Each card is also a tab: clicking "Overdue" lists exactly those tasks. */
const TABS: TabDefinition[] = [
  { id: 'overdue', label: 'Overdue', icon: AlarmClock, tone: 'text-status-danger', count: (s) => s.overdue, query: { due: 'OVERDUE', sort: 'due,asc' }, empty: 'Nothing overdue. Nice work.' },
  { id: 'today', label: 'Due today', icon: CalendarClock, tone: 'text-status-warning', count: (s) => s.dueToday, query: { due: 'TODAY', sort: 'priority,desc' }, empty: 'Nothing due today.' },
  { id: 'upcoming', label: 'Next 7 days', icon: CalendarDays, tone: 'text-primary', count: (s) => s.upcoming, query: { due: 'UPCOMING', sort: 'due,asc' }, empty: 'Nothing due in the next week.' },
  { id: 'active', label: 'All open', icon: CircleDot, tone: 'text-primary', count: (s) => s.active, query: { status: ACTIVE_STATUSES, sort: 'due,asc' }, empty: 'You have no open tasks.' },
  { id: 'blocked', label: 'Blocked', icon: OctagonX, tone: 'text-status-danger', count: (s) => s.blocked, query: { status: ['BLOCKED'], sort: 'due,asc' }, empty: 'Nothing is blocked.' },
  { id: 'completed', label: 'Done this week', icon: CircleCheck, tone: 'text-status-success', count: (s) => s.completedThisWeek, query: { status: ['COMPLETED'], sort: 'updated,desc' }, empty: 'Nothing completed yet this week.' },
];

const PAGE_SIZE = 20;
const todayFormat = new Intl.DateTimeFormat(undefined, { weekday: 'long', day: 'numeric', month: 'long' });

/** The signed-in user's own work: what's overdue, what's due today, what's next. */
export default function MyTasksPage() {
  const { user } = useAuth();
  const summary = useMyTaskSummary();
  const [tab, setTab] = useState<Tab>('active');
  const [page, setPage] = useState(0);
  const [taskId, setTaskId] = useTaskParam();
  const [creating, setCreating] = useState(false);
  const active = TABS.find((t) => t.id === tab) ?? TABS[0]!;
  const tasks = useTasks({ ...active.query, view: 'ASSIGNED_TO_ME', page, size: PAGE_SIZE });

  return (
    <div className="space-y-6">
      <PageHeader
        title="My Tasks"
        description={summary.data ? `Today is ${todayFormat.format(parseLocalDate(summary.data.today))}.` : 'Your assigned work, by urgency.'}
        actions={
          hasPermission(user, 'TASK_CREATE') && (
            <Button onClick={() => setCreating(true)}>
              <Plus aria-hidden />
              New task
            </Button>
          )
        }
      />

      <FilterTabs columns={6} aria-label="Task views">
        {TABS.map((t) => (
          <FilterTab
            key={t.id}
            label={t.label}
            icon={<t.icon className={cn('size-4 shrink-0', t.tone)} aria-hidden />}
            count={summary.data ? t.count(summary.data) : null}
            countTone={t.id === 'overdue' ? 'text-status-danger' : undefined}
            loading={summary.isPending}
            active={tab === t.id}
            onSelect={() => {
              setTab(t.id);
              setPage(0);
            }}
          />
        ))}
      </FilterTabs>

      <Card>
        <div className="flex items-center gap-2 border-b px-4 py-3">
          <active.icon className={cn('size-4', active.tone)} aria-hidden />
          <h2 className="text-card-title font-semibold">{active.label}</h2>
        </div>
        {tasks.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading tasks">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : tasks.isError ? (
          <ErrorState error={tasks.error} title="Couldn't load your tasks" onRetry={() => void tasks.refetch()} />
        ) : tasks.data.content.length === 0 ? (
          <EmptyState icon={ListTodo} title={active.empty} />
        ) : (
          <>
            <TaskTable tasks={tasks.data.content} onOpen={(t) => setTaskId(t.id)} hideAssignee dimmed={tasks.isPlaceholderData} />
            <Pagination {...tasks.data} onPageChange={setPage} />
          </>
        )}
      </Card>

      <CreateTaskDialog open={creating} onOpenChange={setCreating} onCreated={(t) => setTaskId(t.id)} assignToMe />
      <TaskDrawer taskId={taskId} onClose={() => setTaskId(null)} />
    </div>
  );
}
