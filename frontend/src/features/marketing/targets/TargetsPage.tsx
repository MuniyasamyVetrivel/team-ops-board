import { CircleAlert, CircleCheck, Hourglass, ListPlus, Pencil, Plus, Settings2, Target, Zap, type LucideIcon } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { cn } from '@/lib/utils';

import type { TargetStatus } from '../api';
import { MarketingFilterBar } from '../components/MarketingFilterBar';
import { TargetBar, TargetProgress, TargetStatusBadge } from '../components/TargetProgress';
import { formatMetric, formatPercent, periodLabel } from '../marketing-format';
import { TARGET_STATUS_LABELS } from '../marketing-meta';
import { useMarketingFilters } from '../use-marketing-filters';
import { useMonthlyTargets, type TargetItem, type TypeRef } from './api';
import { SetTargetsSheet } from './SetTargetsSheet';
import { ACTUAL_SOURCE_LABELS, UNIT_FORMATS } from './target-meta';
import { TargetFormDialog } from './TargetFormDialog';
import { TargetTrendPanel } from './TargetTrendPanel';
import { TargetTypesSheet } from './TargetTypesSheet';

type Editing = { target: TargetItem } | { type: TypeRef } | null;

/**
 * Marketing Targets (brief sections 30–34): the month's targets as cards and as a table, with progress, remaining and
 * status computed by the server, plus each type's monthly history.
 */
export default function TargetsPage() {
  const { user } = useAuth();
  const { context, filters, isCurrentPeriod } = useMarketingFilters();
  const [status, setStatus] = useState('');
  const [view, setView] = useState('cards');
  const [editing, setEditing] = useState<Editing>(null);
  const [settingTargets, setSettingTargets] = useState(false);
  const [managingTypes, setManagingTypes] = useState(false);
  const canEdit = hasPermission(user, 'TARGET_EDIT');
  const canManageTypes = hasPermission(user, 'MARKETING_EDIT');

  const targets = useMonthlyTargets(filters ? { month: filters.month, year: filters.year, ownerId: filters.ownerId ?? undefined, status: status ? [status as TargetStatus] : undefined } : null);
  const current = context.data?.currentPeriod;
  const planned = filters !== null && current !== undefined && (filters.year > current.year || (filters.year === current.year && filters.month > current.month));
  const label = filters ? periodLabel(filters.month, filters.year) : '';
  const allTypes: TypeRef[] = targets.data ? [...targets.data.targets.map((t) => t.type), ...targets.data.typesWithoutTarget] : [];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Marketing Targets"
        description={filters ? `${label}${planned ? ' · planned' : isCurrentPeriod ? ' · this month' : ''}` : 'Monthly targets versus actuals'}
        actions={
          <div className="flex flex-wrap gap-2">
            {canManageTypes && (
              <Button variant="outline" onClick={() => setManagingTypes(true)}>
                <Settings2 aria-hidden />
                Target types
              </Button>
            )}
            {canEdit && (
              <Button disabled={!targets.data} onClick={() => setSettingTargets(true)}>
                <ListPlus aria-hidden />
                Set targets
              </Button>
            )}
          </div>
        }
      />
      <MarketingFilterBar />

      {targets.isError ? (
        <Card>
          <ErrorState error={targets.error} title="Couldn't load targets" onRetry={() => void targets.refetch()} />
        </Card>
      ) : !targets.data || !filters ? (
        <div className="space-y-4" role="status" aria-label="Loading targets">
          <Skeleton className="h-12 max-w-xl rounded-xl" />
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-36 rounded-xl" />
            ))}
          </div>
        </div>
      ) : (
        <>
          <section aria-label={`Target status for ${label}`} className="flex flex-wrap items-center gap-2">
            <SummaryPill icon={Target} label="Targets" value={targets.data.summary.total} />
            <SummaryPill icon={CircleCheck} label={TARGET_STATUS_LABELS.ACHIEVED} value={targets.data.summary.achieved} tone="text-status-success" />
            <SummaryPill icon={Hourglass} label={TARGET_STATUS_LABELS.IN_PROGRESS} value={targets.data.summary.inProgress} tone="text-status-warning" />
            <SummaryPill icon={CircleAlert} label={TARGET_STATUS_LABELS.BEHIND} value={targets.data.summary.behind} tone="text-status-danger" />
            <Select aria-label="Status" className="ml-auto w-44" value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All statuses</option>
              {(['ACHIEVED', 'IN_PROGRESS', 'BEHIND'] as const).map((s) => (
                <option key={s} value={s}>
                  {TARGET_STATUS_LABELS[s]}
                </option>
              ))}
            </Select>
          </section>

          <Card className={cn(targets.isPlaceholderData && 'opacity-60')}>
            <Tabs value={view} onValueChange={setView}>
              <TabsList>
                <TabsTrigger value="cards">Cards</TabsTrigger>
                <TabsTrigger value="table">Table</TabsTrigger>
              </TabsList>
              {targets.data.targets.length === 0 ? (
                <EmptyState
                  icon={Target}
                  title={status || filters.ownerId !== null ? 'No targets match these filters' : `No targets for ${label} yet`}
                  description={canEdit && !status ? 'Set this month’s targets to start tracking progress.' : undefined}
                  action={
                    canEdit && !status && targets.data.typesWithoutTarget.length > 0 ? (
                      <Button onClick={() => setSettingTargets(true)}>
                        <ListPlus aria-hidden />
                        Set targets
                      </Button>
                    ) : undefined
                  }
                />
              ) : (
                <>
                  <TabsContent value="cards" className="grid gap-3 p-4 sm:grid-cols-2 xl:grid-cols-3">
                    {targets.data.targets.map((target) => (
                      <TargetProgress
                        key={target.id}
                        label={target.type.name}
                        target={target.targetValue}
                        actual={target.actual}
                        achievementPct={target.achievementPct}
                        remaining={target.remaining}
                        status={target.status}
                        format={UNIT_FORMATS[target.type.unit]}
                        hint={<ActualHint target={target} />}
                        action={
                          target.editable ? (
                            <Button variant="ghost" size="icon" className="size-7" aria-label={`Edit ${target.type.name} target`} onClick={() => setEditing({ target })}>
                              <Pencil aria-hidden />
                            </Button>
                          ) : undefined
                        }
                      />
                    ))}
                  </TabsContent>
                  <TabsContent value="table">
                    <TargetTable targets={targets.data.targets} onEdit={(target) => setEditing({ target })} />
                  </TabsContent>
                </>
              )}
            </Tabs>
            {canEdit && targets.data.typesWithoutTarget.length > 0 && targets.data.targets.length > 0 && (
              <div className="flex flex-wrap items-center gap-2 border-t px-4 py-3 text-sm text-muted-foreground">
                <span>No target yet:</span>
                {targets.data.typesWithoutTarget.map((type) => (
                  <Button key={type.id} variant="outline" size="sm" onClick={() => setEditing({ type })}>
                    <Plus aria-hidden />
                    {type.name}
                  </Button>
                ))}
              </div>
            )}
          </Card>

          {allTypes.length > 0 && <TargetTrendPanel key={allTypes[0]!.id} types={allTypes} year={filters.year} />}

          <TargetFormDialog
            open={editing !== null}
            onOpenChange={(open) => !open && setEditing(null)}
            target={editing && 'target' in editing ? editing.target : undefined}
            type={editing && 'type' in editing ? editing.type : undefined}
            month={filters.month}
            year={filters.year}
            planned={planned}
          />
          <SetTargetsSheet open={settingTargets} onOpenChange={setSettingTargets} month={filters.month} year={filters.year} types={targets.data.typesWithoutTarget} />
        </>
      )}
      {canManageTypes && <TargetTypesSheet open={managingTypes} onOpenChange={setManagingTypes} />}
    </div>
  );
}

function SummaryPill({ icon: Icon, label, value, tone }: { icon: LucideIcon; label: string; value: number; tone?: string }) {
  return (
    <span className="inline-flex items-center gap-2 rounded-lg border bg-card px-3 py-1.5 text-sm shadow-xs">
      <Icon className={cn('size-4', tone ?? 'text-muted-foreground')} aria-hidden />
      <span className="text-muted-foreground">{label}</span>
      <span className="font-semibold tabular-nums">{value}</span>
    </span>
  );
}

/** Where the actual comes from, in words. */
function ActualHint({ target }: { target: TargetItem }) {
  if (target.status === null) return <>Actual is recorded once the month starts</>;
  if (target.actualOrigin === 'AUTOMATIC')
    return (
      <span className="inline-flex items-center gap-1">
        <Zap className="size-3 text-primary" aria-hidden />
        Automatic: {ACTUAL_SOURCE_LABELS[target.type.actualSource].toLowerCase()}
      </span>
    );
  if (target.actualOrigin === 'NONE') return <>No actual recorded yet</>;
  return <>Entered by hand</>;
}

function TargetTable({ targets, onEdit }: { targets: TargetItem[]; onEdit: (target: TargetItem) => void }) {
  const anyEditable = targets.some((t) => t.editable);
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Target type</TableHead>
          <TableHead className="text-right">Target</TableHead>
          <TableHead className="text-right">Actual</TableHead>
          <TableHead className="text-right">Remaining</TableHead>
          <TableHead className="min-w-44">Achievement</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Owner</TableHead>
          {anyEditable && (
            <TableHead>
              <span className="sr-only">Actions</span>
            </TableHead>
          )}
        </TableRow>
      </TableHeader>
      <TableBody>
        {targets.map((target) => {
          const format = UNIT_FORMATS[target.type.unit];
          return (
            <TableRow key={target.id}>
              <TableCell>
                <p className="font-medium">{target.type.name}</p>
                <p className="text-xs text-muted-foreground">
                  <ActualHint target={target} />
                </p>
              </TableCell>
              <TableCell className="text-right tabular-nums">{formatMetric(target.targetValue, format)}</TableCell>
              <TableCell className="text-right tabular-nums">{target.status === null ? '—' : formatMetric(target.actual ?? 0, format)}</TableCell>
              <TableCell className="text-right tabular-nums">{formatMetric(target.remaining, format)}</TableCell>
              <TableCell>
                <div className="flex items-center gap-2">
                  <TargetBar label={`${target.type.name} achievement`} achievementPct={target.achievementPct} status={target.status} className="flex-1" />
                  <span className="w-14 text-right text-sm tabular-nums">{formatPercent(target.achievementPct)}</span>
                </div>
              </TableCell>
              <TableCell>
                <TargetStatusBadge status={target.status} />
              </TableCell>
              <TableCell className="max-w-44">{target.owner ? <UserCell name={target.owner.fullName} /> : <span className="text-sm text-muted-foreground">No owner</span>}</TableCell>
              {anyEditable && (
                <TableCell>
                  {target.editable && (
                    <Button variant="ghost" size="icon" aria-label={`Edit ${target.type.name} target`} onClick={() => onEdit(target)}>
                      <Pencil aria-hidden />
                    </Button>
                  )}
                </TableCell>
              )}
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
