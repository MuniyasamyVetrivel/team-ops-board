import { Building, CalendarDays, Lock, Mail, Pencil, Phone } from 'lucide-react';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { toast } from 'sonner';

import { ErrorState } from '@/components/common/ErrorState';
import { UserCell } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { parseLocalDate } from '@/features/tasks/task-meta';
import { errorMessage } from '@/lib/api/errors';

import { LEAD_SOURCE_LABELS } from '../targets/target-meta';
import { LEAD_STATUSES, useChangeLeadStatus, useLead, type Lead, type LeadStatus } from './api';
import { LeadFormDialog } from './LeadFormDialog';
import { LeadStatusBadge } from './LeadBadges';
import { LEAD_ORIGIN_LABELS, LEAD_STATUS_LABELS, LINK_KIND_LABELS } from './lead-meta';

const dayFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
const timeFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' });

interface LeadSheetProps {
  leadId: number | null;
  onOpenChange: (open: boolean) => void;
  canEdit: boolean;
}

/** A lead: contact details, attribution, and its status (changed on its own, audited). */
export function LeadSheet({ leadId, onOpenChange, canEdit }: LeadSheetProps) {
  return (
    <Dialog open={leadId !== null} onOpenChange={onOpenChange}>
      <SheetContent className="sm:max-w-xl">{leadId !== null && <LeadBody leadId={leadId} canEdit={canEdit} onClose={() => onOpenChange(false)} />}</SheetContent>
    </Dialog>
  );
}

function LeadBody({ leadId, canEdit, onClose }: { leadId: number; canEdit: boolean; onClose: () => void }) {
  const lead = useLead(leadId);
  const [editing, setEditing] = useState(false);

  if (lead.isPending) {
    return (
      <div className="space-y-4 p-6" role="status" aria-label="Loading the lead">
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-10" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (lead.isError) {
    return <ErrorState error={lead.error} title="Couldn't load the lead" onRetry={() => void lead.refetch()} />;
  }

  const l = lead.data;
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <div className="flex flex-wrap items-center gap-2">
          <DialogTitle>{l.name}</DialogTitle>
          <LeadStatusBadge status={l.status} />
        </div>
        <DialogDescription className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="font-mono text-xs">{l.code}</span>
          {l.company && (
            <>
              <span aria-hidden>·</span>
              <span>{l.company}</span>
            </>
          )}
          <span aria-hidden>·</span>
          <span>{LEAD_SOURCE_LABELS[l.source]}</span>
        </DialogDescription>
        {canEdit && (
          <div>
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit lead
            </Button>
          </div>
        )}
      </DialogHeader>
      <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 pb-6">
        {canEdit && <StatusPicker lead={l} />}

        <section aria-labelledby="lead-contact" className="space-y-2">
          <h3 id="lead-contact" className="text-sm font-semibold">
            Contact
          </h3>
          <ul className="space-y-1.5 text-sm">
            <ContactLine icon={Mail} label="Email">
              {l.email ? (
                <a className="hover:underline" href={`mailto:${l.email}`}>
                  {l.email}
                </a>
              ) : null}
            </ContactLine>
            <ContactLine icon={Phone} label="Phone">
              {l.phone}
            </ContactLine>
            <ContactLine icon={Building} label="Company">
              {l.company}
            </ContactLine>
          </ul>
        </section>

        <section aria-labelledby="lead-details" className="space-y-2">
          <h3 id="lead-details" className="text-sm font-semibold">
            Details
          </h3>
          <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Detail label="Lead date">
              <span className="inline-flex items-center gap-1">
                <CalendarDays className="size-3.5 text-muted-foreground" aria-hidden />
                {dayFormat.format(parseLocalDate(l.leadDate))}
                {l.countLocked && (
                  <span className="inline-flex items-center gap-1 text-xs text-muted-foreground" title="The month is closed: date and source stay as counted">
                    <Lock className="size-3.5" aria-hidden />
                    Month closed
                  </span>
                )}
              </span>
            </Detail>
            <Detail label="Source">{LEAD_SOURCE_LABELS[l.source]}</Detail>
            <Detail label="Campaign or content">
              {l.link ? (
                <>
                  {l.link.name}
                  <span className="block text-xs text-muted-foreground">
                    {LINK_KIND_LABELS[l.link.kind]}
                    {l.link.date && ` · ${dayFormat.format(parseLocalDate(l.link.date))}`}
                  </span>
                </>
              ) : (
                <span className="text-muted-foreground">None</span>
              )}
            </Detail>
            <Detail label="Owner">{l.owner ? <UserCell name={l.owner.fullName} /> : <span className="text-muted-foreground">No owner</span>}</Detail>
            <Detail label="Department">{l.department?.name ?? <span className="text-muted-foreground">None</span>}</Detail>
            <Detail label="Recorded from">
              {LEAD_ORIGIN_LABELS[l.provider]}
              {l.externalId && <span className="block font-mono text-xs text-muted-foreground">{l.externalId}</span>}
            </Detail>
          </dl>
        </section>

        {l.notes && (
          <section aria-labelledby="lead-notes-heading" className="space-y-2">
            <h3 id="lead-notes-heading" className="text-sm font-semibold">
              Notes
            </h3>
            <p className="rounded-lg border bg-muted/30 px-3 py-2 text-sm whitespace-pre-line">{l.notes}</p>
          </section>
        )}

        <p className="text-xs text-muted-foreground">
          Added by {l.createdBy?.fullName ?? 'Unknown'} · {timeFormat.format(new Date(l.createdAt))}
          {l.updatedAt !== l.createdAt && ` · updated ${timeFormat.format(new Date(l.updatedAt))}`}
        </p>
      </div>
      <LeadFormDialog open={editing} onOpenChange={setEditing} lead={l} onDeleted={onClose} />
    </div>
  );
}

/** One click per status; the change is saved (with the lead's version) and audited on its own. */
function StatusPicker({ lead }: { lead: Lead }) {
  const change = useChangeLeadStatus(lead.id);

  async function onPick(status: LeadStatus) {
    try {
      await change.mutateAsync({ version: lead.version, status });
      toast.success(`${lead.code} is now ${LEAD_STATUS_LABELS[status].toLowerCase()}`);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <div role="group" aria-label="Change status" className="flex flex-wrap gap-1.5">
      {LEAD_STATUSES.map((s) => (
        <Button key={s} type="button" size="sm" variant={s === lead.status ? 'default' : 'outline'} aria-pressed={s === lead.status} disabled={change.isPending || s === lead.status} onClick={() => void onPick(s)}>
          {LEAD_STATUS_LABELS[s]}
        </Button>
      ))}
    </div>
  );
}

function ContactLine({ icon: Icon, label, children }: { icon: typeof Mail; label: string; children: ReactNode }) {
  return (
    <li className="flex items-center gap-2">
      <Icon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
      <span className="sr-only">{label}:</span>
      {children ?? <span className="text-muted-foreground">No {label.toLowerCase()}</span>}
    </li>
  );
}

function Detail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-0.5">{children}</dd>
    </div>
  );
}
