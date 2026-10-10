import { CirclePause, Clock, MessageSquareReply, OctagonAlert, Pencil, ShieldCheck, Timer, TriangleAlert } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { KpiCard } from '@/components/common/KpiCard';
import { PageHeader } from '@/components/common/PageHeader';
import { Panel } from '@/components/common/Panel';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { PriorityIndicator } from '@/features/tasks/TaskBadges';
import { TicketDrawer } from '@/features/tickets/TicketDrawer';
import { TicketTable } from '@/features/tickets/TicketTable';
import { formatMinutes } from '@/features/tickets/ticket-meta';
import { useTicketParam } from '@/features/tickets/use-ticket-param';
import { cn } from '@/lib/utils';

import { useSlaPolicies, useSlaSummary, type SlaPolicy } from './api';
import { SlaPolicyDialog } from './SlaPolicyDialog';

const WINDOWS = [
  { value: 7, label: 'Last 7 days' },
  { value: 30, label: 'Last 30 days' },
  { value: 90, label: 'Last 90 days' },
];

function percent(value: number | null): string {
  return value === null ? '—' : `${value}%`;
}

/**
 * SLA overview (brief section 12): compliance for tickets raised in the window, the current state of open tickets,
 * tickets at risk, and the policy targets. Every number comes from the server for the tickets the viewer can see.
 */
export default function SlaPage() {
  const { user } = useAuth();
  const [days, setDays] = useState(30);
  const summary = useSlaSummary(days);
  const policies = useSlaPolicies();
  const [ticketId, setTicketId] = useTicketParam();
  const [editing, setEditing] = useState<SlaPolicy | null>(null);
  const canManage = hasPermission(user, 'SLA_MANAGE');
  const data = summary.data;

  return (
    <div className="space-y-6">
      <PageHeader
        title="SLA"
        description="First response and resolution targets, compliance and tickets at risk."
        actions={
          <Select aria-label="Window" value={days} onChange={(event) => setDays(Number(event.target.value))} className="w-40">
            {WINDOWS.map((w) => (
              <option key={w.value} value={w.value}>
                {w.label}
              </option>
            ))}
          </Select>
        }
      />

      {summary.isError ? (
        <Card>
          <ErrorState error={summary.error} title="Couldn't load SLA figures" onRetry={() => void summary.refetch()} />
        </Card>
      ) : (
        <>
          <section aria-label="SLA figures" className={cn('grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6', summary.isPlaceholderData && 'opacity-60')}>
            <KpiCard label="First response met" value={data?.firstResponseCompliance} suffix="%" icon={MessageSquareReply} tone="text-primary" loading={!data} hint={data ? `${data.created} tickets raised` : undefined} />
            <KpiCard label="Resolution met" value={data?.resolutionCompliance} suffix="%" icon={ShieldCheck} tone="text-status-success" loading={!data} />
            <KpiCard label="Open tickets" value={data?.open} icon={Timer} loading={!data} to="/tickets" />
            <KpiCard label="On track" value={data?.onTrack} icon={Clock} tone="text-status-success" loading={!data} />
            <KpiCard label="At risk" value={data?.warning} icon={TriangleAlert} tone="text-status-warning" loading={!data} hint={data ? `${data.warningThresholdPct}%+ of target used` : undefined} />
            <KpiCard label="Breached" value={data?.breached} icon={OctagonAlert} tone="text-status-danger" alert={(data?.breached ?? 0) > 0} loading={!data} hint={data && data.paused > 0 ? `${data.paused} paused, waiting on requester` : undefined} />
          </section>

          <Panel title="Tickets at risk" icon={TriangleAlert} description="Open tickets that are close to or past a deadline, most urgent first">
            {!data ? (
              <div className="space-y-3 p-4" role="status" aria-label="Loading tickets at risk">
                {Array.from({ length: 3 }, (_, i) => (
                  <Skeleton key={i} className="h-12" />
                ))}
              </div>
            ) : data.atRisk.length === 0 ? (
              <EmptyState icon={ShieldCheck} title="Nothing at risk" description="Every open ticket is within its SLA." className="py-10" />
            ) : (
              <TicketTable tickets={data.atRisk} onOpen={(t) => setTicketId(t.id)} />
            )}
          </Panel>

          <Panel title="Compliance by priority" icon={ShieldCheck} description={`Tickets raised in the ${WINDOWS.find((w) => w.value === days)?.label.toLowerCase()}. "—" means nothing has been decided yet.`}>
            {!data || policies.isPending ? (
              <div className="space-y-3 p-4" role="status" aria-label="Loading policies">
                {Array.from({ length: 4 }, (_, i) => (
                  <Skeleton key={i} className="h-10" />
                ))}
              </div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Priority</TableHead>
                    <TableHead>First response target</TableHead>
                    <TableHead>Resolution target</TableHead>
                    <TableHead numeric>Raised</TableHead>
                    <TableHead numeric>First response met</TableHead>
                    <TableHead numeric>Resolution met</TableHead>
                    <TableHead numeric>Open breached</TableHead>
                    {canManage && <TableHead className="sr-only">Actions</TableHead>}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.priorities.map((row) => {
                    const policy = policies.data?.find((p) => p.priority === row.priority);
                    return (
                      <TableRow key={row.priority}>
                        <TableCell>
                          <PriorityIndicator priority={row.priority} />
                        </TableCell>
                        <TableCell className="tabular-nums">{formatMinutes(policy?.firstResponseMinutes ?? row.firstResponseMinutes)}</TableCell>
                        <TableCell className="tabular-nums">{formatMinutes(policy?.resolutionMinutes ?? row.resolutionMinutes)}</TableCell>
                        <TableCell numeric>{row.created}</TableCell>
                        <TableCell numeric>{percent(row.firstResponseCompliance)}</TableCell>
                        <TableCell numeric>{percent(row.resolutionCompliance)}</TableCell>
                        <TableCell numeric className={cn(row.openBreached > 0 && 'font-semibold text-status-danger')}>{row.openBreached}</TableCell>
                        {canManage && (
                          <TableCell numeric>
                            {policy && (
                              <Button variant="ghost" size="sm" onClick={() => setEditing(policy)} aria-label={`Edit ${policy.name} targets`}>
                                <Pencil aria-hidden />
                                Edit
                              </Button>
                            )}
                          </TableCell>
                        )}
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            )}
            <p className="flex items-center gap-2 border-t px-6 py-3 text-xs text-muted-foreground">
              <CirclePause className="size-3.5" aria-hidden />
              The clock pauses while a ticket waits for the requester. Due times are fixed when a ticket is raised.
            </p>
          </Panel>
        </>
      )}

      <SlaPolicyDialog policy={editing} onClose={() => setEditing(null)} />
      <TicketDrawer ticketId={ticketId} onClose={() => setTicketId(null)} />
    </div>
  );
}
