import { Ban, CircleCheck, CircleX, LoaderCircle } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { toast } from 'sonner';

import { ErrorState } from '@/components/common/ErrorState';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
import { parseLocalDate } from '@/features/tasks/task-meta';
import { errorMessage } from '@/lib/api/errors';
import { formatDateTime } from '@/lib/format';
import { cn } from '@/lib/utils';

import { useApproval, useCancelApproval, useDecideApproval, type ApprovalDetail } from './api';
import { ApprovalStatusBadge, StepStatusBadge } from './ApprovalBadges';
import { formatAmount, stepApprover } from './approval-meta';

const dayFormat = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });

export function ApprovalDrawer({ approvalId, onClose }: { approvalId: number | null; onClose: () => void }) {
  const query = useApproval(approvalId);
  return (
    <Dialog open={approvalId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent aria-describedby={undefined} className="sm:max-w-2xl">
        {query.isPending ? (
          <div className="space-y-4 p-6" role="status" aria-label="Loading request">
            <DialogTitle className="sr-only">Loading request</DialogTitle>
            <Skeleton className="h-8 w-2/3" />
            <Skeleton className="h-60" />
          </div>
        ) : query.isError ? (
          <div className="p-6">
            <DialogTitle className="sr-only">Request unavailable</DialogTitle>
            <ErrorState error={query.error} title="Couldn't open this request" onRetry={() => void query.refetch()} />
          </div>
        ) : (
          <DrawerBody key={query.data.id} approval={query.data} />
        )}
      </SheetContent>
    </Dialog>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[7rem_1fr] gap-3 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <div className="min-w-0">{children}</div>
    </div>
  );
}

function DrawerBody({ approval }: { approval: ApprovalDetail }) {
  return (
    <>
      <div className="space-y-2 border-b px-6 py-5 pr-12">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="font-mono text-muted-foreground">{approval.code}</span>
          <ApprovalStatusBadge status={approval.status} />
          <span className="text-muted-foreground">{approval.type.name}</span>
        </div>
        <DialogTitle className="text-xl leading-snug">{approval.title}</DialogTitle>
        <DialogDescription className="sr-only">Approval request details</DialogDescription>
      </div>
      <div className="flex-1 space-y-6 overflow-y-auto px-6 py-5">
        <div className="space-y-2.5">
          <Row label="Requester">{approval.requester?.fullName ?? 'Former user'} · {approval.department.name}</Row>
          <Row label="Amount">{formatAmount(approval.amount, approval.currency)}</Row>
          <Row label="Needed by">{approval.dueDate ? dayFormat.format(parseLocalDate(approval.dueDate)) : '—'}</Row>
          <Row label="Raised">{formatDateTime(approval.createdAt)}</Row>
          {approval.decidedAt && <Row label="Finished">{formatDateTime(approval.decidedAt)}</Row>}
        </div>
        {approval.description && (
          <section>
            <h3 className="mb-1.5 text-sm font-semibold">Details</h3>
            <p className="text-sm whitespace-pre-wrap">{approval.description}</p>
          </section>
        )}
        <section>
          <h3 className="mb-3 text-sm font-semibold">Approval steps</h3>
          <ol className="space-y-3" aria-label="Approval steps">
            {approval.steps.map((step) => (
              <li key={step.stepOrder} className={cn('rounded-lg border p-3', step.status === 'PENDING' && 'border-status-warning/50 bg-status-warning/5')}>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm font-medium">
                    <span className="text-muted-foreground">Step {step.stepOrder} · </span>
                    {stepApprover(step)}
                  </p>
                  <StepStatusBadge status={step.status} />
                </div>
                {step.decidedAt && (
                  <p className="mt-1 text-xs text-muted-foreground">
                    {step.decidedBy?.fullName ?? 'Someone'} · {formatDateTime(step.decidedAt)}
                  </p>
                )}
                {step.comment && <p className="mt-1.5 text-sm whitespace-pre-wrap">“{step.comment}”</p>}
              </li>
            ))}
          </ol>
        </section>
        {approval.permissions.canDecide && <DecisionPanel approval={approval} />}
        {approval.permissions.canCancel && <CancelButton approval={approval} />}
      </div>
    </>
  );
}

/** Approve, or reject with a reason (the server requires one). */
function DecisionPanel({ approval }: { approval: ApprovalDetail }) {
  const decide = useDecideApproval(approval.id);
  const [comment, setComment] = useState('');
  const [missingReason, setMissingReason] = useState(false);

  async function send(decision: 'APPROVE' | 'REJECT') {
    if (decision === 'REJECT' && !comment.trim()) {
      setMissingReason(true);
      return;
    }
    try {
      await decide.mutateAsync({ decision, comment: comment.trim() || null });
      toast.success(decision === 'APPROVE' ? 'Approved' : 'Rejected');
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <section className="space-y-3 rounded-lg border p-4" aria-label="Your decision">
      <h3 className="text-sm font-semibold">Your decision</h3>
      <div className="space-y-1.5">
        <Label htmlFor="decision-comment">Comment</Label>
        <Textarea
          id="decision-comment"
          rows={3}
          value={comment}
          aria-invalid={missingReason ? true : undefined}
          aria-describedby="decision-comment-message"
          onChange={(e) => {
            setComment(e.target.value);
            setMissingReason(false);
          }}
          placeholder="Optional when approving, required when rejecting"
        />
        {missingReason && (
          <p id="decision-comment-message" className="text-sm text-destructive">
            Give a reason when rejecting a request
          </p>
        )}
      </div>
      <div className="flex gap-2">
        <Button onClick={() => void send('APPROVE')} disabled={decide.isPending}>
          {decide.isPending ? <LoaderCircle className="animate-spin" aria-hidden /> : <CircleCheck aria-hidden />}
          Approve
        </Button>
        <Button variant="outline" onClick={() => void send('REJECT')} disabled={decide.isPending}>
          <CircleX aria-hidden />
          Reject
        </Button>
      </div>
    </section>
  );
}

function CancelButton({ approval }: { approval: ApprovalDetail }) {
  const cancel = useCancelApproval(approval.id);
  return (
    <Button
      variant="outline"
      disabled={cancel.isPending}
      onClick={() => void cancel.mutateAsync(undefined).then(() => toast.success('Request cancelled'), (error: unknown) => toast.error(errorMessage(error)))}
    >
      <Ban aria-hidden />
      Cancel request
    </Button>
  );
}
