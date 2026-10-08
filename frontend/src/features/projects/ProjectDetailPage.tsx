import {
  AlarmClock,
  ArrowLeft,
  CircleCheck,
  ClipboardList,
  Flag,
  Link2,
  Pencil,
  Plus,
  ShieldAlert,
  Trash2,
  TriangleAlert,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { toast } from 'sonner';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { Pagination } from '@/components/common/Pagination';
import { UserAvatar } from '@/components/common/UserAvatar';
import { Badge, type BadgeProps } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useProjectOptions, useTasks } from '@/features/tasks/api';
import { TaskDrawer } from '@/features/tasks/TaskDrawer';
import { TaskTable } from '@/features/tasks/TaskTable';
import { parseLocalDate } from '@/features/tasks/task-meta';
import { useTaskParam } from '@/features/tasks/use-task-param';
import { useTeamDirectory } from '@/features/team/api';
import { errorMessage } from '@/lib/api/errors';
import { cn } from '@/lib/utils';

import { useProject, useProjectDependencies, useProjectMembers, useProjectMilestones, useProjectRisks, type Milestone, type ProjectDetail, type Risk } from './api';
import { ProgressBar, ProjectStatusBadge } from './ProjectBadges';
import { ProjectFormDialog } from './ProjectFormDialog';
import { MilestoneDialog, RiskDialog } from './ProjectItemDialogs';
import { dateRange, MILESTONE_STATUS_LABELS, RISK_LEVEL_LABELS, RISK_STATUS_LABELS, severityLevel } from './project-meta';

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });

async function attempt(action: Promise<unknown>, success?: string) {
  try {
    await action;
    if (success) toast.success(success);
  } catch (error) {
    toast.error(errorMessage(error));
  }
}

/** One project: progress, milestones, risks, tasks, team and dependencies. */
export default function ProjectDetailPage() {
  const id = Number(useParams().id);
  const project = useProject(id);
  const [editing, setEditing] = useState(false);

  if (project.isPending) {
    return (
      <div className="space-y-6" role="status" aria-label="Loading project">
        <Skeleton className="h-10 w-1/2" />
        <div className="grid gap-3 md:grid-cols-4">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-24 rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-80 rounded-xl" />
      </div>
    );
  }
  if (project.isError) {
    return (
      <Card>
        <ErrorState error={project.error} title="Couldn't open this project" onRetry={() => void project.refetch()} />
      </Card>
    );
  }
  const p = project.data;
  const openRisks = p.risks.filter((r) => r.status === 'OPEN').length;
  const milestonesDone = p.milestones.filter((m) => m.status === 'COMPLETED').length;

  return (
    <div className="space-y-6">
      <div className="space-y-3">
        <Link to="/projects" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="size-4" aria-hidden />
          Projects
        </Link>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="font-mono text-muted-foreground">{p.code}</span>
              <ProjectStatusBadge status={p.status} />
              <span className="text-muted-foreground">{p.department.name}</span>
            </div>
            <h1 className="mt-1 text-xl font-semibold tracking-tight sm:text-2xl">{p.name}</h1>
            {p.description && <p className="mt-1 max-w-3xl text-sm whitespace-pre-wrap text-muted-foreground">{p.description}</p>}
          </div>
          {p.permissions.canEdit && (
            <Button variant="outline" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit project
            </Button>
          )}
        </div>
      </div>

      <section aria-label="Project figures" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label={p.progressOverride === null ? 'Progress (from tasks)' : 'Progress (set manually)'} icon={CircleCheck}>
          <ProgressBar value={p.progress} label="Project progress" className="mt-2" />
        </Stat>
        <Stat label="Tasks" icon={ClipboardList}>
          <span className="text-2xl font-semibold tabular-nums">
            {p.tasks.completed}/{p.tasks.total}
          </span>
          {p.tasks.overdue > 0 && <span className="ml-2 text-sm font-medium text-status-danger">{p.tasks.overdue} overdue</span>}
        </Stat>
        <Stat label="Milestones" icon={Flag}>
          <span className="text-2xl font-semibold tabular-nums">
            {milestonesDone}/{p.milestones.length}
          </span>
        </Stat>
        <Stat label="Timeline" icon={AlarmClock}>
          <span className="text-sm font-medium">{dateRange(p.startDate, p.endDate)}</span>
          <span className="block text-xs text-muted-foreground">Owner: {p.owner?.fullName ?? 'none'}</span>
        </Stat>
      </section>

      <Card>
        <Tabs defaultValue="milestones" className="pb-4">
          <TabsList className="overflow-x-auto">
            <TabsTrigger value="milestones">Milestones ({p.milestones.length})</TabsTrigger>
            <TabsTrigger value="risks">Risks ({openRisks} open)</TabsTrigger>
            <TabsTrigger value="tasks">Tasks ({p.tasks.total})</TabsTrigger>
            <TabsTrigger value="team">Team ({p.members.length})</TabsTrigger>
            <TabsTrigger value="dependencies">Depends on ({p.dependencies.length})</TabsTrigger>
          </TabsList>
          <div className="px-4 pt-4 sm:px-6">
            <TabsContent value="milestones">
              <MilestonesSection project={p} />
            </TabsContent>
            <TabsContent value="risks">
              <RisksSection project={p} />
            </TabsContent>
            <TabsContent value="tasks">
              <TasksSection project={p} />
            </TabsContent>
            <TabsContent value="team">
              <TeamSection project={p} />
            </TabsContent>
            <TabsContent value="dependencies">
              <DependenciesSection project={p} />
            </TabsContent>
          </div>
        </Tabs>
      </Card>

      <ProjectFormDialog open={editing} onOpenChange={setEditing} project={p} />
    </div>
  );
}

function Stat({ label, icon: Icon, children }: { label: string; icon: LucideIcon; children: ReactNode }) {
  return (
    <div className="rounded-xl border bg-card p-4 shadow-xs">
      <p className="flex items-center gap-2 text-sm text-muted-foreground">
        <Icon className="size-4" aria-hidden />
        {label}
      </p>
      <div className="mt-1">{children}</div>
    </div>
  );
}

function SectionHeader({ text, action }: { text: string; action?: ReactNode }) {
  return (
    <div className="mb-3 flex items-center justify-between gap-3">
      <p className="text-sm text-muted-foreground">{text}</p>
      {action}
    </div>
  );
}

function MilestonesSection({ project }: { project: ProjectDetail }) {
  const milestones = useProjectMilestones(project.id);
  const [editing, setEditing] = useState<Milestone | null | undefined>(undefined);
  const canEdit = project.permissions.canEdit;

  return (
    <>
      <SectionHeader
        text="Key dates for the project. Open milestones also appear on the calendar."
        action={
          canEdit && (
            <Button size="sm" variant="outline" onClick={() => setEditing(null)}>
              <Plus aria-hidden />
              Add milestone
            </Button>
          )
        }
      />
      {project.milestones.length === 0 ? (
        <EmptyState icon={Flag} title="No milestones yet" className="py-8" />
      ) : (
        <ol className="divide-y rounded-lg border">
          {project.milestones.map((m) => (
            <li key={m.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              {m.status === 'COMPLETED' ? (
                <CircleCheck className="size-4 shrink-0 text-status-success" aria-label="Completed" />
              ) : (
                <Flag className={cn('size-4 shrink-0', m.overdue ? 'text-status-danger' : 'text-muted-foreground')} aria-hidden />
              )}
              <div className="min-w-0 flex-1">
                <p className={cn('text-sm font-medium', m.status === 'COMPLETED' && 'text-muted-foreground line-through')}>{m.name}</p>
                <p className="text-xs text-muted-foreground">
                  {m.dueDate ? `Due ${dayFormat.format(parseLocalDate(m.dueDate))}` : 'No due date'} · {MILESTONE_STATUS_LABELS[m.status]}
                </p>
              </div>
              {m.overdue && (
                <Badge tone="danger">
                  <AlarmClock aria-hidden />
                  Overdue
                </Badge>
              )}
              {canEdit && (
                <div className="flex gap-1">
                  <Button variant="ghost" size="icon" className="size-8" aria-label={`Edit ${m.name}`} onClick={() => setEditing(m)}>
                    <Pencil />
                  </Button>
                  <Button variant="ghost" size="icon" className="size-8" aria-label={`Delete ${m.name}`} onClick={() => void attempt(milestones.remove.mutateAsync(m.id), 'Milestone deleted')}>
                    <Trash2 />
                  </Button>
                </div>
              )}
            </li>
          ))}
        </ol>
      )}
      <MilestoneDialog
        open={editing !== undefined}
        milestone={editing ?? null}
        onOpenChange={(open) => !open && setEditing(undefined)}
        onSave={(input) => (editing ? milestones.update.mutateAsync({ milestoneId: editing.id, input }) : milestones.add.mutateAsync(input))}
      />
    </>
  );
}

const SEVERITY_TONE: Record<'LOW' | 'MEDIUM' | 'HIGH', BadgeProps['tone']> = { LOW: 'neutral', MEDIUM: 'warning', HIGH: 'danger' };

function RisksSection({ project }: { project: ProjectDetail }) {
  const risks = useProjectRisks(project.id);
  const [editing, setEditing] = useState<Risk | null | undefined>(undefined);
  const canEdit = project.permissions.canEdit;
  const people = [...(project.owner ? [project.owner] : []), ...project.members.filter((m) => m.id !== project.owner?.id)];

  return (
    <>
      <SectionHeader
        text="Severity is probability × impact."
        action={
          canEdit && (
            <Button size="sm" variant="outline" onClick={() => setEditing(null)}>
              <Plus aria-hidden />
              Add risk
            </Button>
          )
        }
      />
      {project.risks.length === 0 ? (
        <EmptyState icon={ShieldAlert} title="No risks recorded" className="py-8" />
      ) : (
        <ul className="divide-y rounded-lg border">
          {project.risks.map((r) => {
            const level = severityLevel(r.severity);
            return (
              <li key={r.id} className="flex flex-wrap items-start gap-3 px-4 py-3">
                <Badge tone={r.status === 'OPEN' ? SEVERITY_TONE[level] : 'neutral'} className="mt-0.5">
                  <TriangleAlert aria-hidden />
                  {RISK_LEVEL_LABELS[level]} ({r.severity})
                </Badge>
                <div className="min-w-0 flex-1">
                  <p className={cn('text-sm font-medium', r.status !== 'OPEN' && 'text-muted-foreground')}>{r.title}</p>
                  <p className="text-xs text-muted-foreground">
                    Probability {RISK_LEVEL_LABELS[r.probability].toLowerCase()} · impact {RISK_LEVEL_LABELS[r.impact].toLowerCase()} · {RISK_STATUS_LABELS[r.status]}
                    {r.owner && ` · ${r.owner.fullName}`}
                  </p>
                  {r.mitigation && <p className="mt-1 text-sm">Mitigation: {r.mitigation}</p>}
                </div>
                {canEdit && (
                  <div className="flex gap-1">
                    <Button variant="ghost" size="icon" className="size-8" aria-label={`Edit ${r.title}`} onClick={() => setEditing(r)}>
                      <Pencil />
                    </Button>
                    <Button variant="ghost" size="icon" className="size-8" aria-label={`Delete ${r.title}`} onClick={() => void attempt(risks.remove.mutateAsync(r.id), 'Risk deleted')}>
                      <Trash2 />
                    </Button>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
      <RiskDialog
        open={editing !== undefined}
        risk={editing ?? null}
        people={people}
        onOpenChange={(open) => !open && setEditing(undefined)}
        onSave={(input) => (editing ? risks.update.mutateAsync({ riskId: editing.id, input }) : risks.add.mutateAsync(input))}
      />
    </>
  );
}

function TasksSection({ project }: { project: ProjectDetail }) {
  const [page, setPage] = useState(0);
  const [taskId, setTaskId] = useTaskParam();
  const tasks = useTasks({ projectId: project.id, sort: 'due,asc', page, size: 20 });
  return (
    <>
      {tasks.isPending ? (
        <div className="space-y-2" role="status" aria-label="Loading tasks">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-12" />
          ))}
        </div>
      ) : tasks.isError ? (
        <ErrorState error={tasks.error} title="Couldn't load tasks" onRetry={() => void tasks.refetch()} />
      ) : tasks.data.content.length === 0 ? (
        <EmptyState icon={ClipboardList} title="No tasks you can see in this project" className="py-8" />
      ) : (
        <div className="-mx-4 sm:-mx-6">
          <TaskTable tasks={tasks.data.content} onOpen={(t) => setTaskId(t.id)} dimmed={tasks.isPlaceholderData} />
          <Pagination {...tasks.data} onPageChange={setPage} />
        </div>
      )}
      <TaskDrawer taskId={taskId} onClose={() => setTaskId(null)} />
    </>
  );
}

function TeamSection({ project }: { project: ProjectDetail }) {
  const members = useProjectMembers(project.id);
  const canEdit = project.permissions.canEdit;
  const people = useTeamDirectory({ status: 'ACTIVE', size: 100, sort: 'name,asc' }, canEdit);
  const [userId, setUserId] = useState('');
  const candidates = (people.data?.content ?? []).filter((p) => !project.members.some((m) => m.id === p.id));

  return (
    <>
      <SectionHeader text="Members can see the project even outside its department." />
      {project.members.length === 0 ? (
        <EmptyState icon={Users} title="No members yet" className="py-8" />
      ) : (
        <ul className="divide-y rounded-lg border">
          {project.members.map((m) => (
            <li key={m.id} className="flex items-center gap-3 px-4 py-2.5">
              <UserAvatar name={m.fullName} size="sm" />
              <span className="flex-1 text-sm">
                {m.fullName}
                {m.jobTitle && <span className="text-muted-foreground"> · {m.jobTitle}</span>}
              </span>
              {canEdit && (
                <Button variant="ghost" size="icon" className="size-8" aria-label={`Remove ${m.fullName}`} onClick={() => void attempt(members.remove.mutateAsync(m.id))}>
                  <Trash2 />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {canEdit && (
        <div className="mt-3 flex gap-2">
          <Select aria-label="Add member" value={userId} onChange={(e) => setUserId(e.target.value)}>
            <option value="">Add a member…</option>
            {candidates.map((p) => (
              <option key={p.id} value={p.id}>
                {p.fullName} — {p.department.name}
              </option>
            ))}
          </Select>
          <Button variant="outline" disabled={!userId || members.add.isPending} onClick={() => void attempt(members.add.mutateAsync(Number(userId))).then(() => setUserId(''))}>
            Add
          </Button>
        </div>
      )}
    </>
  );
}

function DependenciesSection({ project }: { project: ProjectDetail }) {
  const dependencies = useProjectDependencies(project.id);
  const options = useProjectOptions();
  const canEdit = project.permissions.canEdit;
  const [projectId, setProjectId] = useState('');
  const candidates = (options.data ?? []).filter((o) => o.id !== project.id && !project.dependencies.some((d) => d.id === o.id));

  return (
    <>
      <SectionHeader text="Projects that must finish before this one." />
      {project.dependencies.length === 0 ? (
        <EmptyState icon={Link2} title="No dependencies" className="py-8" />
      ) : (
        <ul className="divide-y rounded-lg border">
          {project.dependencies.map((d) => (
            <li key={d.id} className="flex items-center gap-3 px-4 py-2.5">
              <Link2 className="size-4 text-muted-foreground" aria-hidden />
              <Link to={`/projects/${d.id}`} className="flex-1 text-sm hover:underline">
                <span className="font-mono text-muted-foreground">{d.code}</span> {d.name}
              </Link>
              {canEdit && (
                <Button variant="ghost" size="icon" className="size-8" aria-label={`Remove dependency ${d.code}`} onClick={() => void attempt(dependencies.remove.mutateAsync(d.id))}>
                  <Trash2 />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {canEdit && (
        <div className="mt-3 flex gap-2">
          <Select aria-label="Add dependency" value={projectId} onChange={(e) => setProjectId(e.target.value)}>
            <option value="">Depends on…</option>
            {candidates.map((o) => (
              <option key={o.id} value={o.id}>
                {o.code} · {o.name}
              </option>
            ))}
          </Select>
          <Button variant="outline" disabled={!projectId || dependencies.add.isPending} onClick={() => void attempt(dependencies.add.mutateAsync(Number(projectId))).then(() => setProjectId(''))}>
            Add
          </Button>
        </div>
      )}
    </>
  );
}
