import { ArrowRight, Inbox, Pencil, Plus, Workflow } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { parseLocalDate } from '@/features/tasks/task-meta';
import { useIdParam } from '@/lib/use-id-param';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { cn } from '@/lib/utils';

import { APPROVAL_STATUSES, useApprovals, useApprovalTypes, type ApprovalStatus, type ApprovalType, type ApprovalView } from './api';
import { ApprovalStatusBadge } from './ApprovalBadges';
import { ApprovalDrawer } from './ApprovalDrawer';
import { APPROVAL_STATUS_LABELS, formatAmount, templateApprover } from './approval-meta';
import { NewApprovalDialog } from './NewApprovalDialog';
import { WorkflowDialog } from './WorkflowDialog';

type Tab = ApprovalView | 'WORKFLOWS';

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' });

/** Requests waiting for the viewer, their own requests, everything they can see, and (for configurers) workflows. */
export default function ApprovalsPage() {
  const { user } = useAuth();
  const canDecide = hasPermission(user, 'APPROVAL_DECIDE');
  const canConfigure = hasPermission(user, 'APPROVAL_CONFIGURE');
  const [tab, setTab] = useState<Tab>(canDecide ? 'TO_DECIDE' : 'MINE');
  const [approvalId, setApprovalId] = useIdParam('approval');
  const [creating, setCreating] = useState(false);
  const toDecide = useApprovals({ view: 'TO_DECIDE', size: 1 });

  const tabs: { id: Tab; label: string; count?: number }[] = [
    ...(canDecide ? [{ id: 'TO_DECIDE' as Tab, label: 'To decide', count: toDecide.data?.totalElements }] : []),
    { id: 'MINE', label: 'My requests' },
    { id: 'ALL', label: 'All I can see' },
    ...(canConfigure ? [{ id: 'WORKFLOWS' as Tab, label: 'Workflows' }] : []),
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Approvals"
        description="Access, purchases, expenses and other requests, step by step."
        actions={
          <Button onClick={() => setCreating(true)}>
            <Plus aria-hidden />
            New request
          </Button>
        }
      />
      <div className="flex flex-wrap gap-1 border-b" role="tablist" aria-label="Approval views">
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => setTab(t.id)}
            className={cn(
              '-mb-px flex items-center gap-2 border-b-2 px-3 py-2 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50',
              tab === t.id ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground',
            )}
          >
            {t.label}
            {t.count !== undefined && t.count > 0 && <Badge tone="warning">{t.count}</Badge>}
          </button>
        ))}
      </div>
      {tab === 'WORKFLOWS' ? <WorkflowsPanel /> : <RequestList key={tab} view={tab} onOpen={setApprovalId} />}
      <NewApprovalDialog open={creating} onOpenChange={setCreating} onCreated={(a) => setApprovalId(a.id)} />
      <ApprovalDrawer approvalId={approvalId} onClose={() => setApprovalId(null)} />
    </div>
  );
}

const EMPTY: Record<ApprovalView, { title: string; description: string }> = {
  TO_DECIDE: { title: 'Nothing waiting for you', description: 'Requests that need your decision will appear here.' },
  MINE: { title: "You haven't raised any requests", description: 'Use "New request" to ask for access, software, a purchase and more.' },
  ALL: { title: 'No requests to show', description: 'Requests from your team appear here.' },
};

function RequestList({ view, onOpen }: { view: ApprovalView; onOpen: (id: number) => void }) {
  const types = useApprovalTypes();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<ApprovalStatus | ''>('');
  const [typeId, setTypeId] = useState('');
  const [page, setPage] = useState(0);
  const debounced = useDebouncedValue(search.trim());
  const approvals = useApprovals({
    view,
    search: debounced,
    status: status ? [status] : undefined,
    typeId: typeId ? Number(typeId) : undefined,
    sort: view === 'TO_DECIDE' ? 'due,asc' : 'created,desc',
    page,
    size: 25,
  });
  const filtered = Boolean(search || status || typeId);

  return (
    <Card>
      <div className="grid gap-3 border-b p-4 sm:grid-cols-[1fr_repeat(2,minmax(0,12rem))]">
        <SearchInput placeholder="Search title or code" aria-label="Search requests" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
        {view !== 'TO_DECIDE' && (
          <Select aria-label="Status" value={status} onChange={(e) => { setStatus(e.target.value as ApprovalStatus | ''); setPage(0); }}>
            <option value="">Any status</option>
            {APPROVAL_STATUSES.map((s) => (
              <option key={s} value={s}>
                {APPROVAL_STATUS_LABELS[s]}
              </option>
            ))}
          </Select>
        )}
        <Select aria-label="Type" value={typeId} onChange={(e) => { setTypeId(e.target.value); setPage(0); }}>
          <option value="">All types</option>
          {types.data?.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </Select>
      </div>
      {approvals.isPending ? (
        <div className="space-y-3 p-4" role="status" aria-label="Loading requests">
          {Array.from({ length: 5 }, (_, i) => (
            <Skeleton key={i} className="h-12" />
          ))}
        </div>
      ) : approvals.isError ? (
        <ErrorState error={approvals.error} title="Couldn't load requests" onRetry={() => void approvals.refetch()} />
      ) : approvals.data.content.length === 0 ? (
        <EmptyState icon={Inbox} title={filtered ? 'No requests match these filters' : EMPTY[view].title} description={filtered ? undefined : EMPTY[view].description} />
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Request</TableHead>
                <TableHead>Requester</TableHead>
                <TableHead className="text-right">Amount</TableHead>
                <TableHead>Needed by</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody className={cn(approvals.isPlaceholderData && 'opacity-60')}>
              {approvals.data.content.map((a) => (
                <TableRow
                  key={a.id}
                  data-clickable="true"
                  tabIndex={0}
                  aria-label={`Open ${a.code} ${a.title}`}
                  onClick={() => onOpen(a.id)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') onOpen(a.id);
                  }}
                >
                  <TableCell className="max-w-md">
                    <p className="truncate font-medium">{a.title}</p>
                    <p className="text-xs text-muted-foreground">
                      <span className="font-mono">{a.code}</span> · {a.type.name}
                    </p>
                  </TableCell>
                  <TableCell className="text-sm">
                    {a.requester?.fullName ?? 'Former user'}
                    <span className="block text-xs text-muted-foreground">{a.department.name}</span>
                  </TableCell>
                  <TableCell className="text-right text-sm tabular-nums">{formatAmount(a.amount, a.currency)}</TableCell>
                  <TableCell className="text-sm whitespace-nowrap">{a.dueDate ? dayFormat.format(parseLocalDate(a.dueDate)) : <span className="text-muted-foreground">—</span>}</TableCell>
                  <TableCell>
                    <div className="flex flex-col items-start gap-1">
                      <ApprovalStatusBadge status={a.status} />
                      {a.status === 'PENDING' && (
                        <span className="text-xs text-muted-foreground">
                          {a.awaitingMe ? <span className="font-medium text-status-warning">Waiting for you</span> : `Waiting on ${a.waitingOn ?? '—'}`} · step {a.currentStep} of {a.stepCount}
                        </span>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Pagination {...approvals.data} onPageChange={setPage} />
        </>
      )}
    </Card>
  );
}

function WorkflowsPanel() {
  const types = useApprovalTypes();
  const [editing, setEditing] = useState<ApprovalType | null>(null);
  if (types.isPending) {
    return (
      <div className="space-y-3" role="status" aria-label="Loading workflows">
        {Array.from({ length: 4 }, (_, i) => (
          <Skeleton key={i} className="h-16 rounded-xl" />
        ))}
      </div>
    );
  }
  if (types.isError) return <ErrorState error={types.error} title="Couldn't load workflows" onRetry={() => void types.refetch()} />;
  return (
    <>
      <div className="grid gap-3 md:grid-cols-2">
        {types.data.map((type) => (
          <Card key={type.id} className="flex items-start gap-3 p-4">
            <Workflow className="mt-0.5 size-5 text-muted-foreground" aria-hidden />
            <div className="min-w-0 flex-1">
              <p className="font-medium">
                {type.name}
                {type.requiresAmount && <span className="ml-2 text-xs text-muted-foreground">needs an amount</span>}
              </p>
              <p className="mt-1 text-sm text-muted-foreground">
                {type.steps.map((step, i) => (
                  <span key={step.stepOrder}>
                    {i > 0 && <ArrowRight className="mx-1 inline size-3" aria-label="then" />}
                    {templateApprover(step)}
                  </span>
                ))}
              </p>
            </div>
            <Button variant="ghost" size="sm" onClick={() => setEditing(type)} aria-label={`Edit ${type.name} workflow`}>
              <Pencil aria-hidden />
              Edit
            </Button>
          </Card>
        ))}
      </div>
      <WorkflowDialog type={editing} onClose={() => setEditing(null)} />
    </>
  );
}
