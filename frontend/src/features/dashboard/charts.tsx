import { BarGraph } from '@/components/charts/BarGraph';
import { ChartTooltip, ChartTooltipRow } from '@/components/charts/ChartTooltip';
import { CHART_HIGHLIGHT } from '@/components/charts/chart-theme';
import { DonutChart } from '@/components/charts/DonutChart';
import { TrendLineChart } from '@/components/charts/TrendLineChart';
import { parseLocalDate, STATUS_LABELS } from '@/features/tasks/task-meta';
import type { TaskStatus } from '@/features/tasks/types';

import type { DepartmentRow, StatusSlice, WeekPoint } from './api';

/** Status colours reuse the semantic tokens of TaskStatusBadge; the legend always names each status. */
const STATUS_COLOR: Record<TaskStatus, string> = {
  TODO: 'var(--color-gray-400)',
  IN_PROGRESS: 'var(--chart-1)',
  BLOCKED: 'var(--status-danger)',
  IN_REVIEW: 'var(--status-warning)',
  COMPLETED: 'var(--status-success)',
  CANCELLED: 'var(--color-gray-300)',
};

const weekFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' });

export function StatusDonut({ slices }: { slices: StatusSlice[] }) {
  return (
    <DonutChart
      ariaLabel="Tasks by status"
      centerLabel="tasks"
      segments={slices.map((slice) => ({ key: slice.status, label: STATUS_LABELS[slice.status], value: slice.count, color: STATUS_COLOR[slice.status] }))}
    />
  );
}

type WeekDatum = WeekPoint & { label: string; value: number; highlight: boolean };

function weekData(weeks: WeekPoint[], currentWeekStart: string): WeekDatum[] {
  return weeks.map((week) => ({ ...week, label: weekFormat.format(parseLocalDate(week.weekStart)), value: week.completed, highlight: week.weekStart === currentWeekStart }));
}

function percentText(value: number | null): string {
  return value === null ? '—' : `${value}%`;
}

/** Completions per week; the current (partial) week is the amber bar. */
export function WeeklyCompletionChart({ weeks, currentWeekStart }: { weeks: WeekPoint[]; currentWeekStart: string }) {
  const data = weekData(weeks, currentWeekStart);
  return (
    <>
      <BarGraph
        data={data}
        height={256}
        renderTooltip={(week) => (
          <ChartTooltip title={week.highlight ? `This week (from ${week.label})` : `Week of ${week.label}`}>
            <ChartTooltipRow color={week.highlight ? CHART_HIGHLIGHT : 'var(--chart-1)'} label="Completed" value={week.completed} />
            <ChartTooltipRow label="On time" value={percentText(week.onTimePercent)} />
          </ChartTooltip>
        )}
      />
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
              <td>{percentText(week.onTimePercent)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

/** On-time share of each week's completions as a smooth trend; weeks without completions leave a gap. */
export function OnTimeTrendChart({ weeks }: { weeks: WeekPoint[] }) {
  const data = weeks.map((week) => ({ label: weekFormat.format(parseLocalDate(week.weekStart)), onTime: week.onTimePercent, week }));
  return (
    <TrendLineChart
      data={data}
      series={[{ key: 'onTime', label: 'On time' }]}
      height={200}
      valueDomain={[0, 100]}
      formatValue={(v) => `${v}%`}
      yAxisWidth={44}
      renderTooltip={(d) => (
        <ChartTooltip title={`Week of ${d.label}`}>
          <ChartTooltipRow color="var(--chart-1)" label="On time" value={percentText(d.onTime)} />
          <ChartTooltipRow label="Completed" value={`${d.week.onTime} of ${d.week.completed}`} />
        </ChartTooltip>
      )}
    />
  );
}

/** Horizontal bars of department workload %, with a marker at 100% capacity. Departments without people are skipped. */
export function DepartmentWorkloadChart({ departments }: { departments: DepartmentRow[] }) {
  const data = departments
    .filter((d) => d.workloadPercent !== null)
    .map((d) => ({ label: d.department.name, value: d.workloadPercent ?? 0, row: d }));
  return (
    <BarGraph
      data={data}
      layout="rows"
      formatValue={(v) => `${v}%`}
      valueDomain={[0, (max: number) => Math.max(100, Math.ceil(max / 20) * 20)]}
      referenceLine={{ value: 100, label: 'Capacity' }}
      renderTooltip={({ row }) => (
        <ChartTooltip title={row.department.name}>
          <ChartTooltipRow color="var(--chart-1)" label="Workload" value={`${row.workloadPercent}%`} />
          <ChartTooltipRow label="People" value={row.people} />
          <ChartTooltipRow label="Open · overdue" value={`${row.openTasks} · ${row.overdue}`} />
        </ChartTooltip>
      )}
    />
  );
}
