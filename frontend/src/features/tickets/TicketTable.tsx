import { UserAvatar } from '@/components/common/UserAvatar';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { PriorityIndicator } from '@/features/tasks/TaskBadges';
import { formatRelative } from '@/lib/format';
import { cn } from '@/lib/utils';

import { SlaCountdown, TicketStatusBadge } from './TicketBadges';
import type { TicketListItem } from './types';

interface TicketTableProps {
  tickets: TicketListItem[];
  onOpen: (ticket: TicketListItem) => void;
  /** Hide the requester column (e.g. "requested by me"). */
  hideRequester?: boolean;
  dimmed?: boolean;
}

/**
 * Dense ticket list: code + subject, status, priority, people and the SLA that matters now (first response until
 * it is met, then resolution).
 */
export function TicketTable({ tickets, onOpen, hideRequester = false, dimmed = false }: TicketTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Ticket</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Priority</TableHead>
          {!hideRequester && <TableHead>Requester</TableHead>}
          <TableHead>Assignee</TableHead>
          <TableHead>SLA</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody className={cn(dimmed && 'opacity-60')}>
        {tickets.map((ticket) => {
          const firstPending = ticket.sla.firstResponse.met === null;
          const sla = firstPending ? ticket.sla.firstResponse : ticket.sla.resolution;
          return (
            <TableRow
              key={ticket.id}
              data-clickable="true"
              tabIndex={0}
              aria-label={`Open ${ticket.code} ${ticket.subject}`}
              onClick={() => onOpen(ticket)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  onOpen(ticket);
                }
              }}
            >
              <TableCell className="max-w-md">
                <p className="truncate font-medium">{ticket.subject}</p>
                <p className="flex items-center gap-2 text-xs text-muted-foreground">
                  <span className="font-mono">{ticket.code}</span>
                  <span aria-hidden>·</span>
                  <span className="truncate">{ticket.category?.name ?? ticket.department.name}</span>
                  <span aria-hidden>·</span>
                  <span className="whitespace-nowrap">{formatRelative(ticket.createdAt)}</span>
                </p>
              </TableCell>
              <TableCell>
                <TicketStatusBadge status={ticket.status} />
              </TableCell>
              <TableCell>
                <PriorityIndicator priority={ticket.priority} />
              </TableCell>
              {!hideRequester && (
                <TableCell className="max-w-40">
                  <span className="block truncate text-sm">{ticket.requester?.fullName ?? 'Former user'}</span>
                </TableCell>
              )}
              <TableCell>
                {ticket.assignee ? (
                  <span className="flex items-center gap-2">
                    <UserAvatar name={ticket.assignee.fullName} size="sm" />
                    <span className="truncate text-sm">{ticket.assignee.fullName}</span>
                  </span>
                ) : (
                  <span className="text-sm text-muted-foreground">Unassigned</span>
                )}
              </TableCell>
              <TableCell>
                <div className="flex flex-col items-start gap-0.5">
                  <SlaCountdown status={sla} />
                  <span className="text-[11px] text-muted-foreground">{firstPending ? 'First response' : 'Resolution'}</span>
                </div>
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
