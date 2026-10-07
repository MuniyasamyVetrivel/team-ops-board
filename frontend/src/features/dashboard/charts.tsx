import type { ReactNode } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { TaskStatusBadge } from '@/features/tasks/TaskBadges';
import { parseLocalDate } from '@/features/tasks/task-meta';
import type { TaskStatus } from '@/features/tasks/types';

import type { DepartmentRow, StatusSlice, WeekPoint } from './api';

/** Status colours reuse the semantic tokens of TaskStatusBadge; the legend always shows icon + label + count. */
const STATUS_COLOR: Record<TaskStatus, string> = {
  TODO: 'var(--status-neutral)',
  IN_PROGRESS: 'var(--primary)',
  BLOCKED: 'var(--status-danger)',
  IN_REVIEW: 'var(--status-warning)',
  COMPLETED: 'var(--status-success)',
  CANCELLED: 'var(--status-neutral)',
};

const AXIS = { fontSize: 12, fill: 'var(--muted-foreground)' };
const weekFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' });

/** Initial size so the chart renders before (and without) a measured container, e.g. in tests. */
const INITIAL = { width: 480, height: 240 };

function TooltipBox({ title, children }: { title: ReactNode; children: ReactNode }) {
  return (
    <div className="rounded-lg border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-md">
      <p className="mb-1 font-medium">{title}</p>
      {children}
    </div>
  );
}

export function StatusDonut({ slices, completedWindowDays }: { slices: StatusSlice[]; completedWindowDays: number }) {
  const total = slices.reduce((sum, slice) => sum + slice.count, 0);
  const data = slices.filter((slice) => slice.count > 0);
  return (
    <div className="flex flex-col items-center gap-4 sm:flex-row lg:flex-col xl:flex-row">
      <div className="relative size-44 shrink-0">
        <ResponsiveContainer width="100%" height="100%" initialDimension={{ width: 176, height: 176 }}>
          <PieChart>
            <Pie data={data} dataKey="count" nameKey="status" innerRadius="68%" outerRadius="100%" paddingAngle={data.length > 1 ? 2 : 0} stroke="var(--card)" strokeWidth={2} isAnimationActive={false}>
              {data.map((slice) => (
                <Cell key={slice.status} fill={STATUS_COLOR[slice.status]} />
              ))}
            </Pie>
            <Tooltip
              content={({ active, payload }) => {
                const slice = payload?.[0]?.payload as StatusSlice | undefined;
                if (!active || !slice) return null;
                return (
                  <TooltipBox title={<TaskStatusBadge status={slice.status} />}>
                    <span className="tabular-nums">
                      {slice.count} task{slice.count === 1 ? '' : 's'} · {total ? Math.round((slice.count / total) * 100) : 0}%
                    </span>
                  </TooltipBox>
                );
              }}
            />
          </PieChart>
        </ResponsiveContainer>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-2xl font-semibold tabular-nums">{total}</span>
          <span className="text-xs text-muted-foreground">tasks</span>
        </div>
      </div>
      <ul className="w-full space-y-1.5 text-sm" aria-label="Tasks by status">
        {slices.map((slice) => (
          <li key={slice.status} className="flex items-center justify-between gap-3">
            <span className="flex items-center gap-2">
              <span className="size-2.5 rounded-sm" style={{ background: STATUS_COLOR[slice.status] }} aria-hidden />
              <TaskStatusBadge status={slice.status} />
              {slice.status === 'COMPLETED' && <span className="text-xs text-muted-foreground">last {completedWindowDays} days</span>}
            </span>
            <span className="font-medium tabular-nums">{slice.count}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function WeeklyCompletionChart({ weeks }: { weeks: WeekPoint[] }) {
  const data = weeks.map((week) => ({ ...week, label: weekFormat.format(parseLocalDate(week.weekStart)) }));
  return (
    <>
      <div className="h-60">
        <ResponsiveContainer width="100%" height="100%" initialDimension={INITIAL}>
          <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
            <CartesianGrid vertical={false} stroke="var(--border)" />
            <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={false} />
            <YAxis allowDecimals={false} tick={AXIS} tickLine={false} axisLine={false} />
            <Tooltip
              cursor={{ fill: 'var(--muted)' }}
              content={({ active, payload }) => {
                const week = payload?.[0]?.payload as (WeekPoint & { label: string }) | undefined;
                if (!active || !week) return null;
                return (
                  <TooltipBox title={`Week of ${week.label}`}>
                    <p className="tabular-nums">{week.completed} completed</p>
                    <p className="text-muted-foreground tabular-nums">
                      On time: {week.onTimePercent === null ? '—' : `${week.onTimePercent}%`}
                    </p>
                  </TooltipBox>
                );
              }}
            />
            <Bar dataKey="completed" name="Completed" fill="var(--primary)" radius={[4, 4, 0, 0]} maxBarSize={36} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <table className="sr-only">
        <caption>Tasks completed per week</caption>
        <thead>
          <tr>
            <th>Week of</th>
            <th>Completed</th>
            <th>On time</th>
          </tr>
        </thead>
        <tbody>
          {data.map((week) => (
            <tr key={week.weekStart}>
              <td>{week.label}</td>
              <td>{week.completed}</td>
              <td>{week.onTimePercent === null ? '—' : `${week.onTimePercent}%`}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

/** Horizontal bars of department workload %, with a marker at 100% capacity. Departments without people are skipped. */
export function DepartmentWorkloadChart({ departments }: { departments: DepartmentRow[] }) {
  const data = departments
    .filter((d) => d.workloadPercent !== null)
    .map((d) => ({ name: d.department.name, percent: d.workloadPercent ?? 0, row: d }));
  const height = Math.max(160, data.length * 34 + 32);
  return (
    <div style={{ height }}>
      <ResponsiveContainer width="100%" height="100%" initialDimension={{ width: INITIAL.width, height }}>
        <BarChart data={data} layout="vertical" margin={{ top: 4, right: 24, bottom: 0, left: 8 }}>
          <CartesianGrid horizontal={false} stroke="var(--border)" />
          <XAxis type="number" tick={AXIS} tickLine={false} axisLine={false} unit="%" domain={[0, (max: number) => Math.max(100, Math.ceil(max / 20) * 20)]} />
          <YAxis type="category" dataKey="name" tick={AXIS} tickLine={false} axisLine={false} width={120} />
          <ReferenceLine x={100} stroke="var(--status-danger)" strokeDasharray="4 4" label={{ value: 'Capacity', position: 'top', fontSize: 11, fill: 'var(--muted-foreground)' }} />
          <Tooltip
            cursor={{ fill: 'var(--muted)' }}
            content={({ active, payload }) => {
              const item = payload?.[0]?.payload as { row: DepartmentRow } | undefined;
              if (!active || !item) return null;
              const { row } = item;
              return (
                <TooltipBox title={row.department.name}>
                  <p className="tabular-nums">Workload {row.workloadPercent}%</p>
                  <p className="text-muted-foreground tabular-nums">
                    {row.people} people · {row.openTasks} open · {row.overdue} overdue
                  </p>
                </TooltipBox>
              );
            }}
          />
          <Bar dataKey="percent" name="Workload %" fill="var(--primary)" radius={[0, 4, 4, 0]} maxBarSize={20} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
