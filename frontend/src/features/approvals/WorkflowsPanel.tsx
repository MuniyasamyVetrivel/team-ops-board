import { ArrowRight, CircleOff, Pencil, Plus, Settings2, Workflow } from 'lucide-react';
import { useState } from 'react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';

import { useApprovalTypes, type ApprovalType } from './api';
import { templateApprover } from './approval-meta';
import { ApprovalTypeDialog } from './ApprovalTypeDialog';
import { WorkflowDialog } from './WorkflowDialog';

/**
 * Approval types and their workflows, for APPROVAL_CONFIGURE holders: add a type, edit its details (name, amount
 * rule, active) and edit its approver steps. Shown on the Approvals page and in Settings.
 */
export function WorkflowsPanel() {
  const types = useApprovalTypes();
  const [editingSteps, setEditingSteps] = useState<ApprovalType | null>(null);
  const [editingType, setEditingType] = useState<ApprovalType | null>(null);
  const [creating, setCreating] = useState(false);

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
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">Each request type runs its own approval steps. Changes apply to new requests only.</p>
        <Button size="sm" onClick={() => setCreating(true)}>
          <Plus aria-hidden />
          New approval type
        </Button>
      </div>
      {types.data.length === 0 ? (
        <EmptyState icon={Workflow} title="No approval types yet" description="Add a type so people can raise requests." />
      ) : (
        <div className="grid gap-3 md:grid-cols-2">
          {types.data.map((type) => (
            <Card key={type.id} className="flex items-start gap-3 p-4" data-inactive={!type.active || undefined}>
              <Workflow className="mt-0.5 size-5 text-muted-foreground" aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="flex flex-wrap items-center gap-2 font-medium">
                  <span className={type.active ? undefined : 'text-muted-foreground'}>{type.name}</span>
                  {type.requiresAmount && <span className="text-xs font-normal text-muted-foreground">needs an amount</span>}
                  {!type.active && (
                    <Badge tone="neutral">
                      <CircleOff aria-hidden />
                      Inactive
                    </Badge>
                  )}
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
              <div className="flex shrink-0 gap-1">
                <Button variant="ghost" size="sm" onClick={() => setEditingType(type)} aria-label={`Edit ${type.name} details`}>
                  <Settings2 aria-hidden />
                  Details
                </Button>
                <Button variant="ghost" size="sm" onClick={() => setEditingSteps(type)} aria-label={`Edit ${type.name} workflow`}>
                  <Pencil aria-hidden />
                  Steps
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}
      <WorkflowDialog type={editingSteps} onClose={() => setEditingSteps(null)} />
      <ApprovalTypeDialog open={creating || editingType !== null} type={editingType ?? undefined} onClose={() => { setCreating(false); setEditingType(null); }} />
    </div>
  );
}
