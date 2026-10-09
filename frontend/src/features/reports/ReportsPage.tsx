import { Download } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { PageHeader } from '@/components/common/PageHeader';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { hasPermission, type PermissionCode } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { PROJECT_STATUS_LABELS } from '@/features/projects/project-meta';
import { useProjectOptions } from '@/features/tasks/api';
import { STATUS_LABELS } from '@/features/tasks/task-meta';
import { useTeamDirectory } from '@/features/team/api';
import { errorMessage } from '@/lib/api/errors';

import { exportReport, useProjectReport, useTaskReport, useTicketReport, useWorkloadReport, type ReportKind, type ReportQuery } from './api';
import { PRESET_LABELS, previousRange, rangeFor, rangeLabel, type DateRange, type RangePreset } from './report-range';
import { ProjectReportView, ReportState, TaskReportView, TicketReportView, WorkloadReportView } from './ReportViews';

const TABS: { kind: ReportKind; label: string; permission: PermissionCode }[] = [
  { kind: 'tasks', label: 'Tasks', permission: 'TASK_VIEW' },
  { kind: 'workload', label: 'Workload', permission: 'WORKLOAD_VIEW' },
  { kind: 'tickets', label: 'Tickets', permission: 'TICKET_VIEW' },
  { kind: 'projects', label: 'Projects', permission: 'PROJECT_VIEW' },
];

const TASK_STATUSES = ['TODO', 'IN_PROGRESS', 'BLOCKED', 'IN_REVIEW', 'COMPLETED', 'CANCELLED'] as const;
const PROJECT_STATUSES = ['PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED'] as const;
const PRESETS: RangePreset[] = ['LAST_30', 'THIS_MONTH', 'LAST_MONTH', 'LAST_90', 'CUSTOM'];

/**
 * Management reports (brief sections 20 and 78): tasks, workload, tickets and projects within the viewer's scope,
 * with filters, a comparison with the previous period, charts and CSV export. Each tab needs its module's view
 * permission; the server enforces the same.
 */
export default function ReportsPage() {
  const { user } = useAuth();
  const tabs = TABS.filter((t) => user && hasPermission(user, t.permission));
  const [tab, setTab] = useState<ReportKind>(tabs[0]?.kind ?? 'tasks');
  const [preset, setPreset] = useState<RangePreset>('LAST_30');
  const [custom, setCustom] = useState<DateRange>({ from: '', to: '' });
  const [departmentId, setDepartmentId] = useState('');
  const [personId, setPersonId] = useState('');
  const [projectId, setProjectId] = useState('');
  const [taskStatus, setTaskStatus] = useState('');
  const [projectStatus, setProjectStatus] = useState('');
  const [exporting, setExporting] = useState(false);

  const canTasks = user !== null && hasPermission(user, 'TASK_VIEW');
  const canTickets = user !== null && hasPermission(user, 'TICKET_VIEW');
  const canExport = user !== null && hasPermission(user, 'REPORT_EXPORT');
  const departments = useDepartments();
  const people = useTeamDirectory({ departmentId: departmentId ? Number(departmentId) : undefined, status: 'ACTIVE', size: 100, sort: 'name,asc' }, user !== null && hasPermission(user, 'TEAM_VIEW'));
  const projects = useProjectOptions();

  // "Today" is the server's: the end of the default (last 30 days) report, which the first tab shares from the cache.
  const anchorTasks = useTaskReport({}, canTasks);
  const anchorTickets = useTicketReport({}, !canTasks && canTickets);
  const today = anchorTasks.data?.range.to ?? anchorTickets.data?.range.to;

  const range: DateRange | null =
    preset === 'LAST_30' ? null : preset === 'CUSTOM' ? (custom.from && custom.to && custom.from <= custom.to ? custom : null) : today ? rangeFor(preset, today) : null;
  const rangeReady = preset === 'LAST_30' || range !== null;
  const dates = range ? { from: range.from, to: range.to } : {};
  const department = departmentId ? Number(departmentId) : undefined;
  const person = personId ? Number(personId) : undefined;

  const queries: Record<ReportKind, ReportQuery> = {
    tasks: { ...dates, departmentId: department, userId: person, projectId: projectId ? Number(projectId) : undefined, status: taskStatus ? [taskStatus] : undefined },
    workload: { departmentId: department, userId: person },
    tickets: { ...dates, departmentId: department, assigneeId: person },
    projects: { departmentId: department, status: projectStatus ? [projectStatus] : undefined },
  };

  async function onExport() {
    setExporting(true);
    try {
      await exportReport(tab, queries[tab]);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setExporting(false);
    }
  }

  const dated = tab === 'tasks' || tab === 'tickets';
  return (
    <div className="space-y-6">
      <PageHeader
        title="Reports"
        description="Management reports for your teams, compared with the previous period"
        actions={
          canExport && (
            <Button variant="outline" disabled={exporting || (dated && !rangeReady)} onClick={() => void onExport()}>
              <Download aria-hidden />
              Export CSV
            </Button>
          )
        }
      />

      <Tabs value={tab} onValueChange={(value) => setTab(value as ReportKind)}>
        <TabsList>
          {tabs.map((t) => (
            <TabsTrigger key={t.kind} value={t.kind}>
              {t.label}
            </TabsTrigger>
          ))}
        </TabsList>

        <div role="group" aria-label="Report filters" className="mt-4 flex flex-wrap items-end gap-3 rounded-xl border bg-card p-4">
          {dated && (
            <label className="space-y-1 text-sm">
              <span className="block text-xs text-muted-foreground">Period</span>
              <Select aria-label="Period" className="w-44" value={preset} onChange={(e) => setPreset(e.target.value as RangePreset)}>
                {PRESETS.map((p) => (
                  <option key={p} value={p} disabled={p !== 'LAST_30' && p !== 'CUSTOM' && !today}>
                    {PRESET_LABELS[p]}
                  </option>
                ))}
              </Select>
            </label>
          )}
          {dated && preset === 'CUSTOM' && (
            <>
              <label className="space-y-1 text-sm">
                <span className="block text-xs text-muted-foreground">From</span>
                <Input type="date" aria-label="From" value={custom.from} max={today} onChange={(e) => setCustom({ ...custom, from: e.target.value })} />
              </label>
              <label className="space-y-1 text-sm">
                <span className="block text-xs text-muted-foreground">To</span>
                <Input type="date" aria-label="To" value={custom.to} max={today} onChange={(e) => setCustom({ ...custom, to: e.target.value })} />
              </label>
            </>
          )}
          <label className="space-y-1 text-sm">
            <span className="block text-xs text-muted-foreground">Department</span>
            <Select aria-label="Department" className="w-48" value={departmentId} onChange={(e) => (setDepartmentId(e.target.value), setPersonId(''))}>
              <option value="">All departments</option>
              {(departments.data ?? []).map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </Select>
          </label>
          {tab !== 'projects' && people.data && (
            <label className="space-y-1 text-sm">
              <span className="block text-xs text-muted-foreground">{tab === 'tickets' ? 'Assignee' : 'Employee'}</span>
              <Select aria-label="Employee" className="w-48" value={personId} onChange={(e) => setPersonId(e.target.value)}>
                <option value="">Everyone</option>
                {people.data.content.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.fullName}
                  </option>
                ))}
              </Select>
            </label>
          )}
          {tab === 'tasks' && (
            <>
              <label className="space-y-1 text-sm">
                <span className="block text-xs text-muted-foreground">Project</span>
                <Select aria-label="Project" className="w-48" value={projectId} onChange={(e) => setProjectId(e.target.value)}>
                  <option value="">All projects</option>
                  {(projects.data ?? []).map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </Select>
              </label>
              <label className="space-y-1 text-sm">
                <span className="block text-xs text-muted-foreground">Status</span>
                <Select aria-label="Task status" className="w-40" value={taskStatus} onChange={(e) => setTaskStatus(e.target.value)}>
                  <option value="">Any status</option>
                  {TASK_STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {STATUS_LABELS[s]}
                    </option>
                  ))}
                </Select>
              </label>
            </>
          )}
          {tab === 'projects' && (
            <label className="space-y-1 text-sm">
              <span className="block text-xs text-muted-foreground">Status</span>
              <Select aria-label="Project status" className="w-40" value={projectStatus} onChange={(e) => setProjectStatus(e.target.value)}>
                <option value="">Any status</option>
                {PROJECT_STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {PROJECT_STATUS_LABELS[s]}
                  </option>
                ))}
              </Select>
            </label>
          )}
        </div>

        <TabsContent value="tasks" className="mt-6">
          {tab === 'tasks' && <TasksTab query={queries.tasks} ready={rangeReady} />}
        </TabsContent>
        <TabsContent value="workload" className="mt-6">
          {tab === 'workload' && <WorkloadTab query={queries.workload} />}
        </TabsContent>
        <TabsContent value="tickets" className="mt-6">
          {tab === 'tickets' && <TicketsTab query={queries.tickets} ready={rangeReady} />}
        </TabsContent>
        <TabsContent value="projects" className="mt-6">
          {tab === 'projects' && <ProjectsTab query={queries.projects} />}
        </TabsContent>
      </Tabs>
    </div>
  );
}

/** The comparison query: the same filters for the period before the report's own range. */
function previousQuery(query: ReportQuery, range: DateRange | undefined): ReportQuery {
  return range ? { ...query, ...previousRange(range) } : query;
}

function RangeLine({ range }: { range: DateRange | undefined }) {
  if (!range) return null;
  return (
    <p className="mb-4 text-sm text-muted-foreground">
      {rangeLabel(range)} · compared with {rangeLabel(previousRange(range))}
    </p>
  );
}

function TasksTab({ query, ready }: { query: ReportQuery; ready: boolean }) {
  const report = useTaskReport(query, ready);
  const range = report.data?.range;
  const previous = useTaskReport(previousQuery(query, range), range !== undefined);
  return (
    <>
      <RangeLine range={range} />
      <ReportState query={report} label="task report">
        {(data) => <TaskReportView report={data} previous={{ data: previous.data, label: range ? rangeLabel(previousRange(range)) : '' }} />}
      </ReportState>
    </>
  );
}

function TicketsTab({ query, ready }: { query: ReportQuery; ready: boolean }) {
  const report = useTicketReport(query, ready);
  const range = report.data?.range;
  const previous = useTicketReport(previousQuery(query, range), range !== undefined);
  return (
    <>
      <RangeLine range={range} />
      <ReportState query={report} label="ticket report">
        {(data) => <TicketReportView report={data} previous={{ data: previous.data, label: range ? rangeLabel(previousRange(range)) : '' }} />}
      </ReportState>
    </>
  );
}

function WorkloadTab({ query }: { query: ReportQuery }) {
  const report = useWorkloadReport(query);
  return (
    <ReportState query={report} label="workload report">
      {(data) => <WorkloadReportView report={data} />}
    </ReportState>
  );
}

function ProjectsTab({ query }: { query: ReportQuery }) {
  const report = useProjectReport(query);
  return (
    <ReportState query={report} label="project report">
      {(data) => <ProjectReportView report={data} />}
    </ReportState>
  );
}
