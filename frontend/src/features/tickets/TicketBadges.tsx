import {
  CircleCheck,
  CircleDot,
  CirclePause,
  CircleX,
  Clock,
  Hourglass,
  Inbox,
  MessageCircleQuestion,
  OctagonAlert,
  TriangleAlert,
  type LucideIcon,
} from 'lucide-react';

import { Badge, type BadgeProps } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

import { SLA_STATE_LABELS, slaText, TICKET_STATUS_LABELS } from './ticket-meta';
import type { SlaState, SlaStatus, TicketStatus } from './types';

type Tone = BadgeProps['tone'];

const STATUS_STYLE: Record<TicketStatus, { tone: Tone; icon: LucideIcon }> = {
  NEW: { tone: 'primary', icon: Inbox },
  OPEN: { tone: 'neutral', icon: CircleDot },
  IN_PROGRESS: { tone: 'primary', icon: Hourglass },
  WAITING_FOR_REQUESTER: { tone: 'warning', icon: MessageCircleQuestion },
  RESOLVED: { tone: 'success', icon: CircleCheck },
  CLOSED: { tone: 'neutral', icon: CircleX },
};

/** Status with icon + label (never colour alone). */
export function TicketStatusBadge({ status }: { status: TicketStatus }) {
  const { tone, icon: Icon } = STATUS_STYLE[status];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {TICKET_STATUS_LABELS[status]}
    </Badge>
  );
}

const SLA_STYLE: Record<SlaState, { tone: Tone; icon: LucideIcon }> = {
  ON_TRACK: { tone: 'success', icon: Clock },
  WARNING: { tone: 'warning', icon: TriangleAlert },
  BREACHED: { tone: 'danger', icon: OctagonAlert },
};

export function SlaStateBadge({ state }: { state: SlaState }) {
  const { tone, icon: Icon } = SLA_STYLE[state];
  return (
    <Badge tone={tone}>
      <Icon aria-hidden />
      {SLA_STATE_LABELS[state]}
    </Badge>
  );
}

/**
 * Countdown for one SLA deadline: "3h 20m left", "Overdue by 45m", "Paused · 2h left", "Met". The state label is
 * part of the accessible name so it is never conveyed by colour alone.
 */
export function SlaCountdown({ status, className }: { status: SlaStatus; className?: string }) {
  const met = status.met === true;
  const paused = status.paused && status.met === null;
  const { tone, icon } = met ? { tone: 'success' as Tone, icon: CircleCheck } : paused ? { tone: 'neutral' as Tone, icon: CirclePause } : SLA_STYLE[status.state];
  const Icon = icon;
  const label = met ? 'Met' : paused ? 'Paused' : SLA_STATE_LABELS[status.state];
  return (
    <Badge tone={tone} className={cn('tabular-nums', className)} title={`${label}: ${slaText(status)}`}>
      <Icon aria-hidden />
      <span className="sr-only">{label}:</span>{' '}
      {slaText(status)}
    </Badge>
  );
}
