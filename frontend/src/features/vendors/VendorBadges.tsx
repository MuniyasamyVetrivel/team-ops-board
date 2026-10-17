import { EyeOff, Star, Timer } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { ToolErrorState } from '@/features/modules/ToolErrorState';
import { cn } from '@/lib/utils';

import type { Contract, ContractStatus, VendorStatus } from './api';
import { contractValue, CONTRACT_STATUS_META, renewalLabel, renewalTone, VENDOR_STATUS_META } from './vendor-meta';

export function VendorStatusBadge({ status }: { status: VendorStatus }) {
  const meta = VENDOR_STATUS_META[status];
  const Icon = meta.icon;
  return (
    <Badge tone={meta.tone}>
      <Icon aria-hidden />
      {meta.label}
    </Badge>
  );
}

export function ContractStatusBadge({ status }: { status: ContractStatus }) {
  const meta = CONTRACT_STATUS_META[status];
  const Icon = meta.icon;
  return (
    <Badge tone={meta.tone}>
      <Icon aria-hidden />
      {meta.label}
    </Badge>
  );
}

/** Days to the renewal (or the end), from the server; nothing when nothing is due. */
export function RenewalBadge({ contract }: { contract: Pick<Contract, 'daysUntilRenewal' | 'renewalDate' | 'autoRenew'> }) {
  if (contract.daysUntilRenewal === null) return null;
  return (
    <Badge tone={renewalTone(contract.daysUntilRenewal)}>
      <Timer aria-hidden />
      {renewalLabel(contract)}
    </Badge>
  );
}

/** The value, or "Hidden" with an icon for viewers without FINANCE_VIEW. */
export function ContractValue({ contract, className }: { contract: Pick<Contract, 'value' | 'valueHidden' | 'currency'>; className?: string }) {
  if (contract.valueHidden) {
    return (
      <span className={cn('inline-flex items-center gap-1 text-muted-foreground', className)} title="Only people who can see finance see contract values">
        <EyeOff className="size-3.5" aria-hidden />
        {contractValue(contract)}
      </span>
    );
  }
  return <span className={cn('tabular-nums', contract.value === null && 'text-muted-foreground', className)}>{contractValue(contract)}</span>;
}

/** Five stars with the number spelled out, so the rating never depends on colour or shape alone. */
export function Rating({ rating }: { rating: number | null }) {
  if (rating === null) return <span className="text-sm text-muted-foreground">Not rated</span>;
  return (
    <span className="inline-flex items-center gap-1.5" aria-label={`Rated ${rating} out of 5`}>
      <span className="flex" aria-hidden>
        {Array.from({ length: 5 }, (_, i) => (
          <Star key={i} className={cn('size-3.5', i < rating ? 'fill-highlight text-highlight' : 'text-muted-foreground/40')} />
        ))}
      </span>
      <span className="text-xs text-muted-foreground tabular-nums">{rating}/5</span>
    </span>
  );
}

export function VendorErrorState({ error, title, onRetry }: { error: unknown; title: string; onRetry?: () => void }) {
  return <ToolErrorState error={error} title={title} onRetry={onRetry} toolName="Vendors" permission="VENDOR_VIEW" />;
}
