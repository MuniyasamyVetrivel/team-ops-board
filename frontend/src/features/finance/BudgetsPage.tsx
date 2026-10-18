import { ChevronLeft, ChevronRight, CircleAlert, CircleCheck, CircleDashed, PiggyBank, Plus } from 'lucide-react';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { KpiGrid, KpiProgress, KpiTile } from '@/components/common/KpiCard';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useTool } from '@/features/modules/tool-context';
import { ToolSection } from '@/features/modules/ToolSection';
import { cn } from '@/lib/utils';

import { useBudgets, useBudgetSummary, useFinanceContext, type Budget, type BudgetLine } from './api';
import { BudgetFormDialog } from './BudgetFormDialog';
import { FinanceErrorState } from './FinanceBadges';
import { formatAud, formatPercent, monthLabel, monthOf, shiftMonth } from './finance-meta';
import { FINANCE_PATHS } from './finance-paths';

const MONTH = /^(\d{4})-(\d{2})$/;

/** The month shown: ?month=2026-10 (from the dashboard), otherwise this month on the business calendar. */
function useMonth(today: string | undefined) {
  const [params, setParams] = useSearchParams();
  const match = MONTH.exec(params.get('month') ?? '');
  const fromUrl = match ? { year: Number(match[1]), month: Number(match[2]) } : null;
  const current = fromUrl && fromUrl.month >= 1 && fromUrl.month <= 12 ? fromUrl : today ? monthOf(today) : null;
  const set = (next: { year: number; month: number }) =>
    setParams(
      (p) => {
        const updated = new URLSearchParams(p);
        updated.set('month', `${next.year}-${String(next.month).padStart(2, '0')}`);
        return updated;
      },
      { replace: true },
    );
  return [current, set] as const;
}

/** Department budgets in AUD against that month's actual spend (expenses by date, at their stored AUD amounts). */
export default function BudgetsPage() {
  const context = useFinanceContext();
  const [month, setMonth] = useMonth(context.data?.today);

  if (!month) {
    if (context.isError) return <FinanceErrorState error={context.error} title="Couldn't load budgets" onRetry={() => void context.refetch()} />;
    return <Skeleton className="h-96 rounded-xl" role="status" aria-label="Loading budgets" />;
  }
  return <BudgetsForMonth year={month.year} month={month.month} onMonth={setMonth} />;
}

function BudgetsForMonth({ year, month, onMonth }: { year: number; month: number; onMonth: (next: { year: number; month: number }) => void }) {
  const { canEdit } = useTool();
  const summary = useBudgetSummary(year, month);
  const budgets = useBudgets({ year, month, size: 100 });
  const [editing, setEditing] = useState<{ budget?: Budget; departmentId?: number } | null>(null);
  const byId = new Map((budgets.data?.content ?? []).map((b) => [b.id, b]));
  const label = monthLabel(year, month, 'long');
  const pad = String(month).padStart(2, '0');
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();

  return (
    <div className="space-y-4">
      <ToolSection
        title="Budgets"
        description="One budget per department per month, in AUD. Actual spend is the month's expenses at their stored AUD amounts."
        actions={
          <>
            <div className="flex items-center gap-1" role="group" aria-label="Month">
              <Button variant="outline" size="icon" aria-label="Previous month" onClick={() => onMonth(shiftMonth(year, month, -1))}>
                <ChevronLeft />
              </Button>
              <span className="min-w-36 text-center text-sm font-medium" aria-live="polite">
                {label}
              </span>
              <Button variant="outline" size="icon" aria-label="Next month" onClick={() => onMonth(shiftMonth(year, month, 1))}>
                <ChevronRight />
              </Button>
            </div>
            {canEdit && (
              <Button onClick={() => setEditing({})}>
                <Plus aria-hidden />
                New budget
              </Button>
            )}
          </>
        }
      />

      {summary.isPending ? (
        <div className="space-y-4" role="status" aria-label="Loading budgets">
          <KpiGrid>
            {Array.from({ length: 4 }, (_, i) => (
              <KpiTile key={i} label="Loading" value="" loading />
            ))}
          </KpiGrid>
          <Skeleton className="h-64 rounded-xl" />
        </div>
      ) : summary.isError ? (
        <Card>
          <FinanceErrorState error={summary.error} title="Couldn't load budgets" onRetry={() => void summary.refetch()} />
        </Card>
      ) : (
        <>
          <KpiGrid aria-label="Budget figures">
            <KpiTile label="Budgeted" icon={PiggyBank} value={formatAud(summary.data.totalBudget)} muted={summary.data.totalBudget === 0} hint={label} />
            <KpiTile
              label="Spent"
              value={formatAud(summary.data.totalActual)}
              muted={summary.data.totalActual === 0}
              hint={`${formatAud(summary.data.totalActualBudgeted)} in departments with a budget`}
              to={`${FINANCE_PATHS.expenses}?from=${year}-${pad}-01&to=${year}-${pad}-${lastDay}`}
            />
            <KpiTile
              label="Used"
              value={formatPercent(summary.data.totalBudget > 0 ? (summary.data.totalActualBudgeted / summary.data.totalBudget) * 100 : null)}
              muted={summary.data.totalBudget === 0}
              hint="Spend in budgeted departments ÷ their budgets"
              progress={summary.data.totalBudget > 0 ? <KpiProgress value={(summary.data.totalActualBudgeted / summary.data.totalBudget) * 100} label="Budget used" /> : undefined}
            />
            <KpiTile
              label="Over budget"
              icon={CircleAlert}
              value={summary.data.overBudgetDepartments.toLocaleString()}
              muted={summary.data.overBudgetDepartments === 0}
              critical={summary.data.overBudgetDepartments > 0}
              hint={summary.data.overBudgetDepartments === 1 ? '1 department' : `${summary.data.overBudgetDepartments.toLocaleString()} departments`}
            />
          </KpiGrid>
          <Card>
            {summary.data.lines.length === 0 ? (
              <EmptyState
                icon={PiggyBank}
                title={`No budgets or spend in ${label}`}
                description={canEdit ? 'Set a budget for each department to compare it with what they spend.' : undefined}
              />
            ) : (
              <BudgetTable lines={summary.data.lines} totals={summary.data} canEdit={canEdit} ready={budgets.isSuccess} dimmed={summary.isPlaceholderData} onEdit={(line) => setEditing(line.budgetId ? { budget: byId.get(line.budgetId) } : { departmentId: line.department?.id })} />
            )}
          </Card>
        </>
      )}
      {canEdit && (
        <BudgetFormDialog
          open={editing !== null}
          onOpenChange={(open) => !open && setEditing(null)}
          budget={editing?.budget}
          departmentId={editing?.departmentId}
          year={year}
          month={month}
        />
      )}
    </div>
  );
}

/** {@code ready}: the month's budget records (with their versions) have loaded, so an existing budget can be edited. */
function BudgetTable({ lines, totals, canEdit, ready, dimmed, onEdit }: { lines: BudgetLine[]; totals: { totalBudget: number; totalActual: number }; canEdit: boolean; ready: boolean; dimmed: boolean; onEdit: (line: BudgetLine) => void }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Department</TableHead>
          <TableHead className="text-right">Budget</TableHead>
          <TableHead className="text-right">Spent</TableHead>
          <TableHead className="text-right">Remaining</TableHead>
          <TableHead>Used</TableHead>
          <TableHead>Status</TableHead>
          {canEdit && (
            <TableHead>
              <span className="sr-only">Actions</span>
            </TableHead>
          )}
        </TableRow>
      </TableHeader>
      <TableBody className={cn(dimmed && 'opacity-60')}>
        {lines.map((line) => (
          <TableRow key={line.department?.id ?? 'none'}>
            <TableCell className="font-medium">
              {line.department?.name ?? <span className="text-muted-foreground">No department</span>}
            </TableCell>
            <TableCell className="text-right tabular-nums">{line.budget === null ? <span className="text-muted-foreground">—</span> : formatAud(line.budget)}</TableCell>
            <TableCell className="text-right tabular-nums">
              {line.department ? (
                <Link to={`${FINANCE_PATHS.expenses}?departmentId=${line.department.id}`} className="hover:underline">
                  {formatAud(line.actual)}
                </Link>
              ) : (
                formatAud(line.actual)
              )}
            </TableCell>
            <TableCell className="text-right tabular-nums">
              {line.budget === null ? <span className="text-muted-foreground">—</span> : line.overBudget ? <span className="text-status-danger">−{formatAud(line.overBy)}</span> : formatAud(line.remaining)}
            </TableCell>
            <TableCell className="min-w-36">
              {line.usedPercent === null ? (
                <span className="text-sm text-muted-foreground">—</span>
              ) : (
                <div className="flex items-center gap-2">
                  <span className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted" aria-hidden>
                    <span className={cn('block h-full rounded-full', line.overBudget ? 'bg-status-danger' : line.usedPercent >= 90 ? 'bg-status-warning' : 'bg-primary')} style={{ width: `${Math.min(100, line.usedPercent)}%` }} />
                  </span>
                  <span className="w-14 text-right text-xs tabular-nums">{formatPercent(line.usedPercent)}</span>
                </div>
              )}
            </TableCell>
            <TableCell>
              <BudgetStatus line={line} />
            </TableCell>
            {canEdit && (
              <TableCell className="text-right">
                {line.department && (
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={line.budgetId !== null && !ready}
                    aria-label={`${line.budgetId ? 'Edit' : 'Set budget'} for ${line.department.name}`}
                    onClick={() => onEdit(line)}
                  >
                    {line.budgetId ? 'Edit' : 'Set budget'}
                  </Button>
                )}
              </TableCell>
            )}
          </TableRow>
        ))}
        <TableRow className="bg-muted/40 hover:bg-muted/40">
          <TableCell className="font-medium">Total</TableCell>
          <TableCell className="text-right font-medium tabular-nums">{formatAud(totals.totalBudget)}</TableCell>
          <TableCell className="text-right font-medium tabular-nums">{formatAud(totals.totalActual)}</TableCell>
          <TableCell colSpan={canEdit ? 4 : 3} />
        </TableRow>
      </TableBody>
    </Table>
  );
}

/** Over budget, within it, or spend without a budget: always a label and an icon. */
function BudgetStatus({ line }: { line: BudgetLine }) {
  if (line.budget === null) {
    return (
      <Badge tone="neutral">
        <CircleDashed aria-hidden />
        No budget
      </Badge>
    );
  }
  if (line.overBudget) {
    return (
      <Badge tone="danger">
        <CircleAlert aria-hidden />
        Over budget
      </Badge>
    );
  }
  return (
    <Badge tone="success">
      <CircleCheck aria-hidden />
      Within budget
    </Badge>
  );
}
