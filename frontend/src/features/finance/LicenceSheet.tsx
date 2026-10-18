import { CreditCard, Pencil, Users } from 'lucide-react';
import { useState } from 'react';

import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { Detail, Muted } from '@/features/celebrations/Details';
import { DetailsWithHistory } from '@/features/history/DetailsWithHistory';
import { useTool } from '@/features/modules/tool-context';
import { formatLocalDate } from '@/lib/format';

import { useLicence, type Licence } from './api';
import { FinanceErrorState, LicenceRenewalBadge, LicenceStatusBadge, SeatUsage } from './FinanceBadges';
import { BILLING_LABELS, costLabel, formatAud, formatMoney, formatRate, paymentLabel } from './finance-meta';
import { LicenceFormDialog } from './LicenceFormDialog';

/** One tool or licence: seats and who has them, cost (with its AUD at today's rate), renewal; with its History. */
export function LicenceSheet({ licenceId, onClose }: { licenceId: number | null; onClose: () => void }) {
  return (
    <Dialog open={licenceId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="sm:max-w-xl">{licenceId !== null && <LicenceBody licenceId={licenceId} onClose={onClose} />}</SheetContent>
    </Dialog>
  );
}

function LicenceBody({ licenceId, onClose }: { licenceId: number; onClose: () => void }) {
  const { canEdit } = useTool();
  const licence = useLicence(licenceId);
  const [editing, setEditing] = useState(false);

  if (licence.isPending) {
    return (
      <div className="space-y-4 p-6" role="status" aria-label="Loading the licence">
        <Skeleton className="h-12 w-2/3" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (licence.isError) return <FinanceErrorState error={licence.error} title="Couldn't load the licence" onRetry={() => void licence.refetch()} />;

  const l = licence.data;
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <DialogTitle>{l.name}</DialogTitle>
        <DialogDescription>{l.vendor?.name ?? (l.vendorId !== null ? 'Vendor in the recycle bin' : 'No vendor')}</DialogDescription>
        <div className="flex flex-wrap items-center gap-2">
          <LicenceStatusBadge status={l.status} />
          <LicenceRenewalBadge licence={l} />
          {canEdit && (
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit licence
            </Button>
          )}
        </div>
      </DialogHeader>
      <DetailsWithHistory type="FINANCE_LICENCE" id={l.id}>
        <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 pb-6">
          <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Detail label="Cost">{costLabel(l)}</Detail>
            <Detail label="Billing">{BILLING_LABELS[l.billingCycle]}</Detail>
            <Detail label="Annual cost">
              <AnnualCost licence={l} />
            </Detail>
            <Detail label="Department">{l.department?.name ?? <Muted>None</Muted>}</Detail>
            <Detail label="Start date">{l.startDate ? formatLocalDate(l.startDate) : <Muted>Not recorded</Muted>}</Detail>
            <Detail label={l.status === 'TRIAL' ? 'Trial ends' : 'Renewal date'}>{l.renewalDate ? formatLocalDate(l.renewalDate) : <Muted>None</Muted>}</Detail>
            <Detail label="Owner">{l.owner ? <UserCell name={l.owner.fullName} detail={l.owner.jobTitle} user={l.owner} /> : <Muted>No owner</Muted>}</Detail>
            <Detail label="Payment method">
              {paymentLabel(l) ? (
                <span className="inline-flex items-center gap-1.5">
                  <CreditCard className="size-3.5 text-muted-foreground" aria-hidden />
                  {paymentLabel(l)}
                </span>
              ) : (
                <Muted>Not recorded</Muted>
              )}
            </Detail>
          </dl>
          {l.notes && <Detail label="Notes">{<span className="whitespace-pre-wrap">{l.notes}</span>}</Detail>}
          <section aria-label="Seats" className="space-y-3">
            <div className="flex items-center justify-between gap-3">
              <h3 className="text-sm font-medium">Seats</h3>
              {l.seatsFree !== null && <span className="text-xs text-muted-foreground">{l.seatsFree === 1 ? '1 seat free' : `${Math.max(0, l.seatsFree)} seats free`}</span>}
            </div>
            <SeatUsage licence={l} className="max-w-sm" />
            {l.assignedUsers.length === 0 ? (
              <p className="flex items-center gap-2 rounded-lg border border-dashed px-4 py-3 text-sm text-muted-foreground">
                <Users className="size-4" aria-hidden />
                Nobody is assigned yet{canEdit ? '. Edit the licence to assign the people who use it.' : '.'}
              </p>
            ) : (
              <ul className="divide-y rounded-lg border" aria-label="Assigned users">
                {l.assignedUsers.map((u) => (
                  <li key={u.id} className="px-4 py-2.5">
                    <UserCell name={u.fullName} detail={u.status === 'DISABLED' ? 'Disabled account' : u.jobTitle} user={u} />
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </DetailsWithHistory>
      <LicenceFormDialog open={editing} onOpenChange={setEditing} licence={l} onDeleted={onClose} />
    </div>
  );
}

/** The cost per year in its currency and in AUD at today's rate; one-off costs have none. */
function AnnualCost({ licence }: { licence: Licence }) {
  if (licence.billingCycle === 'ONE_OFF') return <Muted>None (one-off)</Muted>;
  if (licence.annualCost === null) return <Muted>—</Muted>;
  return (
    <span>
      {formatMoney(licence.annualCost, licence.currency)}
      {licence.currency !== 'AUD' && (
        <span className="block text-xs text-muted-foreground">
          {licence.annualCostAud !== null && licence.audRate
            ? `${formatAud(licence.annualCostAud)} at ${formatRate(licence.audRate.rate)}${licence.audRate.effectiveFrom ? ` (from ${formatLocalDate(licence.audRate.effectiveFrom)})` : ''}`
            : `No ${licence.currency} exchange rate yet, so it is left out of the annual total`}
        </span>
      )}
    </span>
  );
}
