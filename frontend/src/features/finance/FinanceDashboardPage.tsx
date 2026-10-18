import { AppWindow, Building, CalendarClock, ChartColumn, ChartPie, ChevronLeft, ChevronRight, Coins, PiggyBank, TrendingDown, TriangleAlert, Wallet } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router';

import { BarGraph } from '@/components/charts/BarGraph';
import { CHART_COLORS, CHART_HIGHLIGHT } from '@/components/charts/chart-theme';
import { ChartTooltip, ChartTooltipRow } from '@/components/charts/ChartTooltip';
import { DonutChart } from '@/components/charts/DonutChart';
import { Delta } from '@/components/common/Delta';
import { EmptyState } from '@/components/common/EmptyState';
import { KpiGrid, KpiProgress, KpiTile } from '@/components/common/KpiCard';
import { Panel, PanelLink } from '@/components/common/Panel';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { ToolSection } from '@/features/modules/ToolSection';
import { formatLocalDate } from '@/lib/format';

import { useFinanceDashboard, type FinanceDashboard, type Licence } from './api';
import { FinanceErrorState, LicenceRenewalBadge, SeatUsage } from './FinanceBadges';
import { CATEGORY_LABELS, formatAud, formatAudShort, formatPercent, monthLabel, shiftMonth } from './finance-meta';
import { FINANCE_PATHS, licenceHref } from './finance-paths';

const MONTH = /^(\d{4})-(\d{2})$/;

/**
 * The Finance Tracker for one month (this month on the business calendar by default, or ?month=2026-09): spend and its
 * change, the 12-month trend, spend by department and category, budgets, renewals in the next 30 days, under-used
 * licences and the annualised software cost. Every figure is computed by the server in AUD; the page only renders it.
 */
export default function FinanceDashboardPage() {
  const [params, setParams] = useSearchParams();
  const match = MONTH.exec(params.get('month') ?? '');
  const requested = match && Number(match[2]) >= 1 && Number(match[2]) <= 12 ? { year: Number(match[1]), month: Number(match[2]) } : null;
  const dashboard = useFinanceDashboard(requested?.year, requested?.month);
  const data = dashboard.data;

  function go(by: number) {
    if (!data) return;
    const next = shiftMonth(data.year, data.month, by);
    setParams({ month: `${next.year}-${String(next.month).padStart(2, '0')}` }, { replace: true });
  }

  return (
    <div className="space-y-6">
      <ToolSection
        title="Dashboard"
        description={data ? `${monthLabel(data.year, data.month, 'long')}, in AUD. As of ${formatLocalDate(data.asOf)}.` : "Spend, budgets, renewals and licence use, in AUD."}
        actions={
          data && (
            <div className="flex items-center gap-1" role="group" aria-label="Month">
              <Button variant="outline" size="icon" aria-label="Previous month" onClick={() => go(-1)}>
                <ChevronLeft />
              </Button>
              <span className="min-w-36 text-center text-sm font-medium" aria-live="polite">
                {monthLabel(data.year, data.month, 'long')}
              </span>
              <Button variant="outline" size="icon" aria-label="Next month" onClick={() => go(1)}>
                <ChevronRight />
              </Button>
            </div>
          )
        }
      />
      {dashboard.isPending ? (
        <DashboardSkeleton />
      ) : dashboard.isError ? (
        <Card>
          <FinanceErrorState error={dashboard.error} title="Couldn't load the dashboard" onRetry={() => void dashboard.refetch()} />
        </Card>
      ) : (
        <DashboardBody data={dashboard.data} />
      )}
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6" role="status" aria-label="Loading the dashboard">
      <KpiGrid>
        {Array.from({ length: 4 }, (_, i) => (
          <KpiTile key={i} label="Loading" value="" loading />
        ))}
      </KpiGrid>
      <Skeleton className="h-72 rounded-xl" />
      <div className="grid gap-6 lg:grid-cols-2">
        <Skeleton className="h-72 rounded-xl" />
        <Skeleton className="h-72 rounded-xl" />
      </div>
    </div>
  );
}

function monthRange(year: number, month: number): string {
  const pad = String(month).padStart(2, '0');
  const last = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return `from=${year}-${pad}-01&to=${year}-${pad}-${last}`;
}

function DashboardBody({ data }: { data: FinanceDashboard }) {
  const label = monthLabel(data.year, data.month, 'long');
  const budgetUsed = data.monthBudgetAud > 0 ? (data.monthSpendAud / data.monthBudgetAud) * 100 : null;
  const range = monthRange(data.year, data.month);
  return (
    <>
      {data.licencesWithoutRate > 0 && (
        <div className="flex flex-col gap-2 rounded-xl border border-status-warning/40 bg-status-warning/5 px-4 py-3 text-sm sm:flex-row sm:items-center" role="alert">
          <TriangleAlert className="size-4 shrink-0 text-status-warning" aria-hidden />
          <p className="flex-1">
            <span className="font-medium">Missing exchange rates. </span>
            {data.licencesWithoutRate === 1 ? '1 licence is' : `${data.licencesWithoutRate.toLocaleString()} licences are`} in a currency without a rate, so the annualised cost leaves{' '}
            {data.licencesWithoutRate === 1 ? 'it' : 'them'} out.
          </p>
          <Button asChild variant="outline" size="sm">
            <Link to={FINANCE_PATHS.rates}>Add exchange rates</Link>
          </Button>
        </div>
      )}

      <KpiGrid aria-label="Finance figures">
        <KpiTile
          label={`Spend in ${monthLabel(data.year, data.month)}`}
          icon={Wallet}
          value={formatAud(data.monthSpendAud)}
          muted={data.monthSpendAud === 0}
          delta={<Delta value={data.changePercent} amount={formatPercent(data.changePercent === null ? null : Math.abs(data.changePercent))} label="vs last month" better={null} variant="chip" />}
          hint={`${data.expenseCount === 1 ? '1 expense' : `${data.expenseCount.toLocaleString()} expenses`} · last month ${formatAud(data.previousMonthSpendAud)}`}
          to={`${FINANCE_PATHS.expenses}?${range}`}
        />
        <KpiTile
          label="Budget used"
          icon={PiggyBank}
          value={formatPercent(budgetUsed)}
          muted={budgetUsed === null}
          critical={data.overBudgetDepartments > 0}
          hint={
            data.monthBudgetAud > 0
              ? `${formatAud(data.monthBudgetAud)} budgeted${data.overBudgetDepartments > 0 ? ` · ${data.overBudgetDepartments} over budget` : ''}`
              : 'No budgets set for this month'
          }
          progress={budgetUsed !== null ? <KpiProgress value={budgetUsed} label="Budget used" /> : undefined}
          to={`${FINANCE_PATHS.budgets}?month=${data.year}-${String(data.month).padStart(2, '0')}`}
        />
        <KpiTile
          label="Annualised software cost"
          icon={AppWindow}
          value={formatAud(data.annualisedSoftwareCostAud)}
          muted={data.annualisedSoftwareCostAud === 0}
          tone={data.licencesWithoutRate > 0 ? 'text-status-warning' : undefined}
          hint={data.licencesWithoutRate > 0 ? `${data.licencesWithoutRate} without an exchange rate left out` : 'Active and trial licences, at today’s rates'}
          to={FINANCE_PATHS.licences}
        />
        <KpiTile
          label="Renewals in 30 days"
          icon={CalendarClock}
          value={data.upcomingRenewalCount.toLocaleString()}
          muted={data.upcomingRenewalCount === 0}
          hint={data.underUsedCount === 1 ? '1 licence under-used' : `${data.underUsedCount.toLocaleString()} licences under-used`}
          to={`${FINANCE_PATHS.licences}?renewalWithinDays=30&sort=renewal,asc`}
        />
      </KpiGrid>

      <TrendPanel data={data} />

      <div className="grid gap-6 lg:grid-cols-2">
        <DepartmentPanel data={data} label={label} range={range} />
        <CategoryPanel data={data} label={label} range={range} />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <LicenceListPanel
          title="Upcoming renewals"
          icon={CalendarClock}
          description={`${data.upcomingRenewalCount.toLocaleString()} active and trial licences renew in the next 30 days`}
          licences={data.upcomingRenewals}
          total={data.upcomingRenewalCount}
          link={`${FINANCE_PATHS.licences}?renewalWithinDays=30&sort=renewal,asc`}
          empty="No renewals in the next 30 days"
          detail={(l) => (
            <span className="text-right">
              <LicenceRenewalBadge licence={l} />
              {l.renewalDate && <span className="block text-xs text-muted-foreground">{formatLocalDate(l.renewalDate)}</span>}
            </span>
          )}
        />
        <LicenceListPanel
          title="Under-used licences"
          icon={TrendingDown}
          description="Fewer than 70% of the paid seats assigned, least used first"
          licences={data.underUsedLicences}
          total={data.underUsedCount}
          link={FINANCE_PATHS.licences}
          empty="Every licence uses at least 70% of its seats"
          detail={(l) => <SeatUsage licence={{ ...l, underUsed: false }} className="w-40" />}
        />
      </div>
    </>
  );
}

function TrendPanel({ data }: { data: FinanceDashboard }) {
  const points = data.trend.map((m) => ({
    label: monthLabel(m.year, m.month),
    value: m.totalAud,
    highlight: m.year === data.year && m.month === data.month,
    full: monthLabel(m.year, m.month, 'long'),
  }));
  const total = data.trend.reduce((sum, m) => sum + m.totalAud, 0);
  return (
    <Panel title="Monthly spend" icon={ChartColumn} description={`The 12 months to ${monthLabel(data.year, data.month, 'long')}: ${formatAud(total)} in all`}>
      <div className="px-4 py-5 sm:px-6">
        {total === 0 ? (
          <EmptyState icon={ChartColumn} title="No spend in these 12 months" className="py-10" />
        ) : (
          <>
            <BarGraph
              data={points}
              height={240}
              formatValue={formatAudShort}
              renderTooltip={(m) => (
                <ChartTooltip title={m.full}>
                  <ChartTooltipRow color={m.highlight ? CHART_HIGHLIGHT : CHART_COLORS[0]} label="Spend" value={formatAud(m.value)} />
                </ChartTooltip>
              )}
            />
            <table className="sr-only">
              <caption>Spend per month in AUD</caption>
              <thead>
                <tr>
                  <th>Month</th>
                  <th>Spend</th>
                </tr>
              </thead>
              <tbody>
                {points.map((m) => (
                  <tr key={m.full}>
                    <td>{m.full}</td>
                    <td>{formatAud(m.value)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </div>
    </Panel>
  );
}

function DepartmentPanel({ data, label, range }: { data: FinanceDashboard; label: string; range: string }) {
  const top = Math.max(1, ...data.byDepartment.map((d) => d.totalAud));
  return (
    <Panel title="Spend by department" icon={Building} description={label} action={<PanelLink to={`${FINANCE_PATHS.budgets}?month=${data.year}-${String(data.month).padStart(2, '0')}`}>Budgets</PanelLink>}>
      {data.byDepartment.length === 0 ? (
        <EmptyState icon={Building} title={`No spend in ${label}`} className="py-10" />
      ) : (
        <ul className="divide-y" aria-label="Spend by department">
          {data.byDepartment.map((d) => (
            <li key={d.department?.id ?? 'none'} className="flex items-center gap-3 px-6 py-2.5">
              {d.department ? (
                <Link to={`${FINANCE_PATHS.expenses}?${range}&departmentId=${d.department.id}`} className="w-40 shrink-0 truncate text-sm hover:underline">
                  {d.department.name}
                </Link>
              ) : (
                <span className="w-40 shrink-0 truncate text-sm text-muted-foreground">No department</span>
              )}
              <span className="hidden h-1.5 flex-1 overflow-hidden rounded-full bg-muted sm:block" aria-hidden>
                <span className="block h-full rounded-full bg-primary" style={{ width: `${(d.totalAud / top) * 100}%` }} />
              </span>
              <span className="ml-auto text-right text-sm font-semibold tabular-nums">{formatAud(d.totalAud)}</span>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

function CategoryPanel({ data, label, range }: { data: FinanceDashboard; label: string; range: string }) {
  return (
    <Panel title="Spend by category" icon={ChartPie} description={label}>
      {data.byCategory.length === 0 ? (
        <EmptyState icon={ChartPie} title={`No spend in ${label}`} className="py-10" />
      ) : (
        <div className="space-y-4 px-6 py-5">
          <DonutChart
            ariaLabel="Spend by category, in AUD"
            centerLabel="spent"
            centerValue={formatAudShort(data.monthSpendAud)}
            formatValue={formatAud}
            segments={data.byCategory.map((c, i) => ({ key: c.category, label: CATEGORY_LABELS[c.category], value: c.totalAud, color: CHART_COLORS[i % CHART_COLORS.length]! }))}
          />
          <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs" aria-label="Expenses by category">
            {data.byCategory.map((c) => (
              <li key={c.category}>
                <Link to={`${FINANCE_PATHS.expenses}?${range}&category=${c.category}`} className="text-muted-foreground hover:text-foreground hover:underline">
                  <Coins className="mr-1 inline size-3" aria-hidden />
                  {CATEGORY_LABELS[c.category]}: {c.expenses === 1 ? '1 expense' : `${c.expenses} expenses`}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Panel>
  );
}

function LicenceListPanel({
  title,
  icon,
  description,
  licences,
  total,
  link,
  empty,
  detail,
}: {
  title: string;
  icon: typeof CalendarClock;
  description: string;
  licences: Licence[];
  total: number;
  link: string;
  empty: string;
  detail: (licence: Licence) => ReactNode;
}) {
  const more = total - licences.length;
  return (
    <Panel title={title} icon={icon} description={description} action={<PanelLink to={link}>All licences</PanelLink>}>
      {licences.length === 0 ? (
        <EmptyState icon={icon} title={empty} className="py-10" />
      ) : (
        <ul className="divide-y" aria-label={title}>
          {licences.map((l) => (
            <li key={l.id} className="flex items-center gap-3 px-6 py-3">
              <div className="min-w-0 flex-1">
                <Link to={licenceHref(l.id)} className="block truncate text-sm font-medium hover:underline">
                  {l.name}
                </Link>
                <span className="block truncate text-xs text-muted-foreground">
                  {[l.vendor?.name, l.annualCostAud !== null ? `${formatAud(l.annualCostAud)} a year` : null].filter(Boolean).join(' · ') || '—'}
                </span>
              </div>
              {detail(l)}
            </li>
          ))}
        </ul>
      )}
      {more > 0 && <p className="border-t px-6 py-2.5 text-xs text-muted-foreground">And {more.toLocaleString()} more</p>}
    </Panel>
  );
}
