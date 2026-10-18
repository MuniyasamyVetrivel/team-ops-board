import { TrendingDown, Timer, Users } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { ToolErrorState } from '@/features/modules/ToolErrorState';
import { cn } from '@/lib/utils';

import type { Licence, LicenceStatus } from './api';
import { formatPercent, LICENCE_STATUS_META, licenceRenewalLabel, renewalTone, seatsLabel } from './finance-meta';

export function LicenceStatusBadge({ status }: { status: LicenceStatus }) {
  const meta = LICENCE_STATUS_META[status];
  const Icon = meta.icon;
  return (
    <Badge tone={meta.tone}>
      <Icon aria-hidden />
      {meta.label}
    </Badge>
  );
}

/** Days to the renewal (or the end of a trial), from the server; nothing when nothing is due. */
export function LicenceRenewalBadge({ licence }: { licence: Pick<Licence, 'daysUntilRenewal' | 'status'> }) {
  const label = licenceRenewalLabel(licence);
  if (!label) return null;
  return (
    <Badge tone={renewalTone(licence.daysUntilRenewal)}>
      <Timer aria-hidden />
      {label}
    </Badge>
  );
}

/** Seats used of paid as text, a bar and an "Under-used" or "Over-assigned" tag: the server's figures, never colour alone. */
export function SeatUsage({ licence, className }: { licence: Pick<Licence, 'seatsUsed' | 'seatsPaid' | 'utilisationPercent' | 'underUsed' | 'overAssigned'>; className?: string }) {
  const percent = licence.utilisationPercent;
  return (
    <div className={cn('min-w-32 space-y-1', className)}>
      <div className="flex items-center justify-between gap-2 text-sm">
        <span className="inline-flex items-center gap-1 tabular-nums">
          <Users className="size-3.5 text-muted-foreground" aria-hidden />
          {seatsLabel(licence)}
        </span>
        {percent !== null && <span className="text-xs text-muted-foreground tabular-nums">{formatPercent(percent)}</span>}
      </div>
      {percent !== null && (
        <div
          className="h-1.5 overflow-hidden rounded-full bg-muted"
          role="meter"
          aria-label="Seats used"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.min(100, Math.round(percent))}
        >
          <div
            className={cn('h-full rounded-full', licence.overAssigned ? 'bg-status-danger' : licence.underUsed ? 'bg-status-warning' : 'bg-primary')}
            style={{ width: `${Math.min(100, percent)}%` }}
          />
        </div>
      )}
      {(licence.underUsed || licence.overAssigned) && (
        <Badge tone={licence.overAssigned ? 'danger' : 'warning'}>
          <TrendingDown aria-hidden />
          {licence.overAssigned ? 'More users than seats' : 'Under-used'}
        </Badge>
      )}
    </div>
  );
}

export function FinanceErrorState({ error, title, onRetry }: { error: unknown; title: string; onRetry?: () => void }) {
  return <ToolErrorState error={error} title={title} onRetry={onRetry} toolName="Finance Tracker" permission="FINANCE_VIEW" />;
}
